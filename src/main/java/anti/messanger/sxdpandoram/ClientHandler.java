package anti.messanger.sxdpandoram;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final List<ClientHandler> clients;
    private final DatabaseManager databaseManager;
    private final FileTransferManager fileTransferManager;
    private PrintWriter out;
    private BufferedReader in;
    private String clientName;
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    // Карта для хранения потоков записи в файлы, которые загружаются на сервер
    // Ключ: "sender->recipient::filename", Значение: Поток для записи
    private static final Map<String, FileOutputStream> activeFileUploads = new ConcurrentHashMap<>();
    // Карта для хранения временных файлов, чтобы потом их передать в FileTransferManager
    private static final Map<String, File> tempFiles = new ConcurrentHashMap<>();


    public ClientHandler(Socket socket, List<ClientHandler> clients, DatabaseManager dbManager, FileTransferManager ftManager) {
        this.clientSocket = socket;
        this.clients = clients;
        this.databaseManager = dbManager;
        this.fileTransferManager = ftManager;
    }

    public String getClientName() { return clientName; }

    @Override
    public void run() {
        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            // ЭТАП 1: АУТЕНТИФИКАЦИЯ (использует старый протокол с пробелами, т.к. происходит до основного цикла)
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

            // ЭТАП 2: Вход в чат и основной цикл
            System.out.println("🗣️ " + clientName + " вошел в чат.");
            synchronized (clients) {
                clients.add(this);
                broadcastMessage(String.format("SYS_MSG§§%s§§%s присоединился к чату", getTimestamp(), clientName));
                sendUsersListToAll();
            }

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                // --- ЕДИНЫЙ НАДЕЖНЫЙ ПАРСЕР ДЛЯ ВСЕХ КОМАНД В ЧАТЕ ---
                String[] parts = inputLine.split("§§");
                String command = parts[0];

                switch (command) {
                    case "MSG": // FORMAT: MSG§§text
                        if (parts.length >= 2) {
                            broadcastMessage(String.format("PUB_MSG§§%s§§%s§§%s", getTimestamp(), this.clientName, parts[1]));
                        }
                        break;
                    case "PM": // FORMAT: PM§§recipient§§text
                        if (parts.length >= 3) {
                            sendPrivateMessage(parts[1], parts[2]);
                        }
                        break;
                    case "FILE_OFFER": // FORMAT: FILE_OFFER§§recipient§§filename§§filesize§§previewdata
                        if (parts.length >= 5) {
                            handleFileOffer(parts[1], parts[2], Long.parseLong(parts[3]), parts[4]);
                        }
                        break;
                    case "FILE_ACCEPT": // FORMAT: FILE_ACCEPT§§sender§§filename
                        if (parts.length >= 3) {
                            handleFileAccept(parts[1], parts[2]);
                        }
                        break;
                    case "FILE_DECLINE": // FORMAT: FILE_DECLINE§§sender§§filename
                        if (parts.length >= 3) {
                            handleFileDecline(parts[1], parts[2]);
                        }
                        break;
                    case "FILE_CHUNK": // FORMAT: FILE_CHUNK§§recipient§§filename§§data
                        if (parts.length >= 4) {
                            handleFileChunk(parts[1], parts[2], parts[3]);
                        }
                        break;
                    case "FILE_END": // FORMAT: FILE_END§§recipient§§filename
                        if (parts.length >= 3) {
                            handleFileEnd(parts[1], parts[2]);
                        }
                        break;
                }
            }
        } catch (SocketException e) {
            System.out.println("🔌 Соединение с клиентом " + (clientName != null ? clientName : "") + " разорвано.");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (clientName != null) {
                synchronized (clients) { clients.remove(this); }
                System.out.println("👋 " + clientName + " покинул чат.");
                broadcastMessage(String.format("SYS_MSG§§%s§§%s покинул чат", getTimestamp(), clientName));
                sendUsersListToAll();
                // Очистка незавершенных загрузок при выходе
                activeFileUploads.forEach((key, stream) -> {
                    if (key.startsWith(clientName + "->") || key.contains("->" + clientName + "::")) {
                        try { stream.close(); } catch (IOException e) { e.printStackTrace(); }
                        File file = tempFiles.remove(key);
                        if (file != null) file.delete();
                    }
                });
            }
        }
    }

    // Пересылаем предложение файла получателю
    private void handleFileOffer(String recipientName, String filename, long filesize, String previewData) {
        ClientHandler recipient = findClientByName(recipientName);
        if (recipient != null) {
            recipient.sendMessage(String.format("FILE_INCOMING§§%s§§%s§§%d§§%s", this.clientName, filename, filesize, previewData));
        }
    }

    // Получатель принял файл. Говорим отправителю начать загрузку.
    private void handleFileAccept(String originalSenderName, String filename) {
        ClientHandler sender = findClientByName(originalSenderName);
        if (sender != null) {
            System.out.println(clientName + " принял файл '" + filename + "' от " + originalSenderName + ". Запрашиваю у отправителя загрузку.");
            sender.sendMessage(String.format("UPLOAD_START§§%s§§%s", this.clientName, filename));
        }
    }

    private void handleFileDecline(String originalSenderName, String filename) {
        ClientHandler sender = findClientByName(originalSenderName);
        if (sender != null) {
            sender.sendMessage(String.format("SYS_MSG§§%s§§%s отклонил ваш файл '%s'", getTimestamp(), this.clientName, filename));
        }
    }

    // Обрабатываем пришедший кусочек файла от отправителя
    private void handleFileChunk(String recipientName, String fileName, String base64ChunkData) {
        String fileKey = this.clientName + "->" + recipientName + "::" + fileName;
        try {
            FileOutputStream fos = activeFileUploads.computeIfAbsent(fileKey, key -> {
                try {
                    File tempFile = File.createTempFile("chat_upload_", "_" + fileName);
                    tempFiles.put(key, tempFile);
                    System.out.println("Создан временный файл для загрузки: " + tempFile.getAbsolutePath());
                    return new FileOutputStream(tempFile);
                } catch (IOException e) { throw new UncheckedIOException(e); }
            });
            byte[] decodedChunk = Base64.getDecoder().decode(base64ChunkData);
            fos.write(decodedChunk);
        } catch (Exception e) {
            System.err.println("Ошибка при обработке FILE_CHUNK для ключа " + fileKey + ": " + e.getMessage());
        }
    }

    // Отправитель закончил слать кусочки
    private void handleFileEnd(String recipientName, String fileName) {
        String fileKey = this.clientName + "->" + recipientName + "::" + fileName;
        FileOutputStream fos = activeFileUploads.remove(fileKey);
        File tempFile = tempFiles.remove(fileKey);

        if (fos != null && tempFile != null) {
            try {
                fos.close();
                System.out.println("Загрузка файла '" + fileName + "' завершена. Размер: " + tempFile.length() + " байт.");
                ClientHandler recipient = findClientByName(recipientName);
                if (recipient != null) {
                    fileTransferManager.prepareDownloadLink(tempFile, fileName, recipient);
                } else {
                    tempFile.delete(); // Если получатель вышел, удаляем файл
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private ClientHandler findClientByName(String name) {
        synchronized(clients) {
            for (ClientHandler client : clients) {
                if (client.clientName.equals(name)) return client;
            }
        }
        return null;
    }

    private void sendPrivateMessage(String recipientName, String message) {
        ClientHandler recipientHandler = findClientByName(recipientName);
        String formattedMsg = String.format("PRIV_MSG§§%s§§%s§§%s§§%s", getTimestamp(), this.clientName, recipientName, message);
        if (recipientHandler != null) {
            recipientHandler.sendMessage(formattedMsg);
        }
        this.sendMessage(formattedMsg);
    }

    private void sendUsersListToAll() {
        StringBuilder usersList = new StringBuilder("USERS_LIST§§");
        synchronized (clients) {
            for (ClientHandler client : clients) usersList.append(client.clientName).append(",");
        }
        if (usersList.length() > "USERS_LIST§§".length() && usersList.charAt(usersList.length() - 1) == ',') {
            usersList.setLength(usersList.length() - 1);
        }
        broadcastMessage(usersList.toString());
    }

    private void broadcastMessage(String message) {
        synchronized (clients) { for (ClientHandler client : clients) client.sendMessage(message); }
    }

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    private String getTimestamp() {
        return LocalDateTime.now().format(dtf);
    }
}