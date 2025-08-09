package anti.messanger.sxdpandoram;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ChatClient extends Application {

    private PrintWriter out;
    private BufferedReader in;
    private Stage primaryStage;
    private String currentUsername;
    private String activeChat = "Общий чат";

    private final ObservableList<ChatMessage> allMessages = FXCollections.observableArrayList();
    private final FilteredList<ChatMessage> filteredMessages = new FilteredList<>(allMessages);

    private final ObservableList<String> userList = FXCollections.observableArrayList();
    private Label feedbackLabel;

    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        primaryStage.setTitle("Мессенджер");
        primaryStage.setScene(createLoginScene());
        primaryStage.show();
        new Thread(this::connectToServer).start();
    }

    private Scene createLoginScene() {
        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(25, 25, 25, 25));
        Label title = new Label("Вход или Регистрация");
        grid.add(title, 0, 0, 2, 1);
        Label userName = new Label("Логин:");
        grid.add(userName, 0, 1);
        TextField userTextField = new TextField();
        grid.add(userTextField, 1, 1);
        Label pw = new Label("Пароль:");
        grid.add(pw, 0, 2);
        PasswordField pwBox = new PasswordField();
        grid.add(pwBox, 1, 2);
        Button loginBtn = new Button("Войти");
        Button registerBtn = new Button("Регистрация");
        VBox hbBtn = new VBox(10, loginBtn, registerBtn);
        grid.add(hbBtn, 1, 4);
        this.feedbackLabel = new Label();
        grid.add(feedbackLabel, 1, 6);
        loginBtn.setOnAction(e -> {
            String username = userTextField.getText();
            String password = pwBox.getText();
            if (out != null && !username.isEmpty() && !password.isEmpty()) {
                this.currentUsername = username;
                out.println("LOGIN " + username + " " + password);
            }
        });
        registerBtn.setOnAction(e -> {
            String username = userTextField.getText();
            String password = pwBox.getText();
            if (out != null && !username.isEmpty() && !password.isEmpty()) {
                out.println("REGISTER " + username + " " + password);
            }
        });
        return new Scene(grid, 350, 275);
    }

    private Scene createChatScene() {
        BorderPane layout = new BorderPane();
        Label chatHeader = new Label("Общий чат");
        TextField inputField = new TextField(); // Поле ввода теперь создается здесь
        layout.setLeft(createLeftPanel(chatHeader, inputField));
        layout.setCenter(createCenterPanel(chatHeader, inputField));
        out.println("LIST_USERS");
        return new Scene(layout, 900, 600);
    }

    private VBox createLeftPanel(Label chatHeader, TextField inputField) {
        TextField searchField = new TextField();
        searchField.setPromptText("Поиск...");
        FilteredList<String> filteredUsers = new FilteredList<>(userList, p -> true);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filteredUsers.setPredicate(user -> user.toLowerCase().contains(newVal.toLowerCase())));

        ListView<String> userListView = new ListView<>(filteredUsers);
        VBox.setVgrow(userListView, Priority.ALWAYS);

        userListView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                activeChat = newSelection;
                if (activeChat.equals("Общий чат")) {
                    chatHeader.setText("Общий чат");
                    inputField.setPromptText("Введите сообщение для всех...");
                } else {
                    chatHeader.setText("ЛС с " + activeChat);
                    inputField.setPromptText("Сообщение для " + activeChat);
                }
                updateMessageFilter();
            }
        });

        Circle avatar = new Circle(20, Color.LIGHTGRAY);
        Label nameLabel = new Label(this.currentUsername);
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        Button settingsButton = new Button("⚙️");
        HBox profileBox = new HBox(10, avatar, nameLabel, settingsButton);
        profileBox.setAlignment(Pos.CENTER_LEFT);
        profileBox.setPadding(new Insets(10));
        profileBox.setStyle("-fx-background-color: #f0f0f0;");

        VBox leftPanel = new VBox(searchField, userListView, profileBox);
        leftPanel.setPrefWidth(250);
        return leftPanel;
    }

    private BorderPane createCenterPanel(Label chatHeader, TextField inputField) {
        BorderPane centerLayout = new BorderPane();
        chatHeader.setFont(Font.font("System", FontWeight.BOLD, 16));
        HBox topBar = new HBox(chatHeader);
        topBar.setAlignment(Pos.CENTER);
        topBar.setPadding(new Insets(10));
        topBar.setStyle("-fx-background-color: #f0f0f0;");
        centerLayout.setTop(topBar);

        ListView<ChatMessage> messageListView = new ListView<>(filteredMessages);
        messageListView.setCellFactory(param -> new MessageCell());
        centerLayout.setCenter(messageListView);

        inputField.setOnAction(e -> sendMessage(inputField));
        Button sendButton = new Button("▶");
        sendButton.setOnAction(e -> sendMessage(inputField));
        HBox.setHgrow(inputField, Priority.ALWAYS);
        HBox bottomBar = new HBox(10, inputField, sendButton);
        bottomBar.setPadding(new Insets(10));
        centerLayout.setBottom(bottomBar);
        return centerLayout;
    }

    private void updateMessageFilter() {
        filteredMessages.setPredicate(message -> {
            if (activeChat.equals("Общий чат")) {
                return message.getConversationPartner() == null;
            } else {
                return activeChat.equals(message.getConversationPartner());
            }
        });
    }

    private class MessageCell extends ListCell<ChatMessage> {
        @Override
        protected void updateItem(ChatMessage message, boolean empty) {
            super.updateItem(message, empty);
            if (empty || message == null) { setGraphic(null); return; }
            VBox bubble = new VBox(3);
            bubble.setMaxWidth(400);
            String bubbleStyle = "-fx-background-radius: 15; -fx-padding: 8;";
            if (!message.getSender().equals("Система") && !message.isSentByMe()) {
                Label senderLabel = new Label(message.getSender());
                senderLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
                senderLabel.setTextFill(Color.CORNFLOWERBLUE);
                bubble.getChildren().add(senderLabel);
            }
            Label contentLabel = new Label(message.getContent());
            contentLabel.setWrapText(true);
            bubble.getChildren().add(contentLabel);
            if (!message.getTimestamp().isEmpty()) {
                Label timeLabel = new Label(message.getTimestamp());
                timeLabel.setFont(Font.font(10));
                timeLabel.setTextFill(Color.GRAY);
                HBox timeContainer = new HBox(timeLabel);
                timeContainer.setAlignment(Pos.CENTER_RIGHT);
                bubble.getChildren().add(timeContainer);
            }
            HBox wrapper = new HBox();
            if (message.isSentByMe()) {
                bubble.setStyle(bubbleStyle + "-fx-background-color: #dcf8c6;");
                wrapper.setAlignment(Pos.CENTER_RIGHT);
            } else {
                bubble.setStyle(bubbleStyle + "-fx-background-color: #ffffff;");
                wrapper.setAlignment(Pos.CENTER_LEFT);
            }
            if (message.getSender().equals("Система")) {
                bubble.setStyle("-fx-background-color: transparent;");
                contentLabel.setStyle("-fx-text-fill: gray; -fx-font-style: italic;");
                wrapper.setAlignment(Pos.CENTER);
            }
            wrapper.getChildren().add(bubble);
            wrapper.setPadding(new Insets(5, 10, 5, 10));
            setGraphic(wrapper);
        }
    }

    // --- ГЛАВНОЕ ИСПРАВЛЕНИЕ ЗДЕСЬ ---
    private void sendMessage(TextField field) {
        String text = field.getText();
        if (out == null || text.trim().isEmpty()) {
            return;
        }

        // Проверяем КОНТЕКСТ активного чата, а не текст сообщения
        if ("Общий чат".equals(activeChat)) {
            out.println("MSG " + text);
        } else {
            // Если активен любой другой чат (ЛС) - отправляем PM
            out.println("PM " + activeChat + " " + text);
        }
        field.clear();
    }

    private void handleServerMessage(String msg) {
        Platform.runLater(() -> {
            String[] parts = msg.split("§§");
            String command = parts[0];

            switch (command) {
                case "LOGIN_SUCCESS":
                    primaryStage.setScene(createChatScene());
                    break;
                case "LOGIN_FAILED":
                    feedbackLabel.setText("Ошибка: неверный логин/пароль.");
                    break;
                case "REGISTER_SUCCESS":
                    feedbackLabel.setText("Регистрация успешна! Войдите.");
                    break;
                case "REGISTER_FAILED_USER_EXISTS":
                    feedbackLabel.setText("Ошибка: пользователь существует.");
                    break;
                case "PUB_MSG": // время, отправитель, текст
                    if (parts.length == 4) {
                        allMessages.add(new ChatMessage(parts[3], parts[2], parts[1], parts[2].equals(currentUsername), null));
                    }
                    break;
                case "PRIV_MSG": // время, отправитель, получатель, текст
                    if (parts.length == 5) {
                        String sender = parts[2];
                        String recipient = parts[3];
                        String content = parts[4];
                        boolean isMe = sender.equals(currentUsername);
                        String partner = isMe ? recipient : sender;
                        allMessages.add(new ChatMessage(content, sender, parts[1], isMe, partner));
                    }
                    break;
                case "SYS_MSG": // время, текст
                    if (parts.length == 3) {
                        allMessages.add(new ChatMessage(parts[2], "Система", parts[1], false, null));
                    }
                    break;
                case "USERS_LIST":
                    userList.clear();
                    userList.add("Общий чат");
                    if (parts.length > 1 && !parts[1].isEmpty()) {
                        for (String user : parts[1].split(",")) {
                            if (!user.equals(currentUsername)) userList.add(user);
                        }
                    }
                    break;
            }
        });
    }

    private void connectToServer() {
        try {
            Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            startServerListener();
        } catch (IOException e) { Platform.runLater(() -> { if (feedbackLabel != null) feedbackLabel.setText("Ошибка: не удалось подключиться."); }); }
    }

    private void startServerListener() {
        new Thread(() -> {
            try {
                String fromServer;
                while ((fromServer = in.readLine()) != null) {
                    handleServerMessage(fromServer);
                }
            } catch (IOException e) { Platform.runLater(() -> { if (!allMessages.isEmpty()) { allMessages.add(new ChatMessage("!!! ПОТЕРЯНО СОЕДИНЕНИЕ !!!", "Система", "", false, null)); } }); }
        }).start();
    }
}