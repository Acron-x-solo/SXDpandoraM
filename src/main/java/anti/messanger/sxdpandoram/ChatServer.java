package anti.messanger.sxdpandoram;

import anti.messanger.sxdpandoram.ClientHandler;
import anti.messanger.sxdpandoram.DatabaseManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatServer {
    private static final int PORT = 12345;
    private static final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());

    // --- ЗАМЕНЯЕМ MAP НА DATABASEMANAGER ---
    private static DatabaseManager databaseManager;

    public static void main(String[] args) {
        // Инициализируем менеджер БД при старте сервера
        databaseManager = new DatabaseManager();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("✅ Сервер запущен на порту: " + PORT);
            System.out.println("⏳ Ожидание подключения клиентов...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("🔌 Новое подключение! IP: " + clientSocket.getInetAddress().getHostAddress());

                // Передаем в обработчик ссылку на менеджер БД
                ClientHandler clientThread = new ClientHandler(clientSocket, clients, databaseManager);
                new Thread(clientThread).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}