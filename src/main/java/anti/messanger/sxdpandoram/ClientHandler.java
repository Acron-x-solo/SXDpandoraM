package anti.messanger.sxdpandoram;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final List<ClientHandler> clients;
    private final DatabaseManager databaseManager;
    private final FileTransferManager fileTransferManager;
    private PrintWriter out;
    private BufferedReader in;
    private String clientName;

    private String currentCallPartner;
    private String currentVoiceChatPartner;

    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final Map<String, FileOutputStream> activeFileUploads = new ConcurrentHashMap<>();
    private static final Map<String, File> tempFiles = new ConcurrentHashMap<>();

    public ClientHandler(Socket socket, List<ClientHandler> clients, DatabaseManager dbManager, FileTransferManager ftManager) {
        this.clientSocket = socket;
        this.clients = clients;
        this.databaseManager = dbManager;
        this.fileTransferManager = ftManager;
    }

    public String getClientName() { return clientName; }
    public boolean isInCall() { return currentCallPartner != null || currentVoiceChatPartner != null; }
    public void setInCallWith(String partnerName) { this.currentCallPartner = partnerName; }
    public void endCall() { this.currentCallPartner = null; }
    public void setInVoiceChatWith(String partnerName) { this.currentVoiceChatPartner = partnerName; }
    public void endVoiceChat() { this.currentVoiceChatPartner = null; }

    @Override
    public void run() {
        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

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

            System.out.println("🗣️ " + clientName + " вошел в чат.");
            synchronized (clients) {
                clients.add(this);
                broadcastMessage(String.format("SYS_MSG§§%s присоединился к чату", clientName));
                sendUsersListToAll();
            }

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                String[] parts = inputLine.split("§§");
                String command = parts[0];
                switch (command) {
                    case "MSG":
                        if (parts.length >= 2) broadcastMessage(String.format("PUB_MSG§§%s§§%s§§%s", getTimestamp(), this.clientName, parts[1]));
                        break;
                    case "PM":
                        if (parts.length >= 3) sendPrivateMessage(parts[1], parts[2]);
                        break;
                    case "LIST_USERS":
                        sendUsersListToAll();
                        break;
                    case "FILE_OFFER":
                        if (parts.length >= 5) handleFileOffer(parts[1], parts[2], Long.parseLong(parts[3]), parts[4]);
                        break;
                    case "FILE_ACCEPT":
                        if (parts.length >= 3) handleFileAccept(parts[1], parts[2]);
                        break;
                    case "FILE_DECLINE":
                        if (parts.length >= 3) handleFileDecline(parts[1], parts[2]);
                        break;
                    case "FILE_CHUNK":
                        if (parts.length >= 4) handleFileChunk(parts[1], parts[2], parts[3]);
                        break;
                    case "FILE_END":
                        if (parts.length >= 3) handleFileEnd(parts[1], parts[2]);
                        break;
                    case "CALL_INITIATE":
                        if (parts.length == 2) handleCallInitiate(parts[1]);
                        break;
                    case "CALL_ACCEPT":
                        if (parts.length == 3) handleCallAccept(parts[1], parts[2]);
                        break;
                    case "CALL_DECLINE":
                        if (parts.length == 2) handleCallDecline(parts[1]);
                        break;
                    case "CALL_END":
                        if (parts.length == 2) handleCallEnd(parts[1]);
                        break;
                    case "VOICE_INVITE":
                        if (parts.length == 2) handleVoiceInvite(parts[1]);
                        break;
                    case "VOICE_ACCEPT":
                        if (parts.length == 2) handleVoiceAccept(parts[1]);
                        break;
                    case "VOICE_DECLINE":
                        if (parts.length == 2) handleVoiceDecline(parts[1]);
                        break;
                    case "VOICE_END":
                        if (parts.length == 2) handleVoiceEnd(parts[1]);
                        break;
                    case "AUDIO_CHUNK":
                        if (parts.length == 3) handleAudioChunk(parts[1], parts[2]);
                        break;
                }
            }
        } catch (SocketException e) {
            System.out.println("🔌 Соединение с клиентом " + (clientName != null ? clientName : "") + " разорвано.");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (clientName != null) {
                if (currentCallPartner != null) handleCallEnd(currentCallPartner);
                if (currentVoiceChatPartner != null) handleVoiceEnd(currentVoiceChatPartner);

                synchronized (clients) { clients.remove(this); }
                System.out.println("👋 " + clientName + " покинул чат.");
                broadcastMessage(String.format("SYS_MSG§§%s покинул чат", clientName));
                sendUsersListToAll();

                activeFileUploads.forEach((key, stream) -> {
                    if (key.startsWith(clientName + "->") || key.contains("->" + clientName + "::")) {
                        try { stream.close(); } catch (IOException ex) { ex.printStackTrace(); }
                        File file = tempFiles.remove(key);
                        if (file != null) file.delete();
                    }
                });
            }
        }
    }

    private void handleVoiceInvite(String recipientName) {
        ClientHandler recipient = findClientByName(recipientName);
        if (recipient != null) {
            if (recipient.isInCall()) {
                sendMessage("CALL_BUSY§§" + recipientName);
                return;
            }
            System.out.println("🎤 " + clientName + " приглашает в голосовой чат -> " + recipientName);
            recipient.sendMessage("VOICE_INCOMING§§" + this.clientName);
        }
    }

    private void handleVoiceAccept(String callerName) {
        ClientHandler caller = findClientByName(callerName);
        if (caller != null) {
            if (caller.isInCall()) {
                sendMessage("CALL_BUSY§§" + callerName);
                return;
            }
            System.out.println("✅ " + this.clientName + " принял голосовой чат от " + callerName);
            this.setInVoiceChatWith(callerName);
            caller.setInVoiceChatWith(this.clientName);

            caller.sendMessage("VOICE_START§§" + this.clientName);
            this.sendMessage("VOICE_START§§" + callerName);
        }
    }

    private void handleVoiceDecline(String callerName) {
        ClientHandler caller = findClientByName(callerName);
        if (caller != null) {
            System.out.println("❌ " + this.clientName + " отклонил голосовой чат от " + callerName);
            caller.sendMessage("VOICE_DECLINED§§" + this.clientName);
        }
    }

    private void handleVoiceEnd(String partnerName) {
        ClientHandler partner = findClientByName(partnerName);
        System.out.println("🎤 " + this.clientName + " завершил голосовой чат с " + (partnerName != null ? partnerName : "???"));
        this.endVoiceChat();

        if (partner != null) {
            partner.endVoiceChat();
            partner.sendMessage("VOICE_END");
        }
    }

    private void handleAudioChunk(String recipientName, String audioData) {
        ClientHandler recipient = findClientByName(recipientName);
        if (recipient != null && recipient.currentVoiceChatPartner != null && recipient.currentVoiceChatPartner.equals(this.clientName)) {
            // ИСПРАВЛЕНИЕ: Формируем чистое сообщение для пересылки
            recipient.sendMessage("AUDIO_CHUNK§§" + audioData);
        }
    }

    private void handleCallInitiate(String recipientName) {
        ClientHandler recipient = findClientByName(recipientName);
        if (recipient != null) {
            if (recipient.isInCall()) {
                sendMessage("CALL_BUSY§§" + recipientName);
                return;
            }
            String roomName = "pandora-call-" + UUID.randomUUID().toString();
            System.out.println("📞 " + clientName + " звонит -> " + recipientName + " | Комната: " + roomName);
            recipient.sendMessage("CALL_INCOMING§§" + this.clientName + "§§" + roomName);
        }
    }

    private void handleCallAccept(String callerName, String roomName) {
        ClientHandler caller = findClientByName(callerName);
        if (caller != null) {
            System.out.println("✅ " + this.clientName + " принял видеозвонок от " + callerName);
            this.setInCallWith(callerName);
            caller.setInCallWith(this.clientName);
            caller.sendMessage("CALL_STARTED§§" + this.clientName + "§§" + roomName);
        }
    }

    private void handleCallDecline(String callerName) {
        ClientHandler caller = findClientByName(callerName);
        if (caller != null) {
            System.out.println("❌ " + this.clientName + " отклонил видеозвонок от " + callerName);
            caller.sendMessage("CALL_DECLINED§§" + this.clientName);
        }
    }

    private void handleCallEnd(String partnerName) {
        ClientHandler partner = findClientByName(partnerName);
        System.out.println("📞 " + this.clientName + " завершил видеозвонок с " + (partnerName != null ? partnerName : "???"));
        this.endCall();
        if (partner != null) {
            partner.endCall();
            partner.sendMessage("CALL_ENDED§§" + this.clientName);
        }
    }

    private void handleFileOffer(String recipientName, String filename, long filesize, String previewData) {
        ClientHandler recipient = findClientByName(recipientName);
        if (recipient != null) {
            recipient.sendMessage(String.format("FILE_INCOMING§§%s§§%s§§%d§§%s", this.clientName, filename, filesize, previewData));
        }
    }

    private void handleFileAccept(String originalSenderName, String filename) {
        ClientHandler sender = findClientByName(originalSenderName);
        if (sender != null) {
            System.out.println(clientName + " принял файл '" + filename + "' от " + originalSenderName + ". Запрашиваю загрузку.");
            sender.sendMessage(String.format("UPLOAD_START§§%s§§%s", this.clientName, filename));
        }
    }

    private void handleFileDecline(String originalSenderName, String filename) {
        ClientHandler sender = findClientByName(originalSenderName);
        if (sender != null) {
            sender.sendMessage(String.format("SYS_MSG§§%s отклонил ваш файл '%s'", this.clientName, filename));
        }
    }

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
            fos.write(Base64.getDecoder().decode(base64ChunkData));
        } catch (Exception e) {
            System.err.println("Ошибка при обработке FILE_CHUNK для ключа " + fileKey + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

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
                    tempFile.delete();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private ClientHandler findClientByName(String name) {
        synchronized(clients) {
            for (ClientHandler client : clients) {
                if (client.getClientName().equals(name)) return client;
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
            for (ClientHandler client : clients) {
                usersList.append(client.getClientName()).append(",");
            }
        }
        if (usersList.length() > "USERS_LIST§§".length() && usersList.charAt(usersList.length() - 1) == ',') {
            usersList.setLength(usersList.length() - 1);
        }
        broadcastMessage(usersList.toString());
    }

    private void broadcastMessage(String message) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.sendMessage(message);
            }
        }
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