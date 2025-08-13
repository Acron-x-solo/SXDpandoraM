package anti.messanger.sxdpandoram;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Управляет звонком: выбор устройств, захват и воспроизведение аудио, сигналинг.
 * Передача аудио кадров идёт через основной TCP-канал как строки VOICE_FRAME с Base64.
 */
public class VoiceCallManager {

    private final PrintWriter serverOut;

    private final List<Mixer.Info> inputMixers = new ArrayList<>();
    private final List<Mixer.Info> outputMixers = new ArrayList<>();

    private int selectedInputIndex = -1;
    private int selectedOutputIndex = -1;

    private TargetDataLine microphoneLine;
    private SourceDataLine speakerLine;

    private Thread captureThread;
    private final AtomicBoolean capturing = new AtomicBoolean(false);

    private volatile String activePeer = null;

    // 16 kHz, mono, 16-bit PCM, little-endian
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            16000.0f,
            16,
            1,
            2,
            16000.0f,
            false
    );

    public VoiceCallManager(PrintWriter serverOut) {
        this.serverOut = serverOut;
        discoverMixers();
    }

    private void discoverMixers() {
        inputMixers.clear();
        outputMixers.clear();
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(info);
            boolean supportsInput = false;
            boolean supportsOutput = false;
            for (Line.Info li : mixer.getTargetLineInfo()) {
                if (li instanceof DataLine.Info && ((DataLine.Info) li).getFormats().length >= 0) {
                    supportsInput = true;
                    break;
                }
            }
            for (Line.Info li : mixer.getSourceLineInfo()) {
                if (li instanceof DataLine.Info && ((DataLine.Info) li).getFormats().length >= 0) {
                    supportsOutput = true;
                    break;
                }
            }
            if (supportsInput) inputMixers.add(info);
            if (supportsOutput) outputMixers.add(info);
        }
        if (!inputMixers.isEmpty() && selectedInputIndex < 0) selectedInputIndex = 0;
        if (!outputMixers.isEmpty() && selectedOutputIndex < 0) selectedOutputIndex = 0;
    }

    public List<String> getInputDeviceNames() {
        List<String> names = new ArrayList<>();
        for (Mixer.Info i : inputMixers) names.add(i.getName());
        return names;
    }

    public List<String> getOutputDeviceNames() {
        List<String> names = new ArrayList<>();
        for (Mixer.Info i : outputMixers) names.add(i.getName());
        return names;
    }

    public void setSelectedDevices(int inputIndex, int outputIndex) {
        if (inputIndex >= 0 && inputIndex < inputMixers.size()) selectedInputIndex = inputIndex;
        if (outputIndex >= 0 && outputIndex < outputMixers.size()) selectedOutputIndex = outputIndex;
    }

    public boolean isInCall() { return activePeer != null; }

    public String getActivePeer() { return activePeer; }
    
    private boolean isMuted = false;
    private boolean isSpeakerOn = true;
    
    public void setMuted(boolean muted) {
        this.isMuted = muted;
        if (microphoneLine != null) {
            if (muted) {
                microphoneLine.stop();
            } else {
                microphoneLine.start();
            }
        }
    }
    
    public void setSpeakerOn(boolean speakerOn) {
        this.isSpeakerOn = speakerOn;
        if (speakerLine != null) {
            if (speakerOn) {
                speakerLine.start();
            } else {
                speakerLine.stop();
            }
        }
    }
    
    public boolean isMuted() {
        return isMuted;
    }
    
    public boolean isSpeakerOn() {
        return isSpeakerOn;
    }

    public void invite(String callee) {
        if (serverOut != null) serverOut.println("CALL_INVITE§§" + callee);
    }

    public void accept(String caller) {
        if (serverOut != null) serverOut.println("CALL_ACCEPT§§" + caller);
    }

    public void decline(String caller) {
        if (serverOut != null) serverOut.println("CALL_DECLINE§§" + caller);
    }

    public void endCall() {
        String peer = this.activePeer;
        stopStreaming();
        if (peer != null && serverOut != null) {
            serverOut.println("CALL_END§§" + peer);
        }
    }

    public void startStreamingWithPeer(String peerName) {
        if (isInCall()) return;
        this.activePeer = peerName;
        openLines();
        startCaptureLoop();
    }

    public void stopStreaming() {
        capturing.set(false);
        activePeer = null;
        if (captureThread != null) {
            try { captureThread.join(250); } catch (InterruptedException ignored) { }
            captureThread = null;
        }
        closeLines();
    }

    public void handleIncomingVoiceFrame(String sender, String base64Data) {
        if (!isInCall() || !sender.equals(activePeer)) return;
        try {
            // Проверяем, не отключены ли динамики
            if (!isSpeakerOn) return;
            
            byte[] data = Base64.getDecoder().decode(base64Data);
            if (speakerLine != null) {
                speakerLine.write(data, 0, data.length);
            }
        } catch (IllegalArgumentException ignored) { }
    }
    
    // Алиас для handleIncomingVoiceFrame для совместимости
    public void playAudioChunk(String base64Data) {
        if (activePeer != null) {
            handleIncomingVoiceFrame(activePeer, base64Data);
        }
    }

    private void openLines() {
        try {
            // Open microphone
            Mixer.Info inInfo = (selectedInputIndex >= 0 && selectedInputIndex < inputMixers.size()) ? inputMixers.get(selectedInputIndex) : null;
            Mixer inMixer = (inInfo != null) ? AudioSystem.getMixer(inInfo) : null;
            DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
            if (inMixer != null && inMixer.isLineSupported(micInfo)) {
                microphoneLine = (TargetDataLine) inMixer.getLine(micInfo);
            } else {
                microphoneLine = (TargetDataLine) AudioSystem.getLine(micInfo);
            }
            microphoneLine.open(AUDIO_FORMAT);
            microphoneLine.start();

            // Open speakers
            Mixer.Info outInfo = (selectedOutputIndex >= 0 && selectedOutputIndex < outputMixers.size()) ? outputMixers.get(selectedOutputIndex) : null;
            Mixer outMixer = (outInfo != null) ? AudioSystem.getMixer(outInfo) : null;
            DataLine.Info spkInfo = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT);
            if (outMixer != null && outMixer.isLineSupported(spkInfo)) {
                speakerLine = (SourceDataLine) outMixer.getLine(spkInfo);
            } else {
                speakerLine = (SourceDataLine) AudioSystem.getLine(spkInfo);
            }
            speakerLine.open(AUDIO_FORMAT);
            speakerLine.start();
        } catch (Exception e) {
            closeLines();
        }
    }

    private void closeLines() {
        if (microphoneLine != null) {
            try { microphoneLine.stop(); } catch (Exception ignored) {}
            try { microphoneLine.close(); } catch (Exception ignored) {}
            microphoneLine = null;
        }
        if (speakerLine != null) {
            try { speakerLine.drain(); } catch (Exception ignored) {}
            try { speakerLine.stop(); } catch (Exception ignored) {}
            try { speakerLine.close(); } catch (Exception ignored) {}
            speakerLine = null;
        }
    }

    private void startCaptureLoop() {
        if (microphoneLine == null) return;
        capturing.set(true);
        captureThread = new Thread(() -> {
            // 20 мс пакеты: 16000 Hz * 2 байта * 0.02 = 640 байт
            int frameBytes = 640;
            byte[] buffer = new byte[frameBytes];
            while (capturing.get()) {
                try {
                    // Проверяем, не отключен ли микрофон
                    if (isMuted) {
                        Thread.sleep(20); // Ждем 20мс
                        continue;
                    }
                    
                    int read = microphoneLine.read(buffer, 0, buffer.length);
                    if (read > 0 && serverOut != null && activePeer != null) {
                        // Если было прочитано меньше, подрежем массив, чтобы не тащить мусор
                        byte[] payload;
                        if (read == buffer.length) {
                            payload = buffer;
                        } else {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream(read);
                            baos.write(buffer, 0, read);
                            payload = baos.toByteArray();
                        }
                        String b64 = Base64.getEncoder().encodeToString(payload);
                        serverOut.println("VOICE_FRAME§§" + activePeer + "§§" + b64);
                    }
                } catch (Exception e) {
                    // Прерываем цикл при ошибке
                    break;
                }
            }
        }, "VoiceCaptureThread");
        captureThread.setDaemon(true);
        captureThread.start();
    }
}


