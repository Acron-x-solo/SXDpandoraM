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
}