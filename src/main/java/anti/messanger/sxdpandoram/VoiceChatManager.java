package anti.messanger.sxdpandoram;

import javax.sound.sampled.*;
import java.io.PrintWriter;
import java.util.Base64;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays; // <-- Добавьте этот импорт

public class VoiceChatManager {

    private AudioFormat activeFormat;
    private TargetDataLine microphone;
    private SourceDataLine speaker;
    private volatile boolean isRunning = false;

    public List<Mixer.Info> listMicrophones() {
        List<Mixer.Info> mics = new ArrayList<>();
        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            if (mixer.getTargetLineInfo().length > 0) {
                mics.add(mixerInfo);
            }
        }
        return mics;
    }

    private AudioFormat findSupportedFormat(Mixer mixer) {
        AudioFormat[] preferredFormats = new AudioFormat[]{
                new AudioFormat(44100.0f, 16, 1, true, false),
                new AudioFormat(16000.0f, 16, 1, true, false),
                new AudioFormat(8000.0f, 16, 1, true, false),
                new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, -1, 16, 1, -1, -1, false),
        };

        for (AudioFormat format : preferredFormats) {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (mixer.isLineSupported(info)) {
                System.out.println("Найден поддерживаемый формат: " + format);
                return format;
            }
        }
        System.err.println("Не найдено поддерживаемых форматов для микшера: " + mixer.getMixerInfo().getName());
        return null;
    }


    public void startCapture(Mixer.Info selectedMic, String partnerName, PrintWriter out) throws LineUnavailableException {
        if (selectedMic == null) {
            throw new LineUnavailableException("Микрофон не выбран.");
        }

        Mixer mixer = AudioSystem.getMixer(selectedMic);
        this.activeFormat = findSupportedFormat(mixer);

        if (this.activeFormat == null) {
            throw new LineUnavailableException("Не удалось найти поддерживаемый аудиоформат для выбранного микрофона.");
        }

        DataLine.Info info = new DataLine.Info(TargetDataLine.class, activeFormat);
        microphone = (TargetDataLine) mixer.getLine(info);

        microphone.open(activeFormat);
        microphone.start();

        isRunning = true;
        Thread captureThread = new Thread(() -> {
            byte[] buffer = new byte[2048];
            while (isRunning) {
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    // ======================= ИСПРАВЛЕНИЕ ЗДЕСЬ =======================
                    // 1. Создаем новый массив только с прочитанными данными.
                    byte[] chunkToSend = Arrays.copyOf(buffer, bytesRead);
                    // 2. Кодируем новый, правильно обрезанный массив.
                    String encodedChunk = Base64.getEncoder().encodeToString(chunkToSend);
                    // ===============================================================

                    out.println(String.format("AUDIO_CHUNK§§%s§§%s", partnerName, encodedChunk));
                }
            }
        });
        captureThread.setDaemon(true);
        captureThread.start();
        System.out.println("Захват звука начат с форматом: " + activeFormat);
    }

    public void stopCapture() {
        isRunning = false;
        if (microphone != null) {
            // Добавляем проверку, чтобы избежать NullPointerException, если поток завершится раньше
            if (microphone.isOpen()) {
                microphone.stop();
                microphone.close();
            }
            microphone = null;
        }
        System.out.println("Захват звука остановлен.");
    }

    public void startPlayback() throws LineUnavailableException {
        // Если мы принимающий клиент, у нас еще нет формата. Установим его по умолчанию.
        // Сервер гарантирует, что оба клиента будут использовать один и тот же формат,
        // но нам нужно знать его для инициализации линии воспроизведения.
        // Для надежности, мы будем передавать формат от инициатора звонка.
        // Пока оставим так, но в будущем это можно улучшить.
        if (this.activeFormat == null) {
            // Попробуем найти поддерживаемый формат для динамиков
            AudioFormat[] preferredFormats = new AudioFormat[]{
                    new AudioFormat(44100.0f, 16, 1, true, false),
                    new AudioFormat(16000.0f, 16, 1, true, false),
                    new AudioFormat(8000.0f, 16, 1, true, false),
            };
            for (AudioFormat format : preferredFormats) {
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                if (AudioSystem.isLineSupported(info)) {
                    this.activeFormat = format;
                    break;
                }
            }
            if (this.activeFormat == null) {
                throw new LineUnavailableException("Не найдено подходящих форматов для воспроизведения звука.");
            }
        }

        DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, activeFormat);
        if (!AudioSystem.isLineSupported(dataLineInfo)) {
            throw new LineUnavailableException("Формат воспроизведения " + activeFormat + " не поддерживается динамиками.");
        }

        speaker = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
        speaker.open(activeFormat);
        speaker.start();
        System.out.println("Воспроизведение звука начато с форматом: " + activeFormat);
    }

    public void stopPlayback() {
        if (speaker != null) {
            speaker.drain();
            speaker.close();
            speaker = null;
        }
        System.out.println("Воспроизведение звука остановлено.");
    }

    public void playAudioChunk(byte[] audioChunk) {
        if (speaker != null && speaker.isOpen()) {
            speaker.write(audioChunk, 0, audioChunk.length);
        }
    }
}