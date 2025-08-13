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
    private static final Map<String, FileOutputStream> activeFileUploads = new ConcurrentHashMap<>();
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
                String[] parts = inputLine.split(" ", 3);
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
                        if (parts.length >= 4) {
                            String recipient = parts[1];
                            String filename = parts[2];
                            long filesize = Long.parseLong(parts[3]);
                            String previewData = parts.length >= 5 ? parts[4] : "";
                            handleFileOffer(recipient, filename, filesize, previewData);
                        }
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
                    
                    // ==== Профиль ====
                    case "GET_PROFILE":
                        handleGetProfile();
                        break;
                    case "UPDATE_PROFILE":
                        if (parts.length >= 4) handleUpdateProfile(parts[1], parts[2], parts[3]);
                        break;
                    case "UPDATE_AVATAR":
                        if (parts.length == 2) handleUpdateAvatar(parts[1]);
                        break;
                    case "UPDATE_AVATAR_CLEAR":
                        handleUpdateAvatar(null);
                        break;
                    // ==== Группы ====
                    case "CREATE_GROUP":
                        if (parts.length >= 3) handleCreateGroup(parts[1], parts[2]);
                        break;
                    case "GET_GROUPS":
                        handleGetGroups();
                        break;
                    case "ADD_MEMBER":
                        if (parts.length >= 3) handleAddMember(Long.parseLong(parts[1]), parts[2]);
                        break;
                    case "REMOVE_MEMBER":
                        if (parts.length >= 3) handleRemoveMember(Long.parseLong(parts[1]), parts[2]);
                        break;
                    case "GROUP_MSG":
                        if (parts.length >= 3) handleGroupMessage(Long.parseLong(parts[1]), parts[2]);
                        break;
                    // ==== Серверы ====
                    case "CREATE_SERVER":
                        if (parts.length >= 3) handleCreateServer(parts[1], parts[2]);
                        break;
                    case "GET_SERVERS":
                        handleGetServers();
                        break;
                    case "ADD_SERVER_MEMBER":
                        if (parts.length >= 3) handleAddServerMember(Long.parseLong(parts[1]), parts[2]);
                        break;
                    case "SERVER_MSG":
                        if (parts.length >= 3) handleServerMessage(Long.parseLong(parts[1]), parts[2]);
                        break;
                 // ==== Звонки и аудио ====
                 case "CALL_INVITE":
                     if (parts.length >= 2) handleVoiceInvite(parts[1]);
                     break;
                 case "CALL_ACCEPT":
                     if (parts.length >= 2) handleVoiceAccept(parts[1]);
                     break;
                 case "CALL_DECLINE":
                     if (parts.length >= 2) handleVoiceDecline(parts[1]);
                     break;
                 case "VOICE_INVITE":
                     if (parts.length >= 2) handleVoiceInvite(parts[1]);
                     break;
                 case "VOICE_ACCEPT":
                     if (parts.length >= 2) handleVoiceAccept(parts[1]);
                     break;
                 case "VOICE_DECLINE":
                     if (parts.length >= 2) handleVoiceDecline(parts[1]);
                     break;
                 case "VOICE_END":
                     if (parts.length >= 2) handleVoiceEnd(parts[1]);
                     break;
                 case "VOICE_FRAME":
                     if (parts.length >= 3) handleVoiceFrame(parts[1], parts[2]);
                     break;
                 // ==== Демонстрация экрана ====
                 case "SCREEN_START":
                     if (parts.length >= 2) handleScreenStart(parts[1]);
                     break;
                 case "SCREEN_STOP":
                     if (parts.length >= 2) handleScreenStop(parts[1]);
                     break;
                 case "SCREEN_FRAME":
                     if (parts.length >= 3) handleScreenFrame(parts[1], parts[2]);
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

    private void handleGetProfile() {
        UserProfile profile = databaseManager.getUserProfile(this.clientName);
        String avatarBase64 = profile.getAvatarBytes() != null ? Base64.getEncoder().encodeToString(profile.getAvatarBytes()) : "";
        sendMessage(String.format("PROFILE_DATA§§%s§§%s§§%s§§%s", escape(profile.getDisplayName()), escape(profile.getStatus()), escape(profile.getEmail()), avatarBase64));
    }

    private void handleUpdateProfile(String displayName, String status, String email) {
        boolean ok = databaseManager.updateUserProfile(this.clientName, unescape(displayName), unescape(status), unescape(email));
        sendMessage("PROFILE_UPDATED§§" + (ok ? "OK" : "ERROR"));
        if (ok) {
            // По желанию можно уведомлять всех о смене профиля
            // broadcastMessage("PROFILE_CHANGED§§" + this.clientName);
        }
    }

    private void handleUpdateAvatar(String avatarBase64OrNull) {
        byte[] avatarBytes = null;
        if (avatarBase64OrNull != null && !avatarBase64OrNull.isEmpty()) {
            try {
                avatarBytes = Base64.getDecoder().decode(avatarBase64OrNull);
            } catch (IllegalArgumentException e) {
                sendMessage("AVATAR_UPDATED§§ERROR");
                return;
            }
        }
        boolean ok = databaseManager.updateUserAvatar(this.clientName, avatarBytes);
        sendMessage("AVATAR_UPDATED§§" + (ok ? "OK" : "ERROR"));
    }

    private void handleCreateGroup(String groupName, String description) {
        boolean ok = databaseManager.createGroup(groupName, description, this.clientName);
        if (ok) {
            sendMessage("GROUP_CREATED§§OK");
            // Уведомляем всех о создании новой группы
            broadcastMessage(String.format("SYS_MSG§§Создана новая группа: %s", groupName));
        } else {
            sendMessage("GROUP_CREATED§§ERROR");
        }
    }

    private void handleGetGroups() {
        List<GroupInfo> groups = databaseManager.getUserGroups(this.clientName);
        StringBuilder response = new StringBuilder("GROUPS_LIST§§");
        for (GroupInfo group : groups) {
            response.append(group.getId()).append(":")
                   .append(escape(group.getName())).append(":")
                   .append(escape(group.getDescription())).append(":")
                   .append(group.getCreatedBy()).append(":")
                   .append(group.getCreatedAt()).append(":")
                   .append(group.isAdmin() ? "1" : "0").append(",");
        }
        if (response.charAt(response.length() - 1) == ',') {
            response.setLength(response.length() - 1);
        }
        sendMessage(response.toString());
    }

    private void handleAddMember(long groupId, String username) {
        // Проверяем, является ли текущий пользователь админом группы
        if (!databaseManager.isGroupAdmin(groupId, this.clientName)) {
            sendMessage("MEMBER_ADDED§§ERROR§§Недостаточно прав");
            return;
        }
        
        // Проверяем, существует ли пользователь
        if (!databaseManager.verifyUser(username, "")) { // Пустой пароль для проверки существования
            sendMessage("MEMBER_ADDED§§ERROR§§Пользователь не найден");
            return;
        }
        
        boolean ok = databaseManager.addGroupMember(groupId, username, false);
        if (ok) {
            sendMessage("MEMBER_ADDED§§OK§§" + username);
            // Уведомляем добавленного пользователя
            ClientHandler newMember = findClientByName(username);
            if (newMember != null) {
                GroupInfo groupInfo = databaseManager.getGroupInfo(groupId);
                newMember.sendMessage(String.format("GROUP_INVITE§§%d§§%s§§%s", groupId, groupInfo.getName(), this.clientName));
            }
        } else {
            sendMessage("MEMBER_ADDED§§ERROR§§Не удалось добавить участника");
        }
    }

    private void handleRemoveMember(long groupId, String username) {
        // Проверяем, является ли текущий пользователь админом группы
        if (!databaseManager.isGroupAdmin(groupId, this.clientName)) {
            sendMessage("MEMBER_REMOVED§§ERROR§§Недостаточно прав");
            return;
        }
        
        // Нельзя удалить самого себя
        if (username.equals(this.clientName)) {
            sendMessage("MEMBER_REMOVED§§ERROR§§Нельзя удалить самого себя");
            return;
        }
        
        boolean ok = databaseManager.removeGroupMember(groupId, username);
        if (ok) {
            sendMessage("MEMBER_REMOVED§§OK§§" + username);
            // Уведомляем удаленного пользователя
            ClientHandler removedMember = findClientByName(username);
            if (removedMember != null) {
                GroupInfo groupInfo = databaseManager.getGroupInfo(groupId);
                removedMember.sendMessage(String.format("GROUP_REMOVED§§%d§§%s", groupId, groupInfo.getName()));
            }
        } else {
            sendMessage("MEMBER_REMOVED§§ERROR§§Не удалось удалить участника");
        }
    }

    private void handleGroupMessage(long groupId, String message) {
        // Проверяем, является ли пользователь участником группы
        if (!databaseManager.isGroupMember(groupId, this.clientName)) {
            sendMessage("GROUP_MSG_SENT§§ERROR§§Вы не являетесь участником группы");
            return;
        }

        // Отправляем сообщение всем участникам группы (включая отправителя для локальной синхронизации)
        List<String> members = databaseManager.getGroupMembers(groupId);
        String timestamp = getTimestamp();
        String formattedMsg = String.format("GROUP_MSG§§%s§§%s§§%d§§%s", timestamp, this.clientName, groupId, message);

        // Сохраняем сообщение в БД
        databaseManager.saveMessage("GROUP", timestamp, this.clientName, "GROUP_" + groupId, message);

        for (String member : members) {
            ClientHandler memberHandler = findClientByName(member);
            if (memberHandler != null) {
                memberHandler.sendMessage(formattedMsg);
            }
        }

        // Убираем отдельное подтверждение, так как отправителю уже пришло сообщение
    }

    private void handleCreateServer(String serverName, String description) {
        boolean ok = databaseManager.createServer(serverName, description, this.clientName);
        if (ok) {
            sendMessage("SERVER_CREATED§§OK");
            // Уведомляем всех о создании нового сервера
            broadcastMessage(String.format("SYS_MSG§§Создан новый сервер: %s", serverName));
        } else {
            sendMessage("SERVER_CREATED§§ERROR");
        }
    }

    private void handleGetServers() {
        List<ServerInfo> servers = databaseManager.getUserServers(this.clientName);
        StringBuilder response = new StringBuilder("SERVERS_LIST§§");
        for (ServerInfo server : servers) {
            response.append(server.getId()).append(":")
                   .append(escape(server.getName())).append(":")
                   .append(escape(server.getDescription())).append(":")
                   .append(server.getCreatedBy()).append(",");
        }
        if (response.charAt(response.length() - 1) == ',') {
            response.setLength(response.length() - 1);
        }
        sendMessage(response.toString());
    }

    private void handleAddServerMember(long serverId, String username) {
        // Проверяем, является ли текущий пользователь админом сервера
        if (!databaseManager.isServerAdmin(serverId, this.clientName)) {
            sendMessage("SERVER_MEMBER_ADDED§§ERROR§§Недостаточно прав");
            return;
        }
        
        // Проверяем, существует ли пользователь
        if (!databaseManager.verifyUser(username, "")) { // Пустой пароль для проверки существования
            sendMessage("SERVER_MEMBER_ADDED§§ERROR§§Пользователь не найден");
            return;
        }
        
        boolean ok = databaseManager.addServerMember(serverId, username, false);
        if (ok) {
            sendMessage("SERVER_MEMBER_ADDED§§OK§§" + username);
            // Уведомляем добавленного пользователя
            ClientHandler newMember = findClientByName(username);
            if (newMember != null) {
                ServerInfo serverInfo = databaseManager.getServerInfo(serverId);
                newMember.sendMessage(String.format("SERVER_INVITE§§%d§§%s§§%s", serverId, serverInfo.getName(), this.clientName));
            }
        } else {
            sendMessage("SERVER_MEMBER_ADDED§§ERROR§§Не удалось добавить участника");
        }
    }

    private void handleServerMessage(long serverId, String message) {
        // Проверяем, является ли пользователь участником сервера
        if (!databaseManager.isServerMember(serverId, this.clientName)) {
            sendMessage("SERVER_MSG_SENT§§ERROR§§Вы не являетесь участником сервера");
            return;
        }

        // Отправляем сообщение всем участникам сервера (включая отправителя для локальной синхронизации)
        List<String> members = databaseManager.getServerMembers(serverId);
        String timestamp = getTimestamp();
        String formattedMsg = String.format("SERVER_MSG§§%s§§%s§§%d§§%s", timestamp, this.clientName, serverId, message);

        // Сохраняем сообщение в БД
        databaseManager.saveMessage("SERVER", timestamp, this.clientName, "SERVER_" + serverId, message);

        for (String member : members) {
            ClientHandler memberHandler = findClientByName(member);
            if (memberHandler != null) {
                memberHandler.sendMessage(formattedMsg);
            }
        }
    }

    private String escape(String s) { return s == null ? "" : s.replace("\n", "\\n"); }
    private String unescape(String s) { return s == null ? "" : s.replace("\\n", "\n"); }

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

         // ====== Voice Call helpers ======
     private void handleVoiceInvite(String callee) {
         ClientHandler r = findClientByName(callee);
         if (r != null) {
             r.sendMessage("CALL_INVITE " + this.clientName);
         } else {
             sendMessage("SYS_MSG§§Пользователь недоступен для звонка");
         }
     }

         private void handleVoiceAccept(String caller) {
         ClientHandler c = findClientByName(caller);
         if (c != null) {
             // Подтверждаем обоим начало звонка
             c.sendMessage("CALL_ACCEPTED " + this.clientName);
             sendMessage("CALL_ACCEPTED " + caller);
         }
     }

         private void handleVoiceDecline(String caller) {
         ClientHandler c = findClientByName(caller);
         if (c != null) {
             c.sendMessage("CALL_DECLINED " + this.clientName);
         }
     }

         private void handleVoiceEnd(String peer) {
         ClientHandler p = findClientByName(peer);
         if (p != null) {
             p.sendMessage("VOICE_END " + this.clientName);
         }
     }

         private void handleVoiceFrame(String recipient, String base64Data) {
         ClientHandler r = findClientByName(recipient);
         if (r != null) {
             // Пересылаем кадр получателю с именем отправителя
             r.sendMessage("AUDIO_CHUNK " + this.clientName + " " + base64Data);
         }
     }

    private void handleScreenStart(String recipient) {
        ClientHandler r = findClientByName(recipient);
        if (r != null) {
            r.sendMessage("SYS_MSG§§" + this.clientName + " начал демонстрацию экрана.");
        }
    }

    private void handleScreenStop(String recipient) {
        ClientHandler r = findClientByName(recipient);
        if (r != null) {
            r.sendMessage("SYS_MSG§§" + this.clientName + " остановил демонстрацию экрана.");
        }
    }

         private void handleScreenFrame(String recipient, String base64Jpeg) {
         ClientHandler r = findClientByName(recipient);
         if (r != null) {
             r.sendMessage("SCREEN_FRAME " + this.clientName + " " + base64Jpeg);
         }
     }
}