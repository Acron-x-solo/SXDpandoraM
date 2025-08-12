package anti.messanger.sxdpandoram;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Демонстрация экрана с настройками FPS и качества
 */
public class ScreenShareManager {

    private final PrintWriter serverOut;
    private final AtomicBoolean sharing = new AtomicBoolean(false);
    private Thread captureThread;
    private volatile String peer;

    // Настройки по умолчанию
    private int fps = 5;
    private float quality = 0.7f;
    private int maxWidth = 1280;
    private int maxHeight = 720;

    public ScreenShareManager(PrintWriter out) {
        this.serverOut = out;
    }

    public boolean isSharing() { return sharing.get(); }

    public String getPeer() { return peer; }

    // Геттеры для настроек
    public int getFps() { return fps; }
    public float getQuality() { return quality; }
    public int getMaxWidth() { return maxWidth; }
    public int getMaxHeight() { return maxHeight; }

    // Сеттеры для настроек
    public void setFps(int fps) { 
        this.fps = Math.max(1, Math.min(30, fps)); // Ограничиваем от 1 до 30 FPS
    }
    
    public void setQuality(float quality) { 
        this.quality = Math.max(0.1f, Math.min(1.0f, quality)); // Ограничиваем от 0.1 до 1.0
    }
    
    public void setMaxDimensions(int width, int height) {
        this.maxWidth = Math.max(320, Math.min(1920, width)); // Ограничиваем от 320 до 1920
        this.maxHeight = Math.max(240, Math.min(1080, height)); // Ограничиваем от 240 до 1080
    }

    public void startSharing(String peerName) {
        if (sharing.get()) return;
        this.peer = peerName;
        if (serverOut != null) serverOut.println("SCREEN_START§§" + peerName);
        sharing.set(true);
        captureThread = new Thread(this::captureLoop, "ScreenShareCapture");
        captureThread.setDaemon(true);
        captureThread.start();
    }

    public void stopSharing() {
        sharing.set(false);
        String p = this.peer;
        this.peer = null;
        if (serverOut != null && p != null) serverOut.println("SCREEN_STOP§§" + p);
        if (captureThread != null) {
            try { 
                captureThread.join(500); // Увеличиваем время ожидания
            } catch (InterruptedException ignored) {}
            captureThread = null;
        }
    }

    private void captureLoop() {
        try {
            Robot robot = new Robot();
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            long frameIntervalMs = 1000L / fps;
            
            while (sharing.get() && !Thread.currentThread().isInterrupted()) {
                long started = System.currentTimeMillis();
                
                try {
                    BufferedImage screenshot = robot.createScreenCapture(screenRect);
                    BufferedImage scaled = scaleToMax(screenshot, maxWidth, maxHeight);
                    String b64 = toBase64Jpeg(scaled, quality);
                    
                    if (serverOut != null && peer != null && !b64.isEmpty()) {
                        serverOut.println("SCREEN_FRAME§§" + peer + "§§" + b64);
                    }
                } catch (Exception e) {
                    System.err.println("Ошибка захвата экрана: " + e.getMessage());
                }
                
                // Точное время сна для стабильного FPS
                long elapsed = System.currentTimeMillis() - started;
                long sleep = frameIntervalMs - elapsed;
                if (sleep > 0) {
                    Thread.sleep(sleep);
                }
            }
        } catch (Exception e) {
            System.err.println("Критическая ошибка в потоке захвата: " + e.getMessage());
        }
    }

    private static BufferedImage scaleToMax(BufferedImage src, int maxW, int maxH) {
        int w = src.getWidth();
        int h = src.getHeight();
        double scale = Math.min(maxW / (double) w, maxH / (double) h);
        if (scale >= 1.0) return src;
        
        int nw = (int) Math.round(w * scale);
        int nh = (int) Math.round(h * scale);
        
        Image tmp = src.getScaledInstance(nw, nh, Image.SCALE_SMOOTH);
        BufferedImage scaled = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = scaled.createGraphics();
        g2.drawImage(tmp, 0, 0, null);
        g2.dispose();
        return scaled;
    }

    private static String toBase64Jpeg(BufferedImage img, float quality) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // Используем стандартный JPEG с базовым качеством
            ImageIO.write(img, "jpg", baos);
            byte[] bytes = baos.toByteArray();
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            System.err.println("Ошибка кодирования JPEG: " + e.getMessage());
            return "";
        }
    }
}


