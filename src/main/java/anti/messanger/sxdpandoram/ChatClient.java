package anti.messanger.sxdpandoram;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Hyperlink;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import javafx.util.StringConverter;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;

import javax.imageio.ImageIO;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Arrays;
import java.awt.Desktop;

public class ChatClient extends Application {

    private enum MediaType { IMAGE, VIDEO, OTHER }
    
    private enum Theme {
        DISCORD_DARK("#36393f", "#2f3136", "#202225", "#ffffff", "#7289da", "#99aab5"),
        DISCORD_LIGHT("#ffffff", "#f6f6f7", "#e3e5e8", "#2e3338", "#7289da", "#99aab5"),
        DARK_BLUE("#1a1a2e", "#16213e", "#0f3460", "#ffffff", "#4a90e2", "#99aab5"),
        GREEN_DARK("#1a1a1a", "#2d2d2d", "#404040", "#00ff00", "#00cc00", "#99aab5");
        
        private final String primary;
        private final String secondary;
        private final String tertiary;
        private final String text;
        private final String accent;
        private final String muted;
        
        Theme(String primary, String secondary, String tertiary, String text, String accent, String muted) {
            this.primary = primary;
            this.secondary = secondary;
            this.tertiary = tertiary;
            this.text = text;
            this.accent = accent;
            this.muted = muted;
        }
        
        public String getPrimary() { return primary; }
        public String getSecondary() { return secondary; }
        public String getTertiary() { return tertiary; }
        public String getText() { return text; }
        public String getAccent() { return accent; }
        public String getMuted() { return muted; }
    }

    private PrintWriter out;
    private BufferedReader in;
    private Stage primaryStage;
    private String currentUsername;
    private String activeChat = "Общий чат";

    private final ObservableList<ChatMessage> allMessages = FXCollections.observableArrayList();
    private final FilteredList<ChatMessage> filteredMessages = new FilteredList<>(allMessages);
    private final ObservableList<String> userList = FXCollections.observableArrayList("Общий чат");
    private Label feedbackLabel;

    private ListView<String> userListView;
    private ComboBox<String> microphoneComboBox;
    private TextField messageTextField;

    private final Map<String, File> offeredFiles = new HashMap<>();
    private final Map<String, Stage> activeCallWindows = new HashMap<>();
    
    // Поля для голосовых звонков
    private VoiceCallManager voiceCallManager;
    private ScreenShareManager screenShareManager;
    private boolean isInVoiceChat = false;
    private boolean isScreenSharing = false;
    private Button voiceCallButton;
    private Button hangUpButton;
    
    // Поля для расширенного функционала
    private Theme currentTheme = Theme.DISCORD_DARK;
    private String profileEmail = "";
    private String displayName = "";
    private String avatarPath = "";
    private final Properties sessionSettings = new Properties();
    private final File settingsFile = new File("session_settings.properties");
    private ListView<ChatMessage> messageListView;
    
    // Поля для демонстрации экрана
    private Stage screenShareStage;
    private ImageView screenShareView;
    private Stage callStage;
    
    // Поля для интегрированных панелей
    private VBox callPanel;
    private VBox screenSharePanel;
    private BorderPane integratedCallPanel;
    private BorderPane integratedScreenSharePanel;
    private ImageView callPreviewImageView;
    private ImageView peerPreviewImageView;
    private String currentCallPeer;
    
    // Поля для сохранения входа
    private String savedUsername = "";
    private String savedPassword = "";
    
    // Добавляем недостающие поля
    private Socket socket;
    private Timeline previewTimer;

    private static final String SERVER_CHAT_ADDRESS = "into-eco.gl.at.ply.gg";
    private static final int SERVER_CHAT_PORT = 59462;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("SXD Pandora M - Вход");
        
        // Загружаем настройки сессии
        loadSessionSettings();
        
        // Создаем сцену входа
        Scene loginScene = createLoginScene();
        primaryStage.setScene(loginScene);
        primaryStage.show();
        
        // Подключаемся к серверу
        connectToServer();
    }

    @Override
    public void stop() {
        // Сохраняем настройки при закрытии
        saveSessionSettings();
        
        // Закрываем соединение с сервером
        if (out != null) {
            out.println("LOGOUT");
        }
        
        // Останавливаем все активные процессы
        if (voiceCallManager != null) {
            voiceCallManager.stopStreaming();
        }
        
        if (screenShareManager != null) {
            screenShareManager.stopSharing();
        }
        
        if (previewTimer != null) {
            previewTimer.stop();
        }
        
        // Закрываем сокет
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Ошибка при закрытии сокета: " + e.getMessage());
        }
    }

    private MediaType getMediaType(String fileName) {
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".gif") || lowerName.endsWith(".bmp")) {
            return MediaType.IMAGE;
        }
        if (lowerName.endsWith(".mp4") || lowerName.endsWith(".mov") || lowerName.endsWith(".m4v") || lowerName.endsWith(".avi")) {
            return MediaType.VIDEO;
        }
        return MediaType.OTHER;
    }

    private void showImagePreview(ChatMessage message) {
        Stage previewStage = new Stage();
        previewStage.setTitle("Просмотр: " + message.getFileName());
        Image image = new Image(message.getDownloadUrl(), 1920, 1080, true, true, true);
        ImageView imageView = new ImageView(image);
        imageView.setPreserveRatio(true);
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.progressProperty().bind(image.progressProperty());
        progressIndicator.visibleProperty().bind(image.progressProperty().isNotEqualTo(1));
        ScrollPane scrollPane = new ScrollPane(imageView);
        scrollPane.setPannable(true);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                event.consume();
                double zoomFactor = event.getDeltaY() > 0 ? 1.1 : 1.0 / 1.1;
                imageView.setScaleX(imageView.getScaleX() * zoomFactor);
                imageView.setScaleY(imageView.getScaleY() * zoomFactor);
            }
        });
        StackPane root = new StackPane(scrollPane, progressIndicator);
        root.setStyle("-fx-background-color: #2e2e2e;");
        Scene scene = new Scene(root, 1024, 768);
        previewStage.setScene(scene);
        previewStage.show();
    }

    private void showVideoPreview(ChatMessage message) {
        Stage previewStage = new Stage();
        previewStage.setTitle("Просмотр: " + message.getFileName());
        
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #2f3136;");
        
        Label titleLabel = new Label("Видео: " + message.getFileName());
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        titleLabel.setStyle("-fx-text-fill: white;");
        
        Label infoLabel = new Label("Для просмотра видео используйте внешний плеер");
        infoLabel.setStyle("-fx-text-fill: #b9bbbe;");
        
        Button openInSystemPlayerButton = new Button("Открыть в системном плеере");
        openInSystemPlayerButton.setStyle("-fx-background-color: #7289da; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 10 20; -fx-background-radius: 5;");
        openInSystemPlayerButton.setOnAction(e -> {
            try {
                getHostServices().showDocument(message.getDownloadUrl());
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Ошибка", "Не удалось открыть видео в системном плеере");
            }
        });
        
        Button downloadButton = new Button("Скачать файл");
        downloadButton.setStyle("-fx-background-color: #43b581; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 10 20; -fx-background-radius: 5;");
        downloadButton.setOnAction(e -> {
            try {
                getHostServices().showDocument(message.getDownloadUrl());
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Ошибка", "Не удалось скачать файл");
            }
        });
        
        Button closeButton = new Button("Закрыть");
        closeButton.setStyle("-fx-background-color: #4f545c; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 10 20; -fx-background-radius: 5;");
        closeButton.setOnAction(e -> previewStage.close());
        
        HBox buttons = new HBox(15, openInSystemPlayerButton, downloadButton, closeButton);
        buttons.setAlignment(Pos.CENTER);
        
        root.getChildren().addAll(titleLabel, infoLabel, buttons);
        
        Scene scene = new Scene(root, 400, 200);
            previewStage.setScene(scene);
            previewStage.show();
    }

    private String formatDuration(Duration duration) {
        if (duration == null || duration.isUnknown() || duration.isIndefinite()) return "00:00";
        long totalSeconds = Math.round(duration.toSeconds());
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private Scene createLoginScene() {
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(50));
        root.setStyle("-fx-background-color: " + currentTheme.getPrimary() + ";");
        
        Label titleLabel = new Label("SXD Pandora M");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 32));
        titleLabel.setStyle("-fx-text-fill: " + currentTheme.getText() + ";");
        
        TextField usernameField = new TextField();
        usernameField.setPromptText("Имя пользователя");
        usernameField.setStyle("-fx-background-color: " + currentTheme.getSecondary() + "; -fx-text-fill: " + currentTheme.getText() + "; -fx-border-color: " + currentTheme.getTertiary() + "; -fx-border-radius: 5; -fx-background-radius: 5; -fx-padding: 10; -fx-font-size: 14;");
        usernameField.setPrefWidth(300);
        
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Пароль");
        passwordField.setStyle("-fx-background-color: " + currentTheme.getSecondary() + "; -fx-text-fill: " + currentTheme.getText() + "; -fx-border-color: " + currentTheme.getTertiary() + "; -fx-border-radius: 5; -fx-background-radius: 5; -fx-padding: 10; -fx-font-size: 14;");
        passwordField.setPrefWidth(300);
        
        Button loginButton = new Button("Войти");
        loginButton.setStyle("-fx-background-color: " + currentTheme.getAccent() + "; -fx-text-fill: " + currentTheme.getText() + "; -fx-font-size: 16; -fx-padding: 12 30; -fx-background-radius: 5; -fx-border-radius: 5;");
        loginButton.setPrefWidth(300);
        
        Button registerButton = new Button("Регистрация");
        registerButton.setStyle("-fx-background-color: " + currentTheme.getSecondary() + "; -fx-text-fill: " + currentTheme.getText() + "; -fx-font-size: 14; -fx-padding: 10 20; -fx-background-radius: 5; -fx-border-radius: 5;");
        registerButton.setPrefWidth(300);
        
        Label feedbackLabel = new Label("");
        feedbackLabel.setStyle("-fx-text-fill: " + currentTheme.getMuted() + "; -fx-font-size: 12;");
        this.feedbackLabel = feedbackLabel;
        
        loginButton.setOnAction(e -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText();
            
            if (username.isEmpty() || password.isEmpty()) {
                feedbackLabel.setText("Пожалуйста, заполните все поля");
                return;
            }
            
            if (out != null) {
                currentUsername = username; // Сохраняем имя пользователя
                out.println("LOGIN " + username + " " + password);
                feedbackLabel.setText("Подключение...");
            } else {
                feedbackLabel.setText("Ошибка: нет соединения с сервером");
            }
        });
        
        registerButton.setOnAction(e -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText();
            
            if (username.isEmpty() || password.isEmpty()) {
                feedbackLabel.setText("Пожалуйста, заполните все поля");
                return;
            }
            
            if (out != null) {
                out.println("REGISTER " + username + " " + password);
                feedbackLabel.setText("Регистрация...");
            } else {
                feedbackLabel.setText("Ошибка: нет соединения с сервером");
            }
        });
        
        root.getChildren().addAll(titleLabel, usernameField, passwordField, loginButton, registerButton, feedbackLabel);
        return new Scene(root, 400, 500);
    }

    private Scene createChatScene() {
        // Инициализируем VoiceCallManager после подключения к серверу
        if (voiceCallManager == null && out != null) {
            voiceCallManager = new VoiceCallManager(out);
        }
        
        BorderPane layout = new BorderPane();
        Label chatHeader = new Label("Общий чат");
        TextField inputField = new TextField();
        layout.setLeft(createLeftPanel(chatHeader, inputField));
        layout.setCenter(createCenterPanel(chatHeader, inputField));
        
        // Устанавливаем фильтр сообщений для отображения сообщений общего чата
        Platform.runLater(() -> {
            updateMessageFilter();
            // Обновляем видимость кнопок звонков
            updateCallButtonsVisibility();
        });
        
        out.println("LIST_USERS");
        return new Scene(layout, 900, 600);
    }

    private VBox createLeftPanel(Label chatHeader, TextField inputField) {
        TextField searchField = new TextField();
        searchField.setPromptText("Поиск...");
        FilteredList<String> filteredUsers = new FilteredList<>(userList, p -> true);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filteredUsers.setPredicate(user -> user.toLowerCase().contains(newVal.toLowerCase())));
        this.userListView = new ListView<>(filteredUsers);
        VBox.setVgrow(userListView, Priority.ALWAYS);
        userListView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (isInVoiceChat && newSelection != null && !newSelection.equals(activeChat)) {
                Platform.runLater(() -> userListView.getSelectionModel().select(activeChat));
                showAlert(Alert.AlertType.WARNING, "Звонок активен", "Завершите текущий голосовой чат, чтобы сменить собеседника.");
                return;
            }
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
                
                // Обновляем видимость кнопок звонков
                Platform.runLater(() -> updateCallButtonsVisibility());
            }
        });
        userListView.getSelectionModel().select("Общий чат");
        // Создаем аватар с возможностью изменения
        Circle avatar = new Circle(25, Color.LIGHTGRAY);
        if (!avatarPath.isEmpty()) {
            try {
                Image avatarImage = new Image(new File(avatarPath).toURI().toString());
                avatar.setFill(new javafx.scene.paint.ImagePattern(avatarImage));
            } catch (Exception e) {
                // Если не удалось загрузить аватар, оставляем стандартный
            }
        }
        
        Label nameLabel = new Label(displayName.isEmpty() ? this.currentUsername : displayName);
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        
        Button settingsButton = new Button("Настройки");
        settingsButton.setOnAction(e -> showSettingsDialog());
        
        Button createGroupButton = new Button("Создать группу");
        createGroupButton.setOnAction(e -> showCreateGroupDialog());
        
        Button createServerButton = new Button("Создать сервер");
        createServerButton.setOnAction(e -> showCreateServerDialog());
        
        VBox profileBox = new VBox(5, avatar, nameLabel, settingsButton, createGroupButton, createServerButton);
        profileBox.setAlignment(Pos.CENTER_LEFT);
        profileBox.setPadding(new Insets(10));
        profileBox.setStyle("-fx-background-color: #f0f0f0;");
        VBox leftPanel = new VBox(searchField, userListView, profileBox);
        leftPanel.setPrefWidth(250);
        return leftPanel;
    }

    private BorderPane createCenterPanel(Label chatHeader, TextField inputField) {
        BorderPane centerLayout = new BorderPane();
        centerLayout.setStyle("-fx-background-color: transparent;");
        chatHeader.setFont(Font.font("System", FontWeight.BOLD, 16));

        Button videoCallButton = new Button("📞");
        videoCallButton.setTooltip(new Tooltip("Начать видеозвонок"));
        videoCallButton.setOnAction(e -> initiateVideoCall());

        voiceCallButton = new Button("🎤");
        voiceCallButton.setTooltip(new Tooltip("Начать голосовой чат"));
        voiceCallButton.setOnAction(e -> initiateVoiceChat());

        hangUpButton = new Button("❌");
        hangUpButton.setTooltip(new Tooltip("Завершить голосовой чат"));
        hangUpButton.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        hangUpButton.setOnAction(e -> stopVoiceChat());
        hangUpButton.setVisible(false);

        microphoneComboBox = new ComboBox<>();
        microphoneComboBox.setTooltip(new Tooltip("Выберите микрофон"));
        
        // Инициализируем микрофоны после создания VoiceCallManager
        Platform.runLater(() -> {
            if (voiceCallManager != null) {
                microphoneComboBox.setItems(FXCollections.observableArrayList(voiceCallManager.getInputDeviceNames()));
                if (!microphoneComboBox.getItems().isEmpty()) {
                    microphoneComboBox.getSelectionModel().selectFirst();
                }
            }
        });

        // Упрощенная логика видимости кнопок
        videoCallButton.setVisible(false); // Пока отключаем видеозвонки
        voiceCallButton.setVisible(false); // Будет обновляться в updateCallButtonsVisibility
        hangUpButton.setVisible(false);
        microphoneComboBox.setVisible(false);
        
        // Обновляем видимость кнопок после инициализации
        Platform.runLater(() -> {
            if (voiceCallButton != null && hangUpButton != null && microphoneComboBox != null) {
                updateCallButtonsVisibility();
            }
        });

        // Создаем расширенную панель с новыми функциями
        Button themeButton = new Button("🎨 Тема");
        themeButton.setOnAction(e -> showThemeSelector());
        
        Button addFriendButton = new Button("👥 Добавить друга");
        addFriendButton.setOnAction(e -> showAddFriendDialog());
        
        Button saveChatButton = new Button("💾 Сохранить чат");
        saveChatButton.setOnAction(e -> saveChatToFile());
        
        Button screenShareButton = new Button("🖥️ Демонстрация");
        screenShareButton.setTooltip(new Tooltip("Начать/остановить демонстрацию экрана"));
        screenShareButton.setOnAction(e -> toggleScreenSharing());
        
        HBox topBar = new HBox(10, chatHeader, videoCallButton, voiceCallButton, microphoneComboBox, hangUpButton, 
                               themeButton, addFriendButton, saveChatButton, screenShareButton);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(10));
        topBar.setStyle("-fx-background-color: #f0f0f0;");
        centerLayout.setTop(topBar);

        // Интегрированные панели будут созданы в createChatScene
        
        // Создаем стек для переключения между чатом и панелями
        StackPane centerStack = new StackPane();
        
        this.messageListView = new ListView<>(filteredMessages);
        messageListView.setCellFactory(param -> new MessageCell());
        messageListView.setStyle("-fx-background-color: transparent;");
        
        // Создаем интегрированные панели, если они еще не созданы
        if (integratedCallPanel == null) {
            createIntegratedCallPanel();
        }
        if (integratedScreenSharePanel == null) {
            createIntegratedScreenSharePanel();
        }
        
        centerStack.getChildren().addAll(messageListView, integratedCallPanel, integratedScreenSharePanel);
        
        // Показываем чат по умолчанию
        integratedCallPanel.setVisible(false);
        integratedScreenSharePanel.setVisible(false);
        
        centerLayout.setCenter(centerStack);

        inputField.setOnAction(e -> sendMessage(inputField));
        Button sendButton = new Button("▶");
        sendButton.setOnAction(e -> sendMessage(inputField));
        Button fileButton = new Button("📎");
        fileButton.setOnAction(e -> sendFileAction());

        HBox.setHgrow(inputField, Priority.ALWAYS);
        HBox bottomBar = new HBox(10, fileButton, inputField, sendButton);
        bottomBar.setPadding(new Insets(10));
        centerLayout.setBottom(bottomBar);
        return centerLayout;
    }

    private void initializeUIElements() {
        // Этот метод больше не нужен, так как мы не используем FXML
    }
    
    private void setupChatEventHandlers() {
        // Этот метод больше не нужен, так как мы не используем FXML
    }
    
    private void sortMessagesByTime() {
        if (allMessages != null) {
            allMessages.sort((m1, m2) -> {
                if (m1.getTimestamp() == null && m2.getTimestamp() == null) return 0;
                if (m1.getTimestamp() == null) return -1;
                if (m2.getTimestamp() == null) return 1;
                return m1.getTimestamp().compareTo(m2.getTimestamp());
            });
        }
    }

    private void sendMessage(TextField field) {
        String text = field.getText().trim();
        if (out == null || text.isEmpty()) return;

        if ("Общий чат".equals(activeChat)) {
            out.println("MSG " + text);
            } else {
                out.println("PM " + activeChat + " " + text);
        }
        field.clear();
    }

    private void sendFileAction() {
        if (activeChat.equals("Общий чат")) {
            showAlert(Alert.AlertType.WARNING, "Ошибка", "Отправка файлов возможна только в личных сообщениях.");
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите файл для отправки");
        File file = fileChooser.showOpenDialog(primaryStage);
        if (file != null) {
            offeredFiles.put(activeChat + "::" + file.getName(), file);
            String previewData = generatePreview(file);
            out.println(String.format("FILE_OFFER %s %s %d %s", activeChat, file.getName(), file.length(), previewData));
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
            ChatMessage fileOfferMsg = new ChatMessage(currentUsername, timestamp, true, activeChat, file.getName(), file.length(), previewData);
            if (allMessages != null) {
                allMessages.add(fileOfferMsg);
            }
        }
    }
    
    private void selectChat(String chatName) {
        if (isInVoiceChat && !chatName.equals(activeChat)) {
            Platform.runLater(() -> userListView.getSelectionModel().select(activeChat));
            showAlert(Alert.AlertType.WARNING, "Звонок активен", "Завершите текущий голосовой чат, чтобы сменить собеседника.");
            return;
        }
        
        activeChat = chatName;
        updateMessageFilter();
        Platform.runLater(() -> updateCallButtonsVisibility());
    }
    
    private void showFileChooser() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите файл для отправки");
        File file = fileChooser.showOpenDialog(primaryStage);
        if (file != null) {
            sendFile(file);
        }
    }
    
    private void sendFile(File file) {
        if (out != null && file.exists()) {
            String recipient = activeChat.equals("Общий чат") ? "ALL" : activeChat;
            out.println("FILE_OFFER " + recipient + " " + file.getName() + " " + file.length());
            offeredFiles.put(recipient + "::" + file.getName(), file);
        }
    }
    
    private void initiateVideoChat() {
        initiateVideoCall();
    }
    
    private void saveCurrentChat() {
        saveChatToFile();
    }

    private void updateMessageFilter() {
        if (filteredMessages != null) {
            filteredMessages.setPredicate(message -> {
                if (activeChat == null) return false;
                
                if (activeChat.equals("Общий чат")) {
                    return message.getConversationPartner() == null;
                } else {
                    return activeChat.equals(message.getConversationPartner()) || 
                           (message.getSender() != null && message.getSender().equals("Система"));
                }
            });
        }
    }

    private String generatePreview(File file) {
        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png") || fileName.endsWith(".gif")) {
            return "IMAGE";
        } else if (fileName.endsWith(".mp4") || fileName.endsWith(".avi") || fileName.endsWith(".mov")) {
            return "VIDEO";
        } else if (fileName.endsWith(".txt") || fileName.endsWith(".doc") || fileName.endsWith(".pdf")) {
            return "DOCUMENT";
        } else {
            return "FILE";
        }
    }

    private void handleServerMessage(String message) {
        // Пытаемся разобрать сообщение с пробелом, если не получилось - пробуем §§
        String[] parts;
        if (message.contains(" ")) {
            parts = message.split(" ", 4);
        } else {
            parts = message.split("§§");
        }
        
        if (parts.length < 1) return;
        
        String command = parts[0];
        
        switch (command) {
            case "SYS_MSG":
                if (parts.length >= 2) {
                    String sysMessage = parts[1];
                    Platform.runLater(() -> {
                        addSystemMessage(sysMessage, null);
                    });
                }
                break;
                
            case "USERS_LIST":
                if (parts.length >= 2) {
                    String[] users = parts[1].split(",");
                    Platform.runLater(() -> {
                        if (userList != null) {
                            userList.clear();
                            userList.add("Общий чат");
                            // Фильтруем текущего пользователя из списка
                            for (String user : users) {
                                if (!user.equals(currentUsername)) {
                                    userList.add(user);
                                }
                            }
                        }
                    });
                }
                break;
                
            case "PROFILE_DATA":
                if (parts.length >= 4) {
                    String username = parts[1];
                    String email = parts[2];
                    String avatarBase64 = parts[3];
                    
                    Platform.runLater(() -> {
                        if (username.equals(currentUsername)) {
                            profileEmail = email;
                            if (!avatarBase64.isEmpty()) {
                                try {
                                    byte[] avatarBytes = Base64.getDecoder().decode(avatarBase64);
                                    // Сохраняем аватар во временный файл
                                    File avatarFile = new File("temp_avatar.png");
                                    try (FileOutputStream fos = new FileOutputStream(avatarFile)) {
                                        fos.write(avatarBytes);
                                    }
                                    avatarPath = avatarFile.getAbsolutePath();
                                } catch (Exception e) {
                                    System.err.println("Ошибка загрузки аватара: " + e.getMessage());
                                }
                            }
                        }
                    });
                }
                break;
                
            default:
                // Если команда не распознана, пробуем разобрать как старый формат
                if (message.contains("§§")) {
                    parts = message.split("§§");
                    command = parts[0];
                    
                    switch (command) {
                        case "SYS_MSG":
                            if (parts.length >= 2) {
                                String sysMessage = parts[1];
                                Platform.runLater(() -> {
                                    addSystemMessage(sysMessage, null);
                                });
                            }
                            break;
                        
                        case "USERS_LIST":
                            if (parts.length >= 2) {
                                String[] users = parts[1].split(",");
                                Platform.runLater(() -> {
                                    if (userList != null) {
                                        userList.clear();
                                        userList.add("Общий чат");
                                        // Фильтруем текущего пользователя из списка
                                        for (String user : users) {
                                            if (!user.equals(currentUsername)) {
                                                userList.add(user);
                                            }
                                        }
                                    }
                                });
                            }
                            break;
                        
                        case "PROFILE_DATA":
                            if (parts.length >= 4) {
                                String username = parts[1];
                                String email = parts[2];
                                String avatarBase64 = parts[3];
                                
                                Platform.runLater(() -> {
                                    if (username.equals(currentUsername)) {
                                        profileEmail = email;
                                        if (!avatarBase64.isEmpty()) {
                                            try {
                                                byte[] avatarBytes = Base64.getDecoder().decode(avatarBase64);
                                                // Сохраняем аватар во временный файл
                                                File avatarFile = new File("temp_avatar.png");
                                                try (FileOutputStream fos = new FileOutputStream(avatarFile)) {
                                                    fos.write(avatarBytes);
                                                }
                                                avatarPath = avatarFile.getAbsolutePath();
                                            } catch (Exception e) {
                                                System.err.println("Ошибка загрузки аватара: " + e.getMessage());
                                            }
                                        }
                                    }
                                });
                            }
                            break;
                        
                        default:
                            System.out.println("Неизвестная команда: " + message);
                            break;
                    }
                } else {
                    System.out.println("Неизвестная команда: " + message);
                }
                break;
        }
    }

    private void uploadFileInChunks(File file, String recipientName) {
        final int CHUNK_SIZE = 8192;
        Platform.runLater(() -> addSystemMessage("Загрузка файла '" + file.getName() + "' на сервер...", recipientName));
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) > 0) {
                byte[] actualChunk = (bytesRead < CHUNK_SIZE) ? java.util.Arrays.copyOf(buffer, bytesRead) : buffer;
                String encodedChunk = Base64.getEncoder().encodeToString(actualChunk);
                out.println(String.format("FILE_CHUNK§§%s§§%s§§%s", recipientName, file.getName(), encodedChunk));
            }
            out.println(String.format("FILE_END§§%s§§%s", recipientName, file.getName()));
            Platform.runLater(() -> addSystemMessage("Файл '" + file.getName() + "' полностью отправлен.", recipientName));
        } catch (IOException e) {
            e.printStackTrace();
            Platform.runLater(() -> addSystemMessage("Ошибка при чтении файла для загрузки: " + e.getMessage(), recipientName));
        }
    }

    private void connectToServer() {
        try {
            socket = new Socket(SERVER_CHAT_ADDRESS, SERVER_CHAT_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // Инициализируем VoiceCallManager после подключения
            voiceCallManager = new VoiceCallManager(out);
            screenShareManager = new ScreenShareManager(out);
            
            // Запускаем поток для чтения сообщений от сервера
            Thread serverThread = new Thread(this::readServerMessages);
            serverThread.setDaemon(true);
            serverThread.start();
            
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Ошибка подключения", "Не удалось подключиться к серверу: " + e.getMessage());
        }
    }

    private void readServerMessages() {
        try {
            String fromServer;
            while ((fromServer = in.readLine()) != null) {
                handleServerMessage(fromServer);
            }
        } catch (IOException e) {
            Platform.runLater(() -> {
                if (primaryStage.getScene() != null && primaryStage.getScene().getRoot().getChildrenUnmodifiable().size() > 1) {
                    addSystemMessage("!!! ПОТЕРЯНО СОЕДИНЕНИЕ С СЕРВЕРОМ !!!", null);
                    showAlert(Alert.AlertType.ERROR, "Связь потеряна", "Потеряно соединение с сервером. Пожалуйста, перезапустите приложение.");
                }
            });
        }
    }



    private void showAlert(Alert.AlertType alertType, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(alertType);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.show();
        });
    }

    private class MessageCell extends ListCell<ChatMessage> {
        @Override
        protected void updateItem(ChatMessage message, boolean empty) {
            super.updateItem(message, empty);
            
            if (empty || message == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            
            VBox messageBox = new VBox(5);
            messageBox.setPadding(new Insets(5));
            
            // Заголовок сообщения
            HBox headerBox = new HBox(10);
            headerBox.setAlignment(Pos.CENTER_LEFT);
            
            Label senderLabel = new Label(message.getSender());
            senderLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " + currentTheme.getAccent() + ";");
            
            Label timeLabel = new Label(message.getTimestamp());
            timeLabel.setStyle("-fx-text-fill: " + currentTheme.getMuted() + "; -fx-font-size: 10;");
            
            headerBox.getChildren().addAll(senderLabel, timeLabel);
            
            // Содержимое сообщения
            VBox contentBox = new VBox(5);
            
            // Проверяем, является ли это системным сообщением
            if (message.isSystemMessage()) {
                messageBox.setStyle("-fx-background-color: " + currentTheme.getTertiary() + "; -fx-border-color: " + currentTheme.getAccent() + "; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10; -fx-alignment: center;");
                
                Label systemLabel = new Label(message.getContent());
                systemLabel.setStyle("-fx-text-fill: " + currentTheme.getText() + "; -fx-font-style: italic; -fx-font-weight: bold;");
                systemLabel.setWrapText(true);
                contentBox.getChildren().add(systemLabel);
            } else {
                // Обычное сообщение
                boolean isOwnMessage = message.getSender().equals(currentUsername);
                String backgroundColor = isOwnMessage ? currentTheme.getAccent() : currentTheme.getSecondary();
                String textColor = isOwnMessage ? "white" : currentTheme.getText();
                
                messageBox.setStyle("-fx-background-color: " + backgroundColor + "; -fx-border-color: " + currentTheme.getTertiary() + "; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10; -fx-max-width: 400;");
                
                // Обрабатываем содержимое сообщения
                String content = message.getContent();
                
                // Заменяем эмодзи
                content = replaceTextEmojis(content);
                
                // Проверяем на спойлеры
                if (content.contains("||") && content.indexOf("||") != content.lastIndexOf("||")) {
                    String[] spoilerParts = content.split("\\|\\|");
                    for (int i = 0; i < spoilerParts.length; i++) {
                        if (i % 2 == 0) {
                            // Обычный текст
                            if (!spoilerParts[i].isEmpty()) {
                                Label textLabel = new Label(spoilerParts[i]);
                                textLabel.setStyle("-fx-text-fill: " + textColor + ";");
                                textLabel.setWrapText(true);
                                contentBox.getChildren().add(textLabel);
                            }
                        } else {
                            // Спойлер
                            VBox spoilerBox = createSpoilerElement(spoilerParts[i]);
                            contentBox.getChildren().add(spoilerBox);
                        }
                    }
                } else {
                    // Обычный текст
                    Label contentLabel = new Label(content);
                    contentLabel.setStyle("-fx-text-fill: " + textColor + ";");
                    contentLabel.setWrapText(true);
                    contentBox.getChildren().add(contentLabel);
                }
                
                // Проверяем на файлы
                if (message.isFileMessage()) {
                    VBox fileBox = new VBox(5);
                    fileBox.setStyle("-fx-background-color: " + currentTheme.getTertiary() + "; -fx-border-color: " + currentTheme.getTertiary() + "; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 10; -fx-max-width: 300;");
                    
                    Label fileNameLabel = new Label(message.getFileName());
                    fileNameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " + currentTheme.getText() + ";");
                    
                    Label fileSizeLabel = new Label(formatFileSize(message.getFileSize()));
                    fileSizeLabel.setStyle("-fx-font-size: 10; -fx-text-fill: " + currentTheme.getMuted() + ";");
                    
                    Button downloadButton = new Button("Скачать");
                    downloadButton.setStyle("-fx-background-color: " + currentTheme.getAccent() + "; -fx-text-fill: white; -fx-background-radius: 4; -fx-border-radius: 4; -fx-padding: 5 10;");
                    downloadButton.setOnAction(e -> {
                        File file = offeredFiles.get(message.getSender() + "::" + message.getFileName());
                        if (file != null && file.exists()) {
                            FileChooser fileChooser = new FileChooser();
                            fileChooser.setTitle("Сохранить файл");
                            fileChooser.setInitialFileName(message.getFileName());
                            File saveFile = fileChooser.showSaveDialog(primaryStage);
                            if (saveFile != null) {
                                try {
                                    java.nio.file.Files.copy(file.toPath(), saveFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                    showAlert(Alert.AlertType.INFORMATION, "Файл сохранен", "Файл успешно сохранен: " + saveFile.getName());
                                } catch (IOException ex) {
                                    showAlert(Alert.AlertType.ERROR, "Ошибка", "Не удалось сохранить файл: " + ex.getMessage());
                                }
                            }
                        } else {
                            showAlert(Alert.AlertType.ERROR, "Ошибка", "Файл не найден");
                        }
                    });
                    
                    fileBox.getChildren().addAll(fileNameLabel, fileSizeLabel, downloadButton);
                    contentBox.getChildren().add(fileBox);
                }
            }
            
            messageBox.getChildren().addAll(headerBox, contentBox);
            setGraphic(messageBox);
            setText(null);
        }
        
        private String formatFileSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    // ===== НОВЫЕ МЕТОДЫ ДЛЯ РАСШИРЕННОГО ФУНКЦИОНАЛА =====
    
    private void showThemeSelector() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Выбор темы");
        alert.setHeaderText("Выберите тему интерфейса:");
        alert.setContentText("Какую тему вы хотите использовать?");
        
        ButtonType discordDark = new ButtonType("Discord Dark");
        ButtonType discordLight = new ButtonType("Discord Light");
        ButtonType darkBlue = new ButtonType("Dark Blue");
        ButtonType greenDark = new ButtonType("Green Dark");
        ButtonType cancel = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        
        alert.getButtonTypes().setAll(discordDark, discordLight, darkBlue, greenDark, cancel);
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            Theme selectedTheme = null;
            if (result.get() == discordDark) selectedTheme = Theme.DISCORD_DARK;
            else if (result.get() == discordLight) selectedTheme = Theme.DISCORD_LIGHT;
            else if (result.get() == darkBlue) selectedTheme = Theme.DARK_BLUE;
            else if (result.get() == greenDark) selectedTheme = Theme.GREEN_DARK;
            
            if (selectedTheme != null) {
                currentTheme = selectedTheme;
                applyTheme(selectedTheme);
                saveSessionSettings();
            }
        }
    }
    
    private void applyTheme(Theme theme) {
        this.currentTheme = theme;
        
        // Обновляем стили для всех элементов интерфейса
        Platform.runLater(() -> {
            if (primaryStage != null && primaryStage.getScene() != null) {
                // Применяем тему к сцене
                primaryStage.getScene().getRoot().setStyle(
                    "-fx-background-color: " + theme.getPrimary() + ";"
                );
                
                // Обновляем стили для всех текстовых полей
                updateTextFieldStyles(primaryStage.getScene().getRoot(), theme);
                
                // Обновляем стили панелей
                updateThemeStyles(theme);
                updateButtonStyles(theme);
                
                // Обновляем стили для всех открытых окон
                updateWindowStyles(theme);
            }
        });
        
        saveSessionSettings();
    }
    
    private void applyThemeToSettingsWindow(Theme theme) {
        Platform.runLater(() -> {
            // Применяем тему к окну настроек
            if (primaryStage != null && primaryStage.getScene() != null) {
                // Обновляем основной цвет фона
                primaryStage.getScene().getRoot().setStyle(
                    "-fx-background-color: " + theme.getPrimary() + ";"
                );
                
                // Обновляем стили для всех элементов в окне настроек
                updateTextFieldStyles(primaryStage.getScene().getRoot(), theme);
                updateButtonStylesRecursive(primaryStage.getScene().getRoot(), theme);
                
                // Обновляем стили для ComboBox
                if (primaryStage.getScene().getRoot() instanceof VBox) {
                    VBox root = (VBox) primaryStage.getScene().getRoot();
                    for (javafx.scene.Node child : root.getChildren()) {
                        if (child instanceof ComboBox) {
                            ComboBox<?> comboBox = (ComboBox<?>) child;
                            comboBox.setStyle("-fx-background-color: " + theme.getSecondary() + "; -fx-text-fill: " + theme.getText() + "; -fx-border-color: " + theme.getTertiary() + "; -fx-border-radius: 5; -fx-background-radius: 5;");
                        }
                    }
                }
            }
        });
    }
    
    private void updateWindowStyles(Theme theme) {
        // Обновляем стили для всех открытых окон звонков
        for (Stage callWindow : activeCallWindows.values()) {
            if (callWindow.getScene() != null && callWindow.getScene().getRoot() != null) {
                javafx.scene.Node root = callWindow.getScene().getRoot();
                if (root instanceof VBox) {
                    VBox vbox = (VBox) root;
                    vbox.setStyle("-fx-background-color: " + theme.getAccent() + "; -fx-text-fill: " + theme.getTertiary() + ";");
                    
                    // Обновляем стили всех элементов в окне звонка
                    for (javafx.scene.Node child : vbox.getChildren()) {
                        if (child instanceof Label) {
                            Label label = (Label) child;
                            if (label.getText().contains("Звоним") || label.getText().contains("Входящий") || label.getText().contains("Разговор")) {
                                label.setStyle("-fx-text-fill: " + theme.getTertiary() + "; -fx-font-size: 16;");
                            } else {
                                label.setStyle("-fx-text-fill: " + theme.getTertiary() + "; -fx-font-size: 24; -fx-font-weight: bold;");
                            }
                        } else if (child instanceof HBox) {
                            HBox hbox = (HBox) child;
                            for (javafx.scene.Node button : hbox.getChildren()) {
                                if (button instanceof Button) {
                                    button.setStyle(String.format(
                                        "-fx-background-color: %s; -fx-text-fill: %s; -fx-border-color: %s; -fx-border-radius: 5; -fx-background-radius: 5;",
                                        theme.getAccent(), theme.getTertiary(), theme.getSecondary()
                                    ));
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Обновляем стили для окна демонстрации экрана
        if (screenShareStage != null && screenShareStage.getScene() != null && screenShareStage.getScene().getRoot() != null) {
            javafx.scene.Node root = screenShareStage.getScene().getRoot();
            if (root instanceof VBox) {
                VBox vbox = (VBox) root;
                vbox.setStyle("-fx-background-color: " + theme.getAccent() + ";");
                
                for (javafx.scene.Node child : vbox.getChildren()) {
                    if (child instanceof Label) {
                        child.setStyle("-fx-text-fill: " + theme.getTertiary() + "; -fx-font-size: 14;");
                    } else if (child instanceof Button) {
                        Button button = (Button) child;
                        button.setStyle(String.format(
                            "-fx-background-color: %s; -fx-text-fill: %s; -fx-border-color: %s; -fx-border-radius: 5; -fx-background-radius: 5;",
                            theme.getSecondary(), theme.getTertiary(), theme.getAccent()
                        ));
                    }
                }
            }
        }
    }
    
    private void updateButtonStylesInContainer(Node node, Theme theme) {
        if (node instanceof Button) {
            Button button = (Button) node;
            String buttonText = button.getText();
            if (buttonText.equals("🔇") || buttonText.equals("🎤") || buttonText.equals("🔊")) {
                // Кнопки управления звонком
                button.setStyle("-fx-background-color: #4f545c; -fx-text-fill: white; -fx-font-size: 18; -fx-min-width: 60; -fx-min-height: 60; -fx-background-radius: 30;");
            } else if (buttonText.equals("📞")) {
                // Кнопка завершения звонка
                button.setStyle("-fx-background-color: #ed4245; -fx-text-fill: white; -fx-font-size: 18; -fx-min-width: 60; -fx-min-height: 60; -fx-background-radius: 30;");
            }
        } else if (node instanceof javafx.scene.Parent) {
            for (Node child : ((javafx.scene.Parent) node).getChildrenUnmodifiable()) {
                updateButtonStylesInContainer(child, theme);
            }
        }
    }
    
    private void updateTextFieldStyles(javafx.scene.Node node, Theme theme) {
        if (node instanceof TextField) {
            TextField textField = (TextField) node;
            textField.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: %s; -fx-border-color: %s; -fx-border-radius: 3; -fx-background-radius: 3;",
                theme.getSecondary(), theme.getText(), theme.getTertiary()
            ));
        } else if (node instanceof javafx.scene.Parent) {
            javafx.scene.Parent parent = (javafx.scene.Parent) node;
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                updateTextFieldStyles(child, theme);
            }
        }
    }
    
    private void updateThemeStyles(Theme theme) {
        if (primaryStage != null && primaryStage.getScene() != null) {
            javafx.scene.Node root = primaryStage.getScene().getRoot();
            
            // Применяем стили ко всем панелям
            String primaryStyle = "-fx-background-color: " + theme.getPrimary() + ";";
            String secondaryStyle = "-fx-background-color: " + theme.getSecondary() + ";";
            String textStyle = "-fx-text-fill: " + theme.getText() + ";";
            
            // Основная панель
            if (root instanceof BorderPane) {
                BorderPane borderPane = (BorderPane) root;
                borderPane.setStyle(primaryStyle);
                
                // Левая панель
                if (borderPane.getLeft() instanceof VBox) {
                    VBox leftPanel = (VBox) borderPane.getLeft();
                    leftPanel.setStyle(secondaryStyle);
                }
                
                // Центральная панель
                if (borderPane.getCenter() instanceof BorderPane) {
                    BorderPane centerPanel = (BorderPane) borderPane.getCenter();
                    centerPanel.setStyle(primaryStyle);
                }
            }
            
            // Обновляем стили для списков сообщений
            if (messageListView != null) {
                messageListView.setStyle(
                    "-fx-background-color: transparent;" +
                    "-fx-text-fill: " + theme.getText() + ";"
                );
            }
            
            // Обновляем стили для списка пользователей
            if (userListView != null) {
                userListView.setStyle(
                    "-fx-background-color: " + theme.getSecondary() + ";" +
                    "-fx-text-fill: " + theme.getText() + ";"
                );
            }
        }
    }
    
    private void updateButtonStyles(Theme theme) {
        if (primaryStage != null && primaryStage.getScene() != null) {
            updateButtonStylesRecursive(primaryStage.getScene().getRoot(), theme);
        }
    }
    
    private void updateButtonStylesRecursive(javafx.scene.Node node, Theme theme) {
        if (node instanceof Button) {
            Button button = (Button) node;
            String buttonStyle = String.format(
                "-fx-background-color: %s; -fx-text-fill: %s; -fx-border-color: %s; -fx-border-radius: 5; -fx-background-radius: 5;",
                theme.getAccent(), theme.getText(), theme.getTertiary()
            );
            button.setStyle(buttonStyle);
        } else if (node instanceof javafx.scene.Parent) {
            javafx.scene.Parent parent = (javafx.scene.Parent) node;
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                updateButtonStylesRecursive(child, theme);
            }
        }
    }
    
    public void showAddFriendDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Добавить друга");
        dialog.setHeaderText("Введите имя пользователя:");
        dialog.setContentText("Имя пользователя:");
        
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(username -> {
            if (!username.isEmpty() && !username.equals(currentUsername)) {
                out.println("ADD_FRIEND " + username);
                showAlert(Alert.AlertType.INFORMATION, "Запрос отправлен", 
                         "Запрос в друзья отправлен пользователю '" + username + "'!");
            } else if (username.equals(currentUsername)) {
                showAlert(Alert.AlertType.WARNING, "Ошибка", "Нельзя добавить самого себя в друзья.");
            }
        });
    }
    
    private void saveChatToFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить чат");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Текстовые файлы", "*.txt")
        );
        fileChooser.setInitialFileName("chat_" + activeChat + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")) + ".txt");
        
        File file = fileChooser.showSaveDialog(primaryStage);
        if (file != null) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                writer.println("Чат с " + activeChat);
                writer.println("Дата: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));
                writer.println("==================================================");
                
                for (ChatMessage message : allMessages) {
                    if (message.getConversationPartner() != null && 
                        (message.getConversationPartner().equals(activeChat) || 
                         (activeChat.equals("Общий чат") && message.getConversationPartner() == null))) {
                        writer.println("[" + message.getTimestamp() + "] " + message.getSender() + ": " + message.getContent());
                    }
                }
                
                showAlert(Alert.AlertType.INFORMATION, "Сохранено", "Чат успешно сохранен в файл: " + file.getName());
            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "Ошибка", "Не удалось сохранить чат: " + e.getMessage());
            }
        }
    }
    
    // Метод createScreenShareWindow удален - теперь используется интегрированная панель
    
    private void loadSessionSettings() {
        Properties sessionSettings = new Properties();
        File sessionFile = new File("session_settings.properties");
        
        if (sessionFile.exists()) {
            try (FileInputStream fis = new FileInputStream(sessionFile)) {
                sessionSettings.load(fis);
                
                // Загружаем размер окна
                String windowWidth = sessionSettings.getProperty("windowWidth", "900");
                String windowHeight = sessionSettings.getProperty("windowHeight", "600");
                
                if (primaryStage != null) {
                    primaryStage.setWidth(Double.parseDouble(windowWidth));
                    primaryStage.setHeight(Double.parseDouble(windowHeight));
                }
                
                // Загружаем тему
                String themeName = sessionSettings.getProperty("currentTheme", "DISCORD_DARK");
                try {
                    currentTheme = Theme.valueOf(themeName);
                } catch (IllegalArgumentException e) {
                    currentTheme = Theme.DISCORD_DARK;
                }
                
                // Загружаем данные профиля
                displayName = sessionSettings.getProperty("displayName", "");
                profileEmail = sessionSettings.getProperty("profileEmail", "");
                avatarPath = sessionSettings.getProperty("avatarPath", "");
                
            } catch (IOException e) {
                System.err.println("Ошибка загрузки настроек сессии: " + e.getMessage());
            }
        }
    }
    
    private void saveSessionSettings() {
        Properties sessionSettings = new Properties();
        
        // Сохраняем размер окна
        if (primaryStage != null) {
            sessionSettings.setProperty("windowWidth", String.valueOf(primaryStage.getWidth()));
            sessionSettings.setProperty("windowHeight", String.valueOf(primaryStage.getHeight()));
        }
        
        // Сохраняем тему
        sessionSettings.setProperty("currentTheme", currentTheme.name());
        
        // Сохраняем данные профиля
        sessionSettings.setProperty("displayName", displayName);
        sessionSettings.setProperty("profileEmail", profileEmail);
        sessionSettings.setProperty("avatarPath", avatarPath);
        
        try (FileOutputStream fos = new FileOutputStream("session_settings.properties")) {
            sessionSettings.store(fos, "Session Settings");
        } catch (IOException e) {
            System.err.println("Ошибка сохранения настроек сессии: " + e.getMessage());
        }
    }
    
    // ===== МЕТОДЫ ДЛЯ РАБОТЫ С ГРУППАМИ И СЕРВЕРАМИ =====
    
    public void showCreateGroupDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Создать группу");
        dialog.setHeaderText("Введите название группы:");
        dialog.setContentText("Название:");
        
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(groupName -> {
            if (!groupName.isEmpty()) {
                out.println("CREATE_GROUP " + groupName);
                showAlert(Alert.AlertType.INFORMATION, "Группа создана", 
                         "Группа '" + groupName + "' успешно создана!");
            }
        });
    }
    
    public void showCreateServerDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Создать сервер");
        dialog.setHeaderText("Введите название сервера:");
        dialog.setContentText("Название:");
        
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(serverName -> {
            if (!serverName.isEmpty()) {
                out.println("CREATE_SERVER " + serverName);
                showAlert(Alert.AlertType.INFORMATION, "Сервер создан", 
                         "Сервер '" + serverName + "' успешно создан!");
            }
        });
    }
    
    private void showSettingsDialog() {
        Stage settingsStage = new Stage();
        settingsStage.setTitle("Настройки профиля");
        settingsStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        
        VBox root = new VBox(20);
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.TOP_CENTER);
        root.setStyle("-fx-background-color: " + currentTheme.getPrimary() + "; -fx-text-fill: " + currentTheme.getText() + ";");
        
        // Заголовок
        Label titleLabel = new Label("Настройки профиля");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        titleLabel.setStyle("-fx-text-fill: " + currentTheme.getText() + ";");
        
        // Аватар
        Label avatarLabel = new Label("Аватар профиля");
        avatarLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        avatarLabel.setStyle("-fx-text-fill: " + currentTheme.getText() + ";");
        
        Circle avatarPreview = new Circle(50, Color.LIGHTGRAY);
        avatarPreview.setStroke(Color.WHITE);
        avatarPreview.setStrokeWidth(3);
        if (!avatarPath.isEmpty()) {
            try {
                Image avatarImage = new Image(new File(avatarPath).toURI().toString());
                avatarPreview.setFill(new javafx.scene.paint.ImagePattern(avatarImage));
            } catch (Exception e) {
                // Оставляем стандартный аватар
            }
        }
        
        Button changeAvatarBtn = new Button("Изменить аватар");
        changeAvatarBtn.setStyle("-fx-background-color: " + currentTheme.getAccent() + "; -fx-text-fill: " + currentTheme.getText() + "; -fx-font-size: 14; -fx-padding: 8 16;");
        changeAvatarBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Выберите изображение для аватара");
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.jpeg", "*.gif")
            );
            File file = fileChooser.showOpenDialog(settingsStage);
            if (file != null) {
                avatarPath = file.getAbsolutePath();
                try {
                    Image avatarImage = new Image(file.toURI().toString());
                    avatarPreview.setFill(new javafx.scene.paint.ImagePattern(avatarImage));
                } catch (Exception ex) {
                    showAlert(Alert.AlertType.ERROR, "Ошибка", "Не удалось загрузить изображение");
                }
            }
        });
        
        // Отображаемое имя
        Label nameLabel = new Label("Отображаемое имя:");
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        nameLabel.setStyle("-fx-text-fill: " + currentTheme.getText() + ";");
        
        TextField nameField = new TextField(displayName.isEmpty() ? currentUsername : displayName);
        nameField.setStyle("-fx-background-color: " + currentTheme.getSecondary() + "; -fx-text-fill: " + currentTheme.getText() + "; -fx-border-color: " + currentTheme.getTertiary() + "; -fx-border-radius: 5; -fx-background-radius: 5; -fx-padding: 8;");
        
        // Email
        Label emailLabel = new Label("Email:");
        emailLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        emailLabel.setStyle("-fx-text-fill: " + currentTheme.getText() + ";");
        
        TextField emailField = new TextField(profileEmail);
        emailField.setStyle("-fx-background-color: " + currentTheme.getSecondary() + "; -fx-text-fill: " + currentTheme.getText() + "; -fx-border-color: " + currentTheme.getTertiary() + "; -fx-border-radius: 5; -fx-background-radius: 5; -fx-padding: 8;");
        
        // Тема
        Label themeLabel = new Label("Тема интерфейса:");
        themeLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        themeLabel.setStyle("-fx-text-fill: " + currentTheme.getText() + ";");
        
        ComboBox<Theme> themeComboBox = new ComboBox<>();
        themeComboBox.getItems().addAll(Theme.values());
        themeComboBox.setValue(currentTheme);
        themeComboBox.setStyle("-fx-background-color: " + currentTheme.getSecondary() + "; -fx-text-fill: " + currentTheme.getText() + "; -fx-border-color: " + currentTheme.getTertiary() + "; -fx-border-radius: 5; -fx-background-radius: 5;");
        
        // Применяем тему к ComboBox
        themeComboBox.setOnAction(e -> {
            Theme selectedTheme = themeComboBox.getValue();
            if (selectedTheme != null) {
                currentTheme = selectedTheme;
                // Применяем тему к окну настроек
                applyThemeToSettingsWindow(selectedTheme);
            }
        });
        themeComboBox.setConverter(new StringConverter<Theme>() {
            @Override
            public String toString(Theme theme) {
                if (theme == null) return "";
                switch (theme) {
                    case DISCORD_DARK: return "Discord Dark";
                    case DISCORD_LIGHT: return "Discord Light";
                    case DARK_BLUE: return "Dark Blue";
                    case GREEN_DARK: return "Green Dark";
                    default: return theme.name();
                }
            }
            
            @Override
            public Theme fromString(String string) {
                return null;
            }
        });
        
        // Дополнительные настройки
        Label advancedLabel = new Label("Дополнительные настройки");
        advancedLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        advancedLabel.setStyle("-fx-text-fill: " + currentTheme.getText() + ";");
        
        CheckBox autoScrollCheckBox = new CheckBox("Автоматическая прокрутка чата");
        autoScrollCheckBox.setSelected(true);
        autoScrollCheckBox.setStyle("-fx-text-fill: " + currentTheme.getText() + ";");
        
        CheckBox soundNotificationsCheckBox = new CheckBox("Звуковые уведомления");
        soundNotificationsCheckBox.setSelected(true);
        soundNotificationsCheckBox.setStyle("-fx-text-fill: " + currentTheme.getText() + ";");
        
        // Кнопки
        Button saveBtn = new Button("Сохранить настройки");
        saveBtn.setStyle("-fx-background-color: #43b581; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 10 20; -fx-background-radius: 5;");
        
        Button cancelBtn = new Button("Отмена");
        cancelBtn.setStyle("-fx-background-color: #4f545c; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 10 20; -fx-background-radius: 5;");
        
        saveBtn.setOnAction(e -> {
            displayName = nameField.getText();
            profileEmail = emailField.getText();
            currentTheme = themeComboBox.getValue();
            
            // Сохраняем настройки локально
            saveSessionSettings();
            
            // Сохраняем профиль в базу данных
            if (out != null) {
                out.println("UPDATE_PROFILE " + displayName + " " + profileEmail + " " + avatarPath);
            }
            
            // Применяем тему
            applyTheme(currentTheme);
            
            showAlert(Alert.AlertType.INFORMATION, "Сохранено", "Настройки профиля обновлены!");
            settingsStage.close();
        });
        
        cancelBtn.setOnAction(e -> settingsStage.close());
        
        HBox buttons = new HBox(15, saveBtn, cancelBtn);
        buttons.setAlignment(Pos.CENTER);
        
        VBox avatarSection = new VBox(10, avatarLabel, avatarPreview, changeAvatarBtn);
        avatarSection.setAlignment(Pos.CENTER);
        
        VBox profileSection = new VBox(10, nameLabel, nameField, emailLabel, emailField);
        
        VBox themeSection = new VBox(10, themeLabel, themeComboBox);
        
        VBox advancedSection = new VBox(10, advancedLabel, autoScrollCheckBox, soundNotificationsCheckBox);
        
        root.getChildren().addAll(
            titleLabel,
            avatarSection,
            profileSection,
            themeSection,
            advancedSection,
            buttons
        );
        
        Scene scene = new Scene(root, 450, 650);
        settingsStage.setScene(scene);
        settingsStage.showAndWait();
    }

    private void addSystemMessage(String content, String conversationPartner) {
        Platform.runLater(() -> {
            if (allMessages != null) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
                ChatMessage systemMessage = new ChatMessage(content, "Система", timestamp, false, conversationPartner);
                allMessages.add(systemMessage);
                sortMessagesByTime();
                
                // Автоматическая прокрутка к последнему сообщению
                if (messageListView != null) {
                    messageListView.scrollTo(allMessages.size() - 1);
                }
            }
        });
    }

    private String replaceTextEmojis(String text) {
        return text
            .replace(":)", "😊")
            .replace(":(", "😢")
            .replace(":D", "😃")
            .replace(":P", "😛")
            .replace(";)", "😉")
            .replace("<3", "❤️")
            .replace(":heart:", "❤️")
            .replace(":smile:", "😊")
            .replace(":sad:", "😢")
            .replace(":laugh:", "😃")
            .replace(":wink:", "😉");
    }
    
    private VBox createSpoilerContent(String content) {
        return createSpoilerElement(content);
    }
    
    private VBox createSpoilerElement(String content) {
        VBox spoilerBox = new VBox(5);
        spoilerBox.setStyle("-fx-background-color: #2f3136; -fx-border-color: #4f545c; -fx-border-radius: 5; -fx-background-radius: 5; -fx-padding: 10;");
        
        Label spoilerLabel = new Label("СПОЙЛЕР (нажмите, чтобы показать)");
        spoilerLabel.setStyle("-fx-text-fill: #b9bbbe; -fx-font-weight: bold;");
        
        Label contentLabel = new Label(content);
        contentLabel.setStyle("-fx-text-fill: white;");
        contentLabel.setWrapText(true);
        contentLabel.setVisible(false);
        
        spoilerBox.setOnMouseClicked(e -> {
            contentLabel.setVisible(!contentLabel.isVisible());
            spoilerLabel.setText(contentLabel.isVisible() ? "СПОЙЛЕР (нажмите, чтобы скрыть)" : "СПОЙЛЕР (нажмите, чтобы показать)");
        });
        
        spoilerBox.getChildren().addAll(spoilerLabel, contentLabel);
        return spoilerBox;
    }

    private VBox createContentWithLinks(String content) {
        VBox contentBox = new VBox(5);
        
        // Простая проверка на ссылки
        if (content.contains("http://") || content.contains("https://") || content.contains("www.")) {
            String[] parts = content.split("\\s+");
            for (String part : parts) {
                if (part.startsWith("http://") || part.startsWith("https://") || part.startsWith("www.")) {
                    Hyperlink link = new Hyperlink(part);
                    link.setOnAction(e -> {
                        try {
                            Desktop.getDesktop().browse(new java.net.URI(part));
                        } catch (Exception ex) {
                            showAlert(Alert.AlertType.ERROR, "Ошибка", "Не удалось открыть ссылку: " + ex.getMessage());
                        }
                    });
                    contentBox.getChildren().add(link);
                } else {
                    Label textLabel = new Label(part);
                    textLabel.setStyle("-fx-text-fill: " + currentTheme.getText() + ";");
                    contentBox.getChildren().add(textLabel);
                }
            }
        } else {
            Label textLabel = new Label(content);
            textLabel.setStyle("-fx-text-fill: " + currentTheme.getText() + ";");
            textLabel.setWrapText(true);
            contentBox.getChildren().add(textLabel);
        }
        
        return contentBox;
    }
    
    private VBox createLinkPreview(String url) {
        VBox previewBox = new VBox(5);
        previewBox.setStyle("-fx-background-color: " + currentTheme.getSecondary() + "; -fx-border-color: " + currentTheme.getTertiary() + "; -fx-border-radius: 5; -fx-background-radius: 5; -fx-padding: 10;");
        
        Label urlLabel = new Label(url);
        urlLabel.setStyle("-fx-text-fill: " + currentTheme.getAccent() + "; -fx-font-weight: bold;");
        
        Label previewLabel = new Label("Предпросмотр ссылки");
        previewLabel.setStyle("-fx-text-fill: " + currentTheme.getText() + ";");
        
        previewBox.getChildren().addAll(urlLabel, previewLabel);
        return previewBox;
    }
    
    private void showImagePreview(String imagePath) {
        try {
            File imageFile = new File(imagePath);
            if (imageFile.exists()) {
                Image image = new Image(imageFile.toURI().toString());
                
                Stage previewStage = new Stage();
                previewStage.setTitle("Предпросмотр изображения");
                previewStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
                
                VBox root = new VBox(10);
                root.setAlignment(Pos.CENTER);
                root.setPadding(new Insets(20));
                root.setStyle("-fx-background-color: " + currentTheme.getPrimary() + ";");
                
                Label titleLabel = new Label("Предпросмотр изображения");
                titleLabel.setStyle("-fx-text-fill: " + currentTheme.getText() + "; -fx-font-size: 18; -fx-font-weight: bold;");
                
                ImageView imageView = new ImageView(image);
                imageView.setFitWidth(400);
                imageView.setFitHeight(300);
                imageView.setPreserveRatio(true);
                imageView.setStyle("-fx-border-color: " + currentTheme.getTertiary() + "; -fx-border-width: 2; -fx-border-radius: 5;");
                
                Button closeButton = new Button("Закрыть");
                closeButton.setStyle("-fx-background-color: " + currentTheme.getAccent() + "; -fx-text-fill: " + currentTheme.getText() + "; -fx-font-size: 14; -fx-padding: 10 20; -fx-background-radius: 5;");
                closeButton.setOnAction(e -> previewStage.close());
                
                root.getChildren().addAll(titleLabel, imageView, closeButton);
                
                Scene scene = new Scene(root, 450, 400);
                previewStage.setScene(scene);
                previewStage.show();
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Ошибка", "Не удалось загрузить изображение: " + e.getMessage());
        }
    }
    
    private void showVideoPreview(String videoPath) {
        try {
            File videoFile = new File(videoPath);
            if (videoFile.exists()) {
                // Для MP4 файлов показываем диалог с опциями
                if (videoPath.toLowerCase().endsWith(".mp4")) {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Предпросмотр MP4");
                    alert.setHeaderText("MP4 файл обнаружен");
                    alert.setContentText("Хотите открыть файл во внешнем проигрывателе или скачать?");
                    
                    ButtonType openButton = new ButtonType("Открыть");
                    ButtonType downloadButton = new ButtonType("Скачать");
                    ButtonType cancelButton = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
                    
                    alert.getButtonTypes().setAll(openButton, downloadButton, cancelButton);
                    
                    Optional<ButtonType> result = alert.showAndWait();
                    if (result.isPresent()) {
                        if (result.get() == openButton) {
                            Desktop.getDesktop().open(videoFile);
                        } else if (result.get() == downloadButton) {
                            // Копируем файл в папку загрузок
                            String downloadsPath = System.getProperty("user.home") + "/Downloads/";
                            File downloadsDir = new File(downloadsPath);
                            if (!downloadsDir.exists()) {
                                downloadsDir.mkdirs();
                            }
                            
                            File destFile = new File(downloadsPath + videoFile.getName());
                            java.nio.file.Files.copy(videoFile.toPath(), destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            
                            showAlert(Alert.AlertType.INFORMATION, "Скачано", "Файл сохранен в папку Загрузки: " + destFile.getAbsolutePath());
                        }
                    }
                } else {
                    // Для других форматов пытаемся показать в MediaView
                    Media media = new Media(videoFile.toURI().toString());
                    MediaPlayer mediaPlayer = new MediaPlayer(media);
                    MediaView mediaView = new MediaView(mediaPlayer);
                    
                    Stage previewStage = new Stage();
                    previewStage.setTitle("Предпросмотр видео");
                    previewStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
                    
                    VBox root = new VBox(10);
                    root.setAlignment(Pos.CENTER);
                    root.setPadding(new Insets(20));
                    root.setStyle("-fx-background-color: " + currentTheme.getPrimary() + ";");
                    
                    Label titleLabel = new Label("Предпросмотр видео");
                    titleLabel.setStyle("-fx-text-fill: " + currentTheme.getText() + "; -fx-font-size: 18; -fx-font-weight: bold;");
                    
                    mediaView.setFitWidth(400);
                    mediaView.setFitHeight(300);
                    mediaView.setStyle("-fx-border-color: " + currentTheme.getTertiary() + "; -fx-border-width: 2; -fx-border-radius: 5;");
                    
                    Button closeButton = new Button("Закрыть");
                    closeButton.setStyle("-fx-background-color: " + currentTheme.getAccent() + "; -fx-text-fill: " + currentTheme.getText() + "; -fx-font-size: 14; -fx-padding: 10 20; -fx-background-radius: 5;");
                    closeButton.setOnAction(e -> {
                        mediaPlayer.stop();
                        previewStage.close();
                    });
                    
                    root.getChildren().addAll(titleLabel, mediaView, closeButton);
                    
                    Scene scene = new Scene(root, 450, 400);
                    previewStage.setScene(scene);
                    previewStage.show();
                    
                    mediaPlayer.play();
                }
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Ошибка", "Не удалось загрузить видео: " + e.getMessage());
        }
    }

    private void sendMessage() {
        if (messageTextField != null) {
            String text = messageTextField.getText().trim();
            if (out != null && !text.isEmpty()) {
                            if (activeChat.equals("Общий чат")) {
                out.println("MSG " + text);
            } else {
                out.println("PM " + activeChat + " " + text);
            }
                messageTextField.clear();
            }
        }
    }
    
    // Добавляем недостающие методы
    private void initiateVideoCall() {
        if (activeChat.equals("Общий чат")) {
            showAlert(Alert.AlertType.WARNING, "Ошибка", "Видеозвонки возможны только в личных сообщениях.");
            return;
        }
        showAlert(Alert.AlertType.INFORMATION, "Видеозвонки", "Видеозвонки пока не поддерживаются");
    }
    
    private void initiateVoiceChat() {
        if (activeChat.equals("Общий чат")) {
            showAlert(Alert.AlertType.WARNING, "Ошибка", "Голосовые звонки возможны только в личных сообщениях.");
            return;
        }
        
        if (voiceCallManager != null) {
            // Отправляем команду на сервер для инициации звонка
            if (out != null) {
                out.println("CALL_INVITE " + activeChat);
                addSystemMessage("Отправлен запрос на голосовой звонок " + activeChat, activeChat);
            }
        } else {
            showAlert(Alert.AlertType.ERROR, "Ошибка", "VoiceCallManager не инициализирован");
        }
    }
    
    private void stopVoiceChat() {
        if (voiceCallManager != null) {
            voiceCallManager.stopStreaming();
            isInVoiceChat = false;
            updateVoiceChatUI(false);
            addSystemMessage("Голосовой звонок завершен с " + activeChat, activeChat);
        }
    }
    
    private void updateVoiceChatUI(boolean inCall) {
        Platform.runLater(() -> {
            if (voiceCallButton != null) {
                voiceCallButton.setVisible(!inCall);
            }
            if (hangUpButton != null) {
                hangUpButton.setVisible(inCall);
            }
            if (microphoneComboBox != null) {
                microphoneComboBox.setVisible(inCall);
            }
            
            // Обновляем общую видимость кнопок
            updateCallButtonsVisibility();
        });
    }
    
    private void updateCallButtonsVisibility() {
        Platform.runLater(() -> {
            if (activeChat == null) return;
            
            boolean isPrivateChat = !activeChat.equals("Общий чат");
            if (voiceCallButton != null) {
                voiceCallButton.setVisible(isPrivateChat && !isInVoiceChat);
            }
            if (hangUpButton != null) {
                hangUpButton.setVisible(isPrivateChat && isInVoiceChat);
            }
            if (microphoneComboBox != null) {
                microphoneComboBox.setVisible(isPrivateChat && isInVoiceChat);
            }
        });
    }
    
    private void showCallPanel(String peer) {
        currentCallPeer = peer;
        Platform.runLater(() -> {
            if (integratedCallPanel != null) {
                integratedCallPanel.setVisible(true);
                if (messageListView != null) {
                    messageListView.setVisible(false);
                }
            }
        });
    }
    
    private void showChat() {
        Platform.runLater(() -> {
            if (integratedCallPanel != null) {
                integratedCallPanel.setVisible(false);
            }
            if (integratedScreenSharePanel != null) {
                integratedScreenSharePanel.setVisible(false);
            }
            if (messageListView != null) {
                messageListView.setVisible(true);
            }
        });
    }
    
    private void toggleScreenSharing() {
        if (screenShareManager != null) {
            if (!isScreenSharing) {
                screenShareManager.startSharing(activeChat);
                isScreenSharing = true;
                addSystemMessage("Демонстрация экрана начата", activeChat);
            } else {
                screenShareManager.stopSharing();
                isScreenSharing = false;
                addSystemMessage("Демонстрация экрана остановлена", activeChat);
            }
        }
    }
    
    private void showIncomingScreenShare(Image image) {
        if (screenShareStage == null) {
            screenShareStage = new Stage();
            screenShareStage.setTitle("Демонстрация экрана");
            
            VBox root = new VBox(10);
            root.setAlignment(Pos.CENTER);
            root.setPadding(new Insets(20));
            root.setStyle("-fx-background-color: " + currentTheme.getPrimary() + ";");
            
            Label titleLabel = new Label("Демонстрация экрана");
            titleLabel.setStyle("-fx-text-fill: " + currentTheme.getText() + "; -fx-font-size: 18; -fx-font-weight: bold;");
            
            screenShareView = new ImageView();
            screenShareView.setFitWidth(800);
            screenShareView.setFitHeight(600);
            screenShareView.setPreserveRatio(true);
            
            Button closeButton = new Button("Закрыть");
            closeButton.setStyle("-fx-background-color: " + currentTheme.getAccent() + "; -fx-text-fill: " + currentTheme.getText() + "; -fx-font-size: 14; -fx-padding: 10 20; -fx-background-radius: 5;");
            closeButton.setOnAction(e -> screenShareStage.close());
            
            root.getChildren().addAll(titleLabel, screenShareView, closeButton);
            
            Scene scene = new Scene(root, 850, 700);
            screenShareStage.setScene(scene);
        }
        
        if (screenShareView != null) {
            screenShareView.setImage(image);
        }
        if (screenShareStage != null && !screenShareStage.isShowing()) {
            screenShareStage.show();
        }
    }
    
    private void createIntegratedCallPanel() {
        integratedCallPanel = new BorderPane();
        integratedCallPanel.setStyle("-fx-background-color: " + currentTheme.getPrimary() + ";");
        integratedCallPanel.setVisible(false);
        
        VBox callContent = new VBox(20);
        callContent.setAlignment(Pos.CENTER);
        callContent.setPadding(new Insets(50));
        
        Label callLabel = new Label("Голосовой звонок");
        callLabel.setStyle("-fx-text-fill: " + currentTheme.getText() + "; -fx-font-size: 24; -fx-font-weight: bold;");
        
        callPreviewImageView = new ImageView();
        callPreviewImageView.setFitWidth(200);
        callPreviewImageView.setFitHeight(150);
        callPreviewImageView.setStyle("-fx-border-color: " + currentTheme.getTertiary() + "; -fx-border-width: 2; -fx-border-radius: 5;");
        
        peerPreviewImageView = new ImageView();
        peerPreviewImageView.setFitWidth(200);
        peerPreviewImageView.setFitHeight(150);
        peerPreviewImageView.setStyle("-fx-border-color: " + currentTheme.getTertiary() + "; -fx-border-width: 2; -fx-border-radius: 5;");
        
        HBox previewBox = new HBox(20, callPreviewImageView, peerPreviewImageView);
        previewBox.setAlignment(Pos.CENTER);
        
        Button endCallButton = new Button("Завершить звонок");
        endCallButton.setStyle("-fx-background-color: #ed4245; -fx-text-fill: white; -fx-font-size: 16; -fx-padding: 10 20; -fx-background-radius: 5;");
        endCallButton.setOnAction(e -> stopVoiceChat());
        
        callContent.getChildren().addAll(callLabel, previewBox, endCallButton);
        if (integratedCallPanel != null) {
            integratedCallPanel.setCenter(callContent);
        }
    }
    
    private void createIntegratedScreenSharePanel() {
        integratedScreenSharePanel = new BorderPane();
        integratedScreenSharePanel.setStyle("-fx-background-color: " + currentTheme.getPrimary() + ";");
        integratedScreenSharePanel.setVisible(false);
        
        VBox screenContent = new VBox(20);
        screenContent.setAlignment(Pos.CENTER);
        screenContent.setPadding(new Insets(50));
        
        Label screenLabel = new Label("Демонстрация экрана");
        screenLabel.setStyle("-fx-text-fill: " + currentTheme.getText() + "; -fx-font-size: 24; -fx-font-weight: bold;");
        
        Button stopScreenShareButton = new Button("Остановить демонстрацию");
        stopScreenShareButton.setStyle("-fx-background-color: #ed4245; -fx-text-fill: white; -fx-font-size: 16; -fx-padding: 10 20; -fx-background-radius: 5;");
        stopScreenShareButton.setOnAction(e -> toggleScreenSharing());
        
        screenContent.getChildren().addAll(screenLabel, stopScreenShareButton);
        if (integratedScreenSharePanel != null) {
            integratedScreenSharePanel.setCenter(screenContent);
        }
    }
}