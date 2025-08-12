package anti.messanger.sxdpandoram;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    private Connection connection;

    public DatabaseManager() {
        try {
            String url = "jdbc:sqlite:chat_database.db";
            connection = DriverManager.getConnection(url);
            System.out.println("✅ Успешное подключение к базе данных SQLite.");
            createUsersTable();
            createMessagesTable();
            createGroupsTable();
            createGroupMembersTable();
            createServersTable();
            createServerMembersTable();
            ensureProfileColumns();
        } catch (SQLException e) {
            System.err.println("❌ Ошибка подключения к базе данных: " + e.getMessage());
        }
    }

    private void createUsersTable() {
        String sql = "CREATE TABLE IF NOT EXISTS users (id integer PRIMARY KEY AUTOINCREMENT, username text NOT NULL UNIQUE, password text NOT NULL);";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.err.println("❌ Ошибка при создании таблицы пользователей: " + e.getMessage());
        }
    }

    private void createMessagesTable() {
        String sql = "CREATE TABLE IF NOT EXISTS messages (id integer PRIMARY KEY AUTOINCREMENT, timestamp text NOT NULL, sender text NOT NULL, recipient text, content text NOT NULL, type text NOT NULL);";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.err.println("❌ Ошибка при создании таблицы сообщений: " + e.getMessage());
        }
    }

    private void createGroupsTable() {
        String sql = "CREATE TABLE IF NOT EXISTS groups (id integer PRIMARY KEY AUTOINCREMENT, name text NOT NULL, description text, created_by text NOT NULL, created_at text NOT NULL);";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.err.println("❌ Ошибка при создании таблицы групп: " + e.getMessage());
        }
    }

    private void createGroupMembersTable() {
        String sql = "CREATE TABLE IF NOT EXISTS group_members (id integer PRIMARY KEY AUTOINCREMENT, group_id integer NOT NULL, username text NOT NULL, joined_at text NOT NULL, is_admin boolean DEFAULT 0, UNIQUE(group_id, username), FOREIGN KEY(group_id) REFERENCES groups(id));";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.err.println("❌ Ошибка при создании таблицы участников групп: " + e.getMessage());
        }
    }

    private void createServersTable() {
        String sql = "CREATE TABLE IF NOT EXISTS servers (id integer PRIMARY KEY AUTOINCREMENT, name text NOT NULL, description text, created_by text NOT NULL, created_at text NOT NULL);";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.err.println("❌ Ошибка при создании таблицы серверов: " + e.getMessage());
        }
    }

    private void createServerMembersTable() {
        String sql = "CREATE TABLE IF NOT EXISTS server_members (id integer PRIMARY KEY AUTOINCREMENT, server_id integer NOT NULL, username text NOT NULL, joined_at text NOT NULL, is_admin boolean DEFAULT 0, UNIQUE(server_id, username), FOREIGN KEY(server_id) REFERENCES servers(id));";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.err.println("❌ Ошибка при создании таблицы участников серверов: " + e.getMessage());
        }
    }

    // Добавление недостающих колонок профиля в таблицу users
    private void ensureProfileColumns() {
        try (Statement stmt = connection.createStatement()) {
            // Выясняем, какие столбцы уже есть
            List<String> existing = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(users)")) {
                while (rs.next()) {
                    existing.add(rs.getString("name"));
                }
            }
            if (!existing.contains("display_name")) {
                stmt.execute("ALTER TABLE users ADD COLUMN display_name TEXT");
            }
            if (!existing.contains("status")) {
                stmt.execute("ALTER TABLE users ADD COLUMN status TEXT");
            }
            if (!existing.contains("avatar")) {
                stmt.execute("ALTER TABLE users ADD COLUMN avatar BLOB");
            }
        } catch (SQLException e) {
            System.err.println("⚠️ Не удалось выполнить миграцию колонок профиля: " + e.getMessage());
        }
    }

    public void saveMessage(String type, String timestamp, String sender, String recipient, String content) {
        String sql = "INSERT INTO messages(type, timestamp, sender, recipient, content) VALUES(?,?,?,?,?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, type);
            pstmt.setString(2, timestamp);
            pstmt.setString(3, sender);
            pstmt.setString(4, recipient);
            pstmt.setString(5, content);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Ошибка при сохранении сообщения: " + e.getMessage());
        }
    }

    /**
     * Загружает историю сообщений, относящихся к конкретному пользователю
     * (публичные, системные и личные, где он отправитель или получатель).
     */
    public List<String> loadChatHistoryForUser(String username) {
        List<String> history = new ArrayList<>();
        // Загружаем последние 100 релевантных сообщений
        String sql = "SELECT * FROM (SELECT * FROM messages WHERE recipient IS NULL OR recipient = ? OR sender = ? ORDER BY id DESC LIMIT 100) ORDER BY id ASC;";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, username);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String type = rs.getString("type");
                String timestamp = rs.getString("timestamp");
                String sender = rs.getString("sender");
                String recipient = rs.getString("recipient");
                String content = rs.getString("content");
                String formattedMessage = "";
                switch (type) {
                    case "PUBLIC":
                        formattedMessage = String.format("PUB_MSG§§%s§§%s§§%s", timestamp, sender, content);
                        break;
                    case "PRIVATE":
                        formattedMessage = String.format("PRIV_MSG§§%s§§%s§§%s§§%s", timestamp, sender, recipient, content);
                        break;
                    case "SYSTEM":
                        formattedMessage = String.format("SYS_MSG§§%s§§%s", timestamp, content);
                        break;
                }
                if (!formattedMessage.isEmpty()) {
                    history.add(formattedMessage);
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка при загрузке истории: " + e.getMessage());
        }
        return history;
    }

    /**
     * Получает список всех уникальных пользователей, с которыми общался данный юзер.
     */
    public List<String> getConversationPartners(String username) {
        List<String> partners = new ArrayList<>();
        String sql = "SELECT DISTINCT sender as partner FROM messages WHERE recipient = ? " +
                "UNION " +
                "SELECT DISTINCT recipient as partner FROM messages WHERE sender = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, username);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String partner = rs.getString("partner");
                if (partner != null && !partner.equals(username) && !partner.equals("System")) {
                    partners.add(partner);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return partners;
    }

    public boolean registerUser(String username, String password) {
        String checkSql = "SELECT id FROM users WHERE username = ?";
        try (PreparedStatement checkPstmt = connection.prepareStatement(checkSql)) {
            checkPstmt.setString(1, username);
            if (checkPstmt.executeQuery().next()) return false;
        } catch (SQLException e) { e.printStackTrace(); return false; }
        String insertSql = "INSERT INTO users(username, password) VALUES(?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(insertSql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean verifyUser(String username, String password) {
        String sql = "SELECT password FROM users WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getString("password").equals(password);
            return false;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    // ===== Профили пользователей =====

    public UserProfile getUserProfile(String username) {
        String sql = "SELECT username, COALESCE(display_name, ''), COALESCE(status, ''), avatar FROM users WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String displayName = rs.getString(2);
                    String status = rs.getString(3);
                    byte[] avatar = rs.getBytes(4);
                    return new UserProfile(username, displayName, status, avatar);
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка чтения профиля: " + e.getMessage());
        }
        return new UserProfile(username, "", "", null);
    }

    public boolean updateUserProfile(String username, String displayName, String status) {
        String sql = "UPDATE users SET display_name = ?, status = ? WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, displayName);
            pstmt.setString(2, status);
            pstmt.setString(3, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Ошибка обновления профиля: " + e.getMessage());
            return false;
        }
    }

    public boolean updateUserAvatar(String username, byte[] avatarBytes) {
        String sql = "UPDATE users SET avatar = ? WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            if (avatarBytes != null) {
                pstmt.setBytes(1, avatarBytes);
            } else {
                pstmt.setNull(1, Types.BLOB);
            }
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Ошибка обновления аватара: " + e.getMessage());
            return false;
        }
    }

    // ===== Группы =====

    public boolean createGroup(String groupName, String description, String createdBy) {
        String sql = "INSERT INTO groups(name, description, created_by, created_at) VALUES(?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, groupName);
            pstmt.setString(2, description);
            pstmt.setString(3, createdBy);
            pstmt.setString(4, java.time.LocalDateTime.now().toString());
            
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                // Получаем ID созданной группы
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        long groupId = rs.getLong(1);
                        // Добавляем создателя как админа группы
                        return addGroupMember(groupId, createdBy, true);
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            System.err.println("❌ Ошибка создания группы: " + e.getMessage());
            return false;
        }
    }

    public boolean addGroupMember(long groupId, String username, boolean isAdmin) {
        String sql = "INSERT OR REPLACE INTO group_members(group_id, username, joined_at, is_admin) VALUES(?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, groupId);
            pstmt.setString(2, username);
            pstmt.setString(3, java.time.LocalDateTime.now().toString());
            pstmt.setBoolean(4, isAdmin);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Ошибка добавления участника группы: " + e.getMessage());
            return false;
        }
    }

    public boolean removeGroupMember(long groupId, String username) {
        String sql = "DELETE FROM group_members WHERE group_id = ? AND username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, groupId);
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Ошибка удаления участника группы: " + e.getMessage());
            return false;
        }
    }

    public List<GroupInfo> getUserGroups(String username) {
        List<GroupInfo> groups = new ArrayList<>();
        String sql = "SELECT g.id, g.name, g.description, g.created_by, g.created_at, gm.is_admin " +
                    "FROM groups g " +
                    "JOIN group_members gm ON g.id = gm.group_id " +
                    "WHERE gm.username = ? " +
                    "ORDER BY g.name";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    GroupInfo group = new GroupInfo(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("created_by"),
                        rs.getString("created_at"),
                        rs.getBoolean("is_admin")
                    );
                    groups.add(group);
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка получения групп пользователя: " + e.getMessage());
        }
        return groups;
    }

    public List<String> getGroupMembers(long groupId) {
        List<String> members = new ArrayList<>();
        String sql = "SELECT username FROM group_members WHERE group_id = ? ORDER BY username";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, groupId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    members.add(rs.getString("username"));
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка получения участников группы: " + e.getMessage());
        }
        return members;
    }

    public GroupInfo getGroupInfo(long groupId) {
        String sql = "SELECT id, name, description, created_by, created_at FROM groups WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, groupId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new GroupInfo(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("created_by"),
                        rs.getString("created_at"),
                        false // isAdmin будет определено отдельно
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка получения информации о группе: " + e.getMessage());
        }
        return null;
    }

    public boolean isGroupMember(long groupId, String username) {
        String sql = "SELECT 1 FROM group_members WHERE group_id = ? AND username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, groupId);
            pstmt.setString(2, username);
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            System.err.println("❌ Ошибка проверки участника группы: " + e.getMessage());
            return false;
        }
    }

    public boolean isGroupAdmin(long groupId, String username) {
        String sql = "SELECT is_admin FROM group_members WHERE group_id = ? AND username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, groupId);
            pstmt.setString(2, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("is_admin");
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка проверки админа группы: " + e.getMessage());
        }
        return false;
    }

    // ===== Серверы =====

    public boolean createServer(String serverName, String description, String createdBy) {
        String sql = "INSERT INTO servers(name, description, created_by, created_at) VALUES(?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, serverName);
            pstmt.setString(2, description);
            pstmt.setString(3, createdBy);
            pstmt.setString(4, java.time.LocalDateTime.now().toString());
            
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                // Получаем ID созданного сервера
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        long serverId = rs.getLong(1);
                        // Добавляем создателя как админа сервера
                        return addServerMember(serverId, createdBy, true);
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            System.err.println("❌ Ошибка создания сервера: " + e.getMessage());
            return false;
        }
    }

    public boolean addServerMember(long serverId, String username, boolean isAdmin) {
        String sql = "INSERT OR REPLACE INTO server_members(server_id, username, joined_at, is_admin) VALUES(?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, serverId);
            pstmt.setString(2, username);
            pstmt.setString(3, java.time.LocalDateTime.now().toString());
            pstmt.setBoolean(4, isAdmin);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Ошибка добавления участника сервера: " + e.getMessage());
            return false;
        }
    }

    public List<ServerInfo> getUserServers(String username) {
        List<ServerInfo> servers = new ArrayList<>();
        String sql = "SELECT s.id, s.name, s.description, s.created_by " +
                    "FROM servers s " +
                    "JOIN server_members sm ON s.id = sm.server_id " +
                    "WHERE sm.username = ? " +
                    "ORDER BY s.name";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    ServerInfo server = new ServerInfo(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("created_by")
                    );
                    servers.add(server);
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка получения серверов пользователя: " + e.getMessage());
        }
        return servers;
    }

    public List<String> getServerMembers(long serverId) {
        List<String> members = new ArrayList<>();
        String sql = "SELECT username FROM server_members WHERE server_id = ? ORDER BY username";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, serverId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    members.add(rs.getString("username"));
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка получения участников сервера: " + e.getMessage());
        }
        return members;
    }

    public boolean isServerMember(long serverId, String username) {
        String sql = "SELECT 1 FROM server_members WHERE server_id = ? AND username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, serverId);
            pstmt.setString(2, username);
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            System.err.println("❌ Ошибка проверки участника сервера: " + e.getMessage());
            return false;
        }
    }

    public boolean isServerAdmin(long serverId, String username) {
        String sql = "SELECT is_admin FROM server_members WHERE server_id = ? AND username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, serverId);
            pstmt.setString(2, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("is_admin");
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка проверки админа сервера: " + e.getMessage());
        }
        return false;
    }

    public ServerInfo getServerInfo(long serverId) {
        String sql = "SELECT id, name, description, created_by FROM servers WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, serverId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new ServerInfo(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("created_by")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка получения информации о сервере: " + e.getMessage());
        }
        return null;
    }
}