package anti.messanger.sxdpandoram;


import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatServer {
    // Порт, который будет "слушать" сервер. Его мы и будем пробрасывать.
    private static final int PORT = 12345;

    // Потокобезопасный список для хранения обработчиков клиентов.
    private static final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("✅ Сервер запущен на порту: " + PORT);
            System.out.println("⏳ Ожидание подключения клиентов...");

            while (true) { // Бесконечный цикл для приема новых клиентов
                try {
                    Socket clientSocket = serverSocket.accept(); // Блокирующий вызов, ждет клиента
                    System.out.println("🔌 Новое подключение! IP: " + clientSocket.getInetAddress().getHostAddress());

                    // Создаем для каждого клиента свой обработчик
                    ClientHandler clientThread = new ClientHandler(clientSocket, clients);
                    // Добавляем обработчик в общий список
                    clients.add(clientThread);
                    // Запускаем обработчик в отдельном потоке
                    new Thread(clientThread).start();

                } catch (IOException e) {
                    System.err.println("❌ Ошибка при подключении клиента: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Не удалось запустить сервер на порту " + PORT + ". Возможно, порт уже занят.");
            e.printStackTrace();
        }
    }
}