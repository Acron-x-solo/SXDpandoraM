package anti.messanger.sxdpandoram;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Map;

public class FileDownloadHandler implements HttpHandler {

    private final Map<String, FileInfo> downloadableFiles;

    public static class FileInfo {
        public final String filePath;
        public final String originalFileName;

        public FileInfo(String filePath, String originalFileName) {
            this.filePath = filePath;
            this.originalFileName = originalFileName;
        }
    }

    public FileDownloadHandler(Map<String, FileInfo> downloadableFiles) {
        this.downloadableFiles = downloadableFiles;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String requestPath = exchange.getRequestURI().getPath();
        String downloadId = requestPath.substring(requestPath.lastIndexOf('/') + 1);

        FileInfo fileInfo = downloadableFiles.get(downloadId);

        if (fileInfo == null) {
            sendErrorResponse(exchange, 404, "File not found or link expired.");
            return;
        }

        File file = new File(fileInfo.filePath);
        if (!file.exists()) {
            sendErrorResponse(exchange, 404, "File not found on server.");
            downloadableFiles.remove(downloadId); // Очистка
            return;
        }

        try {
            // Устанавливаем заголовки, чтобы браузер предложил скачать файл
            exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"" + fileInfo.originalFileName + "\"");
            exchange.sendResponseHeaders(200, file.length());

            try (OutputStream os = exchange.getResponseBody()) {
                Files.copy(file.toPath(), os);
            }
        } catch (IOException e) {
            System.err.println("Ошибка при отправке файла: " + e.getMessage());
        } finally {
            // Делаем ссылку одноразовой и удаляем временный файл
            downloadableFiles.remove(downloadId);
            if (file.delete()) {
                System.out.println("Временный файл удален: " + file.getName());
            }
        }
    }

    private void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        exchange.sendResponseHeaders(statusCode, message.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(message.getBytes());
        }
    }
}