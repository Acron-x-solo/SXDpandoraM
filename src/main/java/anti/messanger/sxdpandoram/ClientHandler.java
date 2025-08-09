package anti.messanger.sxdpandoram;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final List<ClientHandler> clients; // Ссылка на общий список
    private PrintWriter out;
    private BufferedReader in;
    private String clientName;

    // Форматтер для времени
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    public ClientHandler(Socket socket, List<ClientHandler> clients) {
        this.clientSocket = socket;
        this.clients = clients;
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            // Первой строкой клиент присылает свое имя
            clientName = in.readLine();
            if (clientName == null || clientName.isEmpty()) {
                clientName = "Аноним";
            }
            System.out.println("🗣️ " + clientName + " присоединился к чату.");
            broadcastMessage(String.format("🎭 - 😉, [%s]\n%s присоединился к чату", getTimestamp(), clientName));

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                String formattedMessage = String.format("🎭 - 😉, [%s]\n%s написал: %s", getTimestamp(), clientName, inputLine);
                System.out.println("💬 Получено от " + clientName + ": " + inputLine);
                broadcastMessage(formattedMessage);
            }
        } catch (SocketException e) {
            System.out.println("🔌 Соединение с " + clientName + " было сброшено.");
        } catch (IOException e) {
            System.err.println("❌ Ошибка в обработчике клиента: " + e.getMessage());
        } finally {
            // Блок finally гарантирует, что ресурсы будут освобождены в любом случае
            if (clientName != null) {
                System.out.println("👋 " + clientName + " покинул чат.");
                clients.remove(this);
                broadcastMessage(String.format("🎭 - 😉, [%s]\n%s покинул чат", getTimestamp(), clientName));
            }
            try {
                if (out != null) out.close();
                if (in != null) in.close();
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Метод для рассылки сообщения всем клиентам
    private void broadcastMessage(String message) {
        // Синхронизируемся по списку, чтобы избежать проблем при одновременном доступе
        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.out.println(message);
            }
        }
    }

    private String getTimestamp() {
        return LocalDateTime.now().format(dtf);
    }
}