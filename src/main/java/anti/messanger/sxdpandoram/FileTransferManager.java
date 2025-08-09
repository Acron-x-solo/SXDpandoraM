package anti.messanger.sxdpandoram;

import java.io.File;
import java.util.Map;
import java.util.UUID;

public class FileTransferManager {

    private final Map<String, FileDownloadHandler.FileInfo> downloadableFiles;

    public FileTransferManager(Map<String, FileDownloadHandler.FileInfo> downloadableFiles) {
        this.downloadableFiles = downloadableFiles;
    }

    public void prepareDownloadLink(File savedFile, String originalFileName, ClientHandler recipient) {
        String downloadId = UUID.randomUUID().toString();

        downloadableFiles.put(downloadId, new FileDownloadHandler.FileInfo(savedFile.getAbsolutePath(), originalFileName));

        // --- ИЗМЕНЕНА ЭТА СТРОКА: ДОБАВЛЕН ПОРТ В ССЫЛКУ ---
        // Формат теперь: http://[адрес]:[порт]/download/[id]
        String downloadUrl = String.format("http://%s:%d/download/%s",
                ChatServer.SERVER_PUBLIC_DOWNLOAD_IP,
                ChatServer.SERVER_PUBLIC_DOWNLOAD_PORT,
                downloadId);

        recipient.sendMessage(String.format("FILE_LINK§§%s§§%s", originalFileName, downloadUrl));

        System.out.println("Сгенерирована и отправлена ссылка для " + recipient.getClientName() + ": " + downloadUrl);
    }
}