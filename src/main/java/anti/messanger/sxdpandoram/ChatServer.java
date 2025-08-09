package anti.messanger.sxdpandoram;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {
    // --- Локальные порты, которые слушает ваш компьютер ---
    private static final int LOCAL_CHAT_PORT = 12345;
    private static final int LOCAL_HTTP_PORT = 8000;

    // --- ПУБЛИЧНЫЕ ДАННЫЕ ИЗ PLAYIT.GG ДЛЯ ГЕНЕРАЦИИ ССЫЛОК ---
    public static final String SERVER_PUBLIC_DOWNLOAD_IP = "chinese-medium.gl.at.ply.gg";
    // !!! ДОБАВЛЕНА ЭТА СТРОКА С ВАШИМ ПУБЛИЧНЫМ ПОРТОМ !!!
    public static final int SERVER_PUBLIC_DOWNLOAD_PORT = 24613;

    private static final Map<String, FileDownloadHandler.FileInfo> downloadableFiles = new ConcurrentHashMap<>();
    private static final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private static DatabaseManager databaseManager;
    private static FileTransferManager fileTransferManager;

    public static void main(String[] args) {
        databaseManager = new DatabaseManager();
        fileTransferManager = new FileTransferManager(downloadableFiles);

        try {
            // Запускаем HTTP сервер на ЛОКАЛЬНОМ порту 8000
            HttpServer httpServer = HttpServer.create(new InetSocketAddress(LOCAL_HTTP_PORT), 0);
            httpServer.createContext("/download", new FileDownloadHandler(downloadableFiles));
            httpServer.setExecutor(null);
            httpServer.start();
            System.out.println("✅ HTTP сервер для скачивания файлов запущен на локальном порту: " + LOCAL_HTTP_PORT);

            // Запускаем ЧАТ-СЕРВЕР на ЛОКАЛЬНОМ порту 12345
            ServerSocket serverSocket = new ServerSocket(LOCAL_CHAT_PORT);
            System.out.println("✅ Чат-сервер запущен на локальном порту: " + LOCAL_CHAT_PORT);
            System.out.println("⏳ Ожидание подключения клиентов через туннели...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("🔌 Новое подключение! IP: " + clientSocket.getInetAddress().getHostAddress());
                ClientHandler clientThread = new ClientHandler(clientSocket, clients, databaseManager, fileTransferManager);
                new Thread(clientThread).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}