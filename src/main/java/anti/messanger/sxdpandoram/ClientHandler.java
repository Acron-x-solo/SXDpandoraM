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

    // ... поля класса остаются без изменений ...
    private final Socket clientSocket;
    private final List<ClientHandler> clients;
    private final DatabaseManager databaseManager;
    private PrintWriter out;
    private BufferedReader in;
    private String clientName;
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    public ClientHandler(Socket socket, List<ClientHandler> clients, DatabaseManager dbManager) {
        this.clientSocket = socket;
        this.clients = clients;
        this.databaseManager = dbManager;
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            // ЭТАП 1: АУТЕНТИФИКАЦИЯ (без изменений)
            while (true) {
                String line = in.readLine();
                if (line == null) return;
                String[] parts = line.split(" ", 3);
                String command = parts[0];
                if ("LOGIN".equals(command) && parts.length == 3) {
                    if (databaseManager.verifyUser(parts[1], parts[2])) {
                        this.clientName = parts[1];
                        out.println("LOGIN_SUCCESS");
                        break;
                    } else { out.println("LOGIN_FAILED"); }
                } else if ("REGISTER".equals(command) && parts.length == 3) {
                    if (databaseManager.registerUser(parts[1], parts[2])) {
                        out.println("REGISTER_SUCCESS");
                    } else { out.println("REGISTER_FAILED_USER_EXISTS"); }
                }
            }

            // ЭТАП 2: ЧАТ
            System.out.println("🗣️ " + clientName + " вошел в чат.");
            synchronized (clients) {
                clients.add(this);
                String joinMsg = String.format("SYS_MSG§§%s§§%s присоединился к чату", getTimestamp(), clientName);
                broadcastMessage(joinMsg);
                // --- ИСПРАВЛЕНИЕ 1: ОТПРАВЛЯЕМ ОБНОВЛЕННЫЙ СПИСОК ВСЕМ ПОСЛЕ ВХОДА ---
                sendUsersListToAll();
            }

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                // ... обработка MSG, PM, LIST_USERS остается без изменений ...
                String[] parts = inputLine.split(" ", 2);
                String command = parts[0];
                switch (command) {
                    case "MSG":
                        if (parts.length > 1) {
                            String msg = String.format("PUB_MSG§§%s§§%s§§%s", getTimestamp(), this.clientName, parts[1]);
                            broadcastMessage(msg);
                        }
                        break;
                    case "PM":
                        if (parts.length > 1) {
                            String[] pmParts = parts[1].split(" ", 2);
                            if (pmParts.length > 1) sendPrivateMessage(pmParts[0], pmParts[1]);
                        }
                        break;
                    case "LIST_USERS":
                        sendUsersListToOne(); // Старый метод теперь только для одного клиента
                        break;
                }
            }
        } catch (SocketException e) {
            System.out.println("🔌 Соединение с " + (clientName != null ? clientName : "клиентом") + " было сброшено.");
        } catch (IOException e) {
            System.err.println("❌ Ошибка в обработчике клиента: " + e.getMessage());
        } finally {
            if (clientName != null) {
                synchronized (clients) {
                    clients.remove(this);
                }
                System.out.println("👋 " + clientName + " покинул чат.");
                String leaveMsg = String.format("SYS_MSG§§%s§§%s покинул чат", getTimestamp(), clientName);
                broadcastMessage(leaveMsg);
                // --- ИСПРАВЛЕНИЕ 2: ОТПРАВЛЯЕМ ОБНОВЛЕННЫЙ СПИСОК ВСЕМ ПОСЛЕ ВЫХОДА ---
                sendUsersListToAll();
            }
            try { if (out != null) out.close(); if (in != null) in.close(); clientSocket.close(); } catch (IOException e) { e.printStackTrace(); }
        }
    }

    // --- ИСПРАВЛЕНИЕ 3: НОВЫЙ МЕТОД ДЛЯ РАССЫЛКИ СПИСКА ВСЕМ ---
    private void sendUsersListToAll() {
        StringBuilder usersList = new StringBuilder("USERS_LIST§§");
        synchronized (clients) {
            for (ClientHandler client : clients) {
                usersList.append(client.clientName).append(",");
            }
        }
        if (usersList.length() > "USERS_LIST§§".length()) {
            usersList.setLength(usersList.length() - 1);
        }
        // Рассылаем готовый список всем
        broadcastMessage(usersList.toString());
    }

    // --- Переименовываем старый метод для ясности ---
    private void sendUsersListToOne() {
        StringBuilder usersList = new StringBuilder("USERS_LIST§§");
        synchronized (clients) {
            for (ClientHandler client : clients) {
                usersList.append(client.clientName).append(",");
            }
        }
        if (usersList.length() > "USERS_LIST§§".length()) {
            usersList.setLength(usersList.length() - 1);
        }
        this.out.println(usersList.toString());
    }

    private void broadcastMessage(String message) {
        synchronized (clients) { for (ClientHandler client : clients) { client.out.println(message); } }
    }

    private void sendPrivateMessage(String recipientName, String message) {
        ClientHandler recipientHandler = null;
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client.clientName.equals(recipientName)) {
                    recipientHandler = client;
                    break;
                }
            }
        }

        if (recipientHandler != null) {
            // НОВЫЙ ФОРМАТ: PRIV_MSG§§время§§отправитель§§получатель§§текст
            String formattedMsg = String.format("PRIV_MSG§§%s§§%s§§%s§§%s", getTimestamp(), this.clientName, recipientName, message);
            // Отправляем одно и то же сообщение и получателю, и отправителю
            recipientHandler.out.println(formattedMsg);
            this.out.println(formattedMsg);
        } else {
            this.out.println(String.format("SYS_MSG§§%s§§Пользователь '%s' не найден.", getTimestamp(), recipientName));
        }
    }

    private String getTimestamp() {
        return LocalDateTime.now().format(dtf);
    }
}