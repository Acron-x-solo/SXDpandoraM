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

    private final Map<String, File> offeredFiles = new HashMap<>();
    private final Map<String, Stage> activeCallWindows = new HashMap<>();
    
    // Поля для голосовых звонков
    private VoiceCallManager voiceCallManager;
    private boolean isInVoiceChat = false;
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
    
    // Поля для сохранения входа
    private String savedUsername = "";
    private String savedPassword = "";

    private static final String SERVER_CHAT_ADDRESS = "into-eco.gl.at.ply.gg";
    private static final int SERVER_CHAT_PORT = 59462;

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        primaryStage.setTitle("Мессенджер");
        
        // Загружаем настройки сессии
        loadSessionSettings();
        
        // Создаем сцену входа
        primaryStage.setScene(createLoginScene());
        primaryStage.show();
        
        // Подключаемся к серверу
        new Thread(this::connectToServer).start();
    }

    @Override
    public void stop() throws Exception {
        if (isInVoiceChat) {
            stopVoiceChat();
        }
        
        // Сохраняем настройки сессии
        saveSessionSettings();
        
        if(out != null) out.close();
        if(in != null) in.close();
        super.stop();
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
        try {
            Media media = new Media(message.getDownloadUrl());
            MediaPlayer mediaPlayer = new MediaPlayer(media);
            MediaView mediaView = new MediaView(mediaPlayer);
            Button playButton = new Button("▶");
            Slider timeSlider = new Slider();
            Label timeLabel = new Label("00:00 / 00:00");
            Button openInSystemPlayerButton = new Button("Открыть в плеере");
            openInSystemPlayerButton.setOnAction(e -> getHostServices().showDocument(message.getDownloadUrl()));
            playButton.setOnAction(e -> {
                MediaPlayer.Status status = mediaPlayer.getStatus();
                if (status == MediaPlayer.Status.UNKNOWN || status == MediaPlayer.Status.HALTED) return;
                if (status == MediaPlayer.Status.PAUSED || status == MediaPlayer.Status.READY || status == MediaPlayer.Status.STOPPED) {
                    mediaPlayer.play();
                    playButton.setText("❚❚");
                } else {
                    mediaPlayer.pause();
                    playButton.setText("▶");
                }
            });
            mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                if (!timeSlider.isValueChanging()) timeSlider.setValue(newTime.toSeconds());
                timeLabel.setText(formatDuration(newTime) + " / " + formatDuration(mediaPlayer.getTotalDuration()));
            });
            mediaPlayer.setOnReady(() -> {
                timeSlider.setMax(mediaPlayer.getTotalDuration().toSeconds());
                timeLabel.setText("00:00 / " + formatDuration(mediaPlayer.getTotalDuration()));
                mediaPlayer.play();
                playButton.setText("❚❚");
            });
            timeSlider.valueProperty().addListener((obs, oldValue, newValue) -> {
                if (timeSlider.isPressed()) mediaPlayer.seek(Duration.seconds(newValue.doubleValue()));
            });
            mediaPlayer.setOnError(() -> Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Ошибка воспроизведения", "Не удалось воспроизвести видео. Пожалуйста, используйте кнопку 'Открыть в плеере' или скачайте файл.")));
            DoubleProperty width = mediaView.fitWidthProperty();
            DoubleProperty height = mediaView.fitHeightProperty();
            width.bind(Bindings.selectDouble(mediaView.sceneProperty(), "width"));
            height.bind(Bindings.selectDouble(mediaView.sceneProperty(), "height").subtract(40));
            HBox controlBar = new HBox(10, playButton, timeSlider, timeLabel, openInSystemPlayerButton);
            controlBar.setPadding(new Insets(10));
            controlBar.setAlignment(Pos.CENTER);
            HBox.setHgrow(timeSlider, Priority.ALWAYS);
            BorderPane root = new BorderPane();
            root.setCenter(mediaView);
            root.setBottom(controlBar);
            root.setStyle("-fx-background-color: black;");
            Scene scene = new Scene(root, 800, 600);
            previewStage.setScene(scene);
            previewStage.setOnCloseRequest(e -> mediaPlayer.stop());
            previewStage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Ошибка", "Не удалось запустить плеер. Пожалуйста, скачайте файл вручную.");
        }
    }

    private String formatDuration(Duration duration) {
        if (duration == null || duration.isUnknown() || duration.isIndefinite()) return "00:00";
        long totalSeconds = Math.round(duration.toSeconds());
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private Scene createLoginScene() {
        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(25, 25, 25, 25));
        
        Label title = new Label("Вход или Регистрация");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));
        grid.add(title, 0, 0, 2, 1);
        
        Label userName = new Label("Логин:");
        grid.add(userName, 0, 1);
        TextField userTextField = new TextField();
        userTextField.setText(savedUsername);
        grid.add(userTextField, 1, 1);
        
        Label pw = new Label("Пароль:");
        grid.add(pw, 0, 2);
        PasswordField pwBox = new PasswordField();
        pwBox.setText(savedPassword);
        grid.add(pwBox, 1, 2);
        
        CheckBox rememberMe = new CheckBox("Запомнить вход");
        rememberMe.setSelected(!savedUsername.isEmpty());
        grid.add(rememberMe, 1, 3);
        
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
                
                // Сохраняем данные входа если отмечено
                if (rememberMe.isSelected()) {
                    savedUsername = username;
                    savedPassword = password;
                    saveSessionSettings();
                } else {
                    savedUsername = "";
                    savedPassword = "";
                    saveSessionSettings();
                }
                
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
        
        return new Scene(grid, 400, 350);
    }

    private Scene createChatScene() {
        // Инициализируем VoiceCallManager после подключения к серверу
        if (voiceCallManager == null) {
            voiceCallManager = new VoiceCallManager(out);
        }
        
        BorderPane layout = new BorderPane();
        Label chatHeader = new Label("Общий чат");
        TextField inputField = new TextField();
        layout.setLeft(createLeftPanel(chatHeader, inputField));
        layout.setCenter(createCenterPanel(chatHeader, inputField));
        
        // Устанавливаем фильтр сообщений для отображения сообщений общего чата
        updateMessageFilter();
        
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
        
        Button settingsButton = new Button("⚙️ Настройки");
        settingsButton.setOnAction(e -> showSettingsDialog());
        
        Button createGroupButton = new Button("📁 Создать группу");
        createGroupButton.setOnAction(e -> showCreateGroupDialog());
        
        Button createServerButton = new Button("🏠 Создать сервер");
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
        if (voiceCallManager != null) {
            microphoneComboBox.setItems(FXCollections.observableArrayList(voiceCallManager.getInputDeviceNames()));
            if (!microphoneComboBox.getItems().isEmpty()) {
                microphoneComboBox.getSelectionModel().selectFirst();
            }
        }
        microphoneComboBox.setTooltip(new Tooltip("Выберите микрофон"));

        var isPrivateChat = this.userListView.getSelectionModel().selectedItemProperty().isNotNull()
                .and(this.userListView.getSelectionModel().selectedItemProperty().isNotEqualTo("Общий чат"));

        videoCallButton.visibleProperty().bind(isPrivateChat.and(Bindings.createBooleanBinding(() -> !isInVoiceChat, hangUpButton.visibleProperty())));
        voiceCallButton.visibleProperty().bind(isPrivateChat.and(Bindings.createBooleanBinding(() -> !isInVoiceChat, hangUpButton.visibleProperty())));
        microphoneComboBox.visibleProperty().bind(isPrivateChat.and(Bindings.createBooleanBinding(() -> !isInVoiceChat, hangUpButton.visibleProperty())));

        videoCallButton.managedProperty().bind(videoCallButton.visibleProperty());
        voiceCallButton.managedProperty().bind(voiceCallButton.visibleProperty());
        microphoneComboBox.managedProperty().bind(microphoneComboBox.visibleProperty());
        hangUpButton.managedProperty().bind(hangUpButton.visibleProperty());

        // Создаем расширенную панель с новыми функциями
        Button themeButton = new Button("🎨 Тема");
        themeButton.setOnAction(e -> showThemeSelector());
        
        Button addFriendButton = new Button("👥 Добавить друга");
        addFriendButton.setOnAction(e -> showAddFriendDialog());
        
        Button saveChatButton = new Button("💾 Сохранить чат");
        saveChatButton.setOnAction(e -> saveChatToFile());
        
        Button screenShareButton = new Button("🖥️ Демонстрация");
        screenShareButton.setOnAction(e -> createScreenShareWindow());
        
        HBox topBar = new HBox(10, chatHeader, videoCallButton, voiceCallButton, microphoneComboBox, hangUpButton, 
                               themeButton, addFriendButton, saveChatButton, screenShareButton);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(10));
        topBar.setStyle("-fx-background-color: #f0f0f0;");
        centerLayout.setTop(topBar);

        this.messageListView = new ListView<>(filteredMessages);
        messageListView.setCellFactory(param -> new MessageCell());
        messageListView.setStyle("-fx-background-color: transparent;");
        centerLayout.setCenter(messageListView);

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

    private void initiateVideoCall() {
        if (activeChat == null || "Общий чат".equals(activeChat) || isInVoiceChat) return;
        if (activeCallWindows.containsKey(activeChat)) {
            activeCallWindows.get(activeChat).toFront();
            return;
        }
        out.println("CALL_INITIATE§§" + activeChat);
    }

    private void initiateVoiceChat() {
        if (activeChat == null || "Общий чат".equals(activeChat) || isInVoiceChat) return;
        
        if (voiceCallManager == null) {
            showAlert(Alert.AlertType.WARNING, "Ошибка", "Голосовой менеджер не инициализирован.");
            return;
        }
        
        if (microphoneComboBox.getSelectionModel().getSelectedItem() == null) {
            showAlert(Alert.AlertType.WARNING, "Нет микрофона", "Пожалуйста, выберите микрофон для начала звонка.");
            return;
        }
        
        // Показываем окно звонка
        showCallWindow(activeChat, "outgoing");
        out.println("VOICE_INVITE§§" + activeChat);
        addSystemMessage("Исходящий голосовой вызов для " + activeChat + "...", activeChat);
    }

    private void stopVoiceChat() {
        if (!isInVoiceChat) return;
        out.println("VOICE_END§§" + activeChat);
        if (voiceCallManager != null) {
            voiceCallManager.stopStreaming();
        }
        updateVoiceChatUI(false);
        hideCallWindow();
    }

    private void updateVoiceChatUI(boolean isActive) {
        isInVoiceChat = isActive;
        hangUpButton.setVisible(isActive);
        voiceCallButton.setVisible(!isActive);
    }
    
    private void showCallWindow(String partner, String callType) {
        if (callStage != null) {
            callStage.toFront();
            return;
        }
        
        callStage = new Stage();
        callStage.setTitle("Звонок с " + partner);
        callStage.initModality(Modality.NONE);
        callStage.initStyle(StageStyle.UTILITY);
        
        VBox callLayout = new VBox(20);
        callLayout.setAlignment(Pos.CENTER);
        callLayout.setPadding(new Insets(30));
        callLayout.setStyle("-fx-background-color: #2f3136; -fx-text-fill: white;");
        
        // Аватар и имя
        Circle avatar = new Circle(50);
        avatar.setFill(Color.GRAY);
        avatar.setStroke(Color.WHITE);
        avatar.setStrokeWidth(3);
        
        Label nameLabel = new Label(partner);
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        nameLabel.setStyle("-fx-text-fill: white;");
        
        // Статус звонка
        Label statusLabel = new Label(callType.equals("outgoing") ? "Звоним..." : "Входящий звонок");
        statusLabel.setFont(Font.font("System", 16));
        statusLabel.setStyle("-fx-text-fill: #b9bbbe;");
        
        // Кнопки управления
        HBox controlsBox = new HBox(20);
        controlsBox.setAlignment(Pos.CENTER);
        
        Button muteButton = new Button("🔇");
        muteButton.setStyle("-fx-background-color: #4f545c; -fx-text-fill: white; -fx-font-size: 18; -fx-min-width: 60; -fx-min-height: 60; -fx-background-radius: 30;");
        muteButton.setOnAction(e -> {
            if (voiceCallManager != null) {
                voiceCallManager.setMuted(!voiceCallManager.isMuted());
                muteButton.setText(voiceCallManager.isMuted() ? "🔇" : "🎤");
            }
        });
        
        Button speakerButton = new Button("🔊");
        speakerButton.setStyle("-fx-background-color: #4f545c; -fx-text-fill: white; -fx-font-size: 18; -fx-min-width: 60; -fx-min-height: 60; -fx-background-radius: 30;");
        speakerButton.setOnAction(e -> {
            if (voiceCallManager != null) {
                voiceCallManager.setSpeakerOn(!voiceCallManager.isSpeakerOn());
                speakerButton.setText(voiceCallManager.isSpeakerOn() ? "🔊" : "🔇");
            }
        });
        
        Button hangUpButton = new Button("📞");
        hangUpButton.setStyle("-fx-background-color: #ed4245; -fx-text-fill: white; -fx-font-size: 18; -fx-min-width: 60; -fx-min-height: 60; -fx-background-radius: 30;");
        hangUpButton.setOnAction(e -> stopVoiceChat());
        
        if (callType.equals("incoming")) {
            Button acceptButton = new Button("📞");
            acceptButton.setStyle("-fx-background-color: #43b581; -fx-text-fill: white; -fx-font-size: 18; -fx-min-width: 60; -fx-min-height: 60; -fx-background-radius: 30;");
            acceptButton.setOnAction(e -> {
                out.println("VOICE_ACCEPT§§" + partner);
                statusLabel.setText("Разговор");
                controlsBox.getChildren().setAll(muteButton, speakerButton, hangUpButton);
            });
            
            Button declineButton = new Button("📞");
            declineButton.setStyle("-fx-background-color: #ed4245; -fx-text-fill: white; -fx-font-size: 18; -fx-min-width: 60; -fx-min-height: 60; -fx-background-radius: 30;");
            declineButton.setOnAction(e -> {
                out.println("VOICE_DECLINE§§" + partner);
                hideCallWindow();
            });
            
            controlsBox.getChildren().addAll(acceptButton, declineButton);
        } else {
            controlsBox.getChildren().addAll(muteButton, speakerButton, hangUpButton);
        }
        
        callLayout.getChildren().addAll(avatar, nameLabel, statusLabel, controlsBox);
        
        Scene callScene = new Scene(callLayout, 400, 500);
        callStage.setScene(callScene);
        callStage.setOnCloseRequest(e -> stopVoiceChat());
        callStage.show();
    }
    
    private void hideCallWindow() {
        if (callStage != null) {
            callStage.close();
            callStage = null;
        }
    }



    private void sendMessage(TextField field) {
        String text = field.getText().trim();
        if (out == null || text.isEmpty()) return;

        if ("Общий чат".equals(activeChat)) {
            out.println("MSG§§" + text);
            } else {
                out.println("PM§§" + activeChat + "§§" + text);
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
            out.println(String.format("FILE_OFFER§§%s§§%s§§%d§§%s", activeChat, file.getName(), file.length(), previewData));
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
            ChatMessage fileOfferMsg = new ChatMessage(currentUsername, timestamp, true, activeChat, file.getName(), file.length(), previewData);
            allMessages.add(fileOfferMsg);
        }
    }

    private void updateMessageFilter() {
        filteredMessages.setPredicate(message -> {
            // Системные сообщения всегда отображаются
            if (message.getSender().equals("Система")) {
                return true;
            }
            
            if (activeChat.equals("Общий чат")) {
                return message.getConversationPartner() == null;
            } else {
                return activeChat.equals(message.getConversationPartner());
            }
        });
    }

    private String generatePreview(File file) {
        String fileName = file.getName().toLowerCase();
        if (getMediaType(fileName) == MediaType.IMAGE) {
            try {
                Image image = new Image(file.toURI().toString(), 150, 0, true, true);
                BufferedImage bImage = SwingFXUtils.fromFXImage(image, null);
                ByteArrayOutputStream s = new ByteArrayOutputStream();
                ImageIO.write(bImage, "png", s);
                byte[] res = s.toByteArray();
                s.close();
                return "img:" + Base64.getEncoder().encodeToString(res);
            } catch (IOException e) {
                e.printStackTrace();
                return "type:image";
            }
        }
        if (getMediaType(fileName) == MediaType.VIDEO) return "type:video";
        if (fileName.endsWith(".zip") || fileName.endsWith(".rar") || fileName.endsWith(".7z")) return "type:archive";
        if (fileName.endsWith(".txt") || fileName.endsWith(".docx") || fileName.endsWith(".pdf")) return "type:doc";
        return "type:file";
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
                case "PUB_MSG":
                    if (parts.length == 4) {
                        Platform.runLater(() -> {
                            allMessages.add(new ChatMessage(parts[3], parts[2], parts[1], parts[2].equals(currentUsername), null));
                            if (messageListView != null) {
                                messageListView.scrollTo(allMessages.size() - 1);
                            }
                        });
                    }
                    break;
                case "PRIV_MSG":
                    if (parts.length == 5) {
                        Platform.runLater(() -> {
                            boolean isMe = parts[2].equals(currentUsername);
                            String partner = isMe ? parts[3] : parts[2];
                            allMessages.add(new ChatMessage(parts[4], parts[2], parts[1], isMe, partner));
                            if (messageListView != null) {
                                messageListView.scrollTo(allMessages.size() - 1);
                            }
                        });
                    }
                    break;
                case "SYS_MSG":
                    if (parts.length >= 2) {
                        addSystemMessage(parts[1], null);
                    }
                    break;
                case "USERS_LIST":
                    String selected = userListView.getSelectionModel().getSelectedItem();
                    userList.clear();
                    userList.add("Общий чат");
                    if (parts.length > 1 && !parts[1].isEmpty()) {
                        for (String user : parts[1].split(",")) {
                            if (!user.equals(currentUsername)) userList.add(user);
                        }
                    }
                    if (userList.contains(selected)) {
                        userListView.getSelectionModel().select(selected);
                    } else {
                        userListView.getSelectionModel().select("Общий чат");
                    }
                    break;
                case "FILE_INCOMING":
                    if (parts.length == 5) {
                        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
                        allMessages.add(new ChatMessage(parts[1], timestamp, false, parts[1], parts[2], Long.parseLong(parts[3]), parts[4]));
                    }
                    break;
                case "FILE_LINK":
                    if (parts.length == 3) {
                        String filename = parts[1];
                        String url = parts[2];
                        for (int i = 0; i < allMessages.size(); i++) {
                            ChatMessage m = allMessages.get(i);
                            if (m.isFileOffer() && m.getFileName().equals(filename) && !m.isSentByMe()) {
                                ChatMessage updatedMessage = new ChatMessage(m.getSender(), m.getTimestamp(), false, m.getConversationPartner(), m.getFileName(), m.getFileSize(), m.getFilePreviewData(), url);
                                allMessages.set(i, updatedMessage);
                                return;
                            }
                        }
                    }
                    break;
                case "UPLOAD_START":
                    if(parts.length == 3) {
                        String recipientName = parts[1];
                        String fileName = parts[2];
                        File fileToUpload = offeredFiles.get(recipientName + "::" + fileName);
                        if(fileToUpload != null) {
                            new Thread(() -> uploadFileInChunks(fileToUpload, recipientName)).start();
                        } else {
                            addSystemMessage("Внутренняя ошибка: не найден файл для отправки.", recipientName);
                        }
                    }
                    break;
                case "VOICE_INVITE":
                    if (parts.length == 2) {
                        String caller = parts[1];
                        showCallWindow(caller, "incoming");
                    }
                    break;
                case "CALL_INCOMING":
                    if (parts.length == 3) {
                        String caller = parts[1];
                        String roomName = parts[2];
                        Alert incomingCallAlert = new Alert(Alert.AlertType.CONFIRMATION);
                        incomingCallAlert.setTitle("Входящий видеозвонок");
                        incomingCallAlert.setHeaderText("Вам звонит " + caller);
                        incomingCallAlert.setContentText("Хотите принять вызов?");
                        ButtonType acceptButton = new ButtonType("Принять");
                        ButtonType declineButton = new ButtonType("Отклонить", ButtonBar.ButtonData.CANCEL_CLOSE);
                        incomingCallAlert.getButtonTypes().setAll(acceptButton, declineButton);
                        Optional<ButtonType> result = incomingCallAlert.showAndWait();
                        if (result.isPresent() && result.get() == acceptButton) {
                            out.println("CALL_ACCEPT§§" + caller + "§§" + roomName);
                            showCallWindow(caller, roomName);
                        } else {
                            out.println("CALL_DECLINE§§" + caller);
                        }
                    }
                    break;
                case "CALL_STARTED":
                    if (parts.length == 3) {
                        addSystemMessage("Пользователь " + parts[1] + " принял ваш видеозвонок.", parts[1]);
                        showCallWindow(parts[1], parts[2]);
                    }
                    break;
                case "CALL_DECLINED":
                    if (parts.length == 2) {
                        showAlert(Alert.AlertType.INFORMATION, "Видеозвонок отклонен", "Пользователь " + parts[1] + " отклонил ваш вызов.");
                    }
                    break;
                case "CALL_ENDED":
                    if (parts.length == 2) {
                        Stage callWindow = activeCallWindows.remove(parts[1]);
                        if (callWindow != null) {
                            callWindow.close();
                            showAlert(Alert.AlertType.INFORMATION, "Видеозвонок завершен", "Пользователь " + parts[1] + " завершил звонок.");
                        }
                    }
                    break;
                case "CALL_BUSY":
                    if (parts.length == 2) {
                        showAlert(Alert.AlertType.WARNING, "Абонент занят", "Пользователь " + parts[1] + " уже разговаривает.");
                    }
                    break;
                case "VOICE_INCOMING":
                    if (parts.length == 2 && !isInVoiceChat) {
                        String caller = parts[1];
                        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                        alert.setTitle("Входящий голосовой вызов");
                        alert.setHeaderText("Вам звонит " + caller);
                        alert.setContentText("Принять голосовой вызов?");
                        alert.showAndWait().ifPresent(response -> {
                            if (response == ButtonType.OK) {
                                out.println("VOICE_ACCEPT§§" + caller);
                            } else {
                                out.println("VOICE_DECLINE§§" + caller);
                            }
                        });
                    }
                    break;
                case "VOICE_START":
                    if (parts.length == 2 && voiceCallManager != null) {
                        voiceCallManager.startStreamingWithPeer(parts[1]);
                        updateVoiceChatUI(true);
                        addSystemMessage("Голосовой чат начат с " + parts[1], parts[1]);
                        // Обновляем статус в окне звонка
                        if (callStage != null) {
                            Platform.runLater(() -> {
                                VBox root = (VBox) callStage.getScene().getRoot();
                                Label statusLabel = (Label) root.getChildren().get(2);
                                statusLabel.setText("Разговор");
                            });
                        }
                    }
                    break;
                case "VOICE_END":
                    if (parts.length == 2) {
                        if (voiceCallManager != null) {
                            voiceCallManager.stopStreaming();
                        }
                        updateVoiceChatUI(false);
                        hideCallWindow();
                        addSystemMessage("Голосовой чат завершен.", activeChat);
                    }
                    break;
                case "VOICE_DECLINED":
                    if (parts.length == 2) {
                        showAlert(Alert.AlertType.INFORMATION, "Вызов отклонен", "Пользователь " + parts[1] + " отклонил голосовой вызов.");
                    }
                    break;
                case "AUDIO_CHUNK":
                    if (parts.length == 2 && voiceCallManager != null) {
                        voiceCallManager.handleIncomingVoiceFrame(parts[0], parts[1]);
                    }
                    break;
                case "SCREEN_FRAME":
                    if (parts.length == 2 && screenShareView != null) {
                        try {
                            byte[] imageData = Base64.getDecoder().decode(parts[1]);
                            Image image = new Image(new ByteArrayInputStream(imageData));
                            screenShareView.setImage(image);
                        } catch (Exception e) {
                            System.out.println("Ошибка при получении кадра экрана: " + e.getMessage());
                        }
                    }
                    break;
                case "SCREEN_STOP":
                    if (screenShareStage != null) {
                        screenShareStage.hide();
                    }
                    break;
                case "GROUP_MSG_SENT":
                    if (parts.length == 4) {
                        allMessages.add(new ChatMessage(parts[3], parts[2], parts[1], parts[2].equals(currentUsername), null));
                    }
                    break;
                case "SERVER_MSG_SENT":
                    if (parts.length == 4) {
                        allMessages.add(new ChatMessage(parts[3], parts[2], parts[1], parts[2].equals(currentUsername), null));
                    }
                    break;
                case "FRIEND_REQUEST_SENT":
                    if (parts.length == 2) {
                        showAlert(Alert.AlertType.INFORMATION, "Запрос отправлен", 
                                 "Запрос на добавление в друзья отправлен пользователю " + parts[1]);
                    }
                    break;
                case "FRIEND_REQUEST_RECEIVED":
                    if (parts.length == 2) {
                        String requester = parts[1];
                        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                        alert.setTitle("Запрос в друзья");
                        alert.setHeaderText("Пользователь " + requester + " хочет добавить вас в друзья");
                        alert.setContentText("Принять запрос?");
                        alert.showAndWait().ifPresent(response -> {
                            if (response == ButtonType.OK) {
                                out.println("FRIEND_ACCEPT§§" + requester);
                                userList.add(requester);
                            } else {
                                out.println("FRIEND_DECLINE§§" + requester);
                            }
                        });
                    }
                    break;
                case "FRIEND_ACCEPTED":
                    if (parts.length == 2) {
                        String friend = parts[1];
                        if (!userList.contains(friend)) {
                            userList.add(friend);
                        }
                        showAlert(Alert.AlertType.INFORMATION, "Друг добавлен", 
                                 "Пользователь " + friend + " принял ваш запрос в друзья!");
                    }
                    break;
                case "FRIEND_DECLINED":
                    if (parts.length == 2) {
                        showAlert(Alert.AlertType.INFORMATION, "Запрос отклонен", 
                                 "Пользователь " + parts[1] + " отклонил ваш запрос в друзья.");
                    }
                    break;
            }
        });
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
            Socket socket = new Socket(SERVER_CHAT_ADDRESS, SERVER_CHAT_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            startServerListener();
        } catch (IOException e) {
            Platform.runLater(() -> {
                if (feedbackLabel != null) {
                    feedbackLabel.setText("Ошибка: не удалось подключиться к серверу.");
                } else {
                    showAlert(Alert.AlertType.ERROR, "Ошибка подключения", "Не удалось подключиться к серверу.");
                }
                e.printStackTrace();
            });
        }
    }

    private void startServerListener() {
        new Thread(() -> {
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
        }).start();
    }

    private void addSystemMessage(String text, String partner) {
        Platform.runLater(() -> {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
            allMessages.add(new ChatMessage(text, "Система", timestamp, false, partner));
            
            // Автоматическая прокрутка к последнему сообщению
            if (messageListView != null) {
                messageListView.scrollTo(allMessages.size() - 1);
            }
        });
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }

    private class MessageCell extends ListCell<ChatMessage> {
        private final Map<String, Image> iconCache = new HashMap<>();
        public MessageCell() {
            // Создаем простые текстовые иконки вместо загрузки файлов
            try {
                // Попробуем загрузить иконки, но если не получится - создадим текстовые
                iconCache.put("file", new Image(getClass().getResourceAsStream("/anti/messanger/sxdpandoram/icons/file_icon.png")));
                iconCache.put("archive", new Image(getClass().getResourceAsStream("/anti/messanger/sxdpandoram/icons/archive_icon.png")));
                iconCache.put("doc", new Image(getClass().getResourceAsStream("/anti/messanger/sxdpandoram/icons/doc_icon.png")));
                iconCache.put("image", new Image(getClass().getResourceAsStream("/anti/messanger/sxdpandoram/icons/image_icon.png")));
                iconCache.put("video", new Image(getClass().getResourceAsStream("/anti/messanger/sxdpandoram/icons/video_icon.png")));
            } catch (Exception e) {
                System.out.println("Иконки не загружены, используются текстовые символы");
                // Создаем пустые изображения для предотвращения ошибок
                iconCache.put("file", null);
                iconCache.put("archive", null);
                iconCache.put("doc", null);
                iconCache.put("image", null);
                iconCache.put("video", null);
            }
        }
        @Override
        protected void updateItem(ChatMessage message, boolean empty) {
            super.updateItem(message, empty);
            if (empty || message == null) {
                setGraphic(null);
            } else {
                setGraphic(message.isFileOffer() ? createFileOfferBubble(message) : createSimpleMessageBubble(message));
            }
        }
        private Node createSimpleMessageBubble(ChatMessage message) {
            VBox bubble = new VBox(3);
            bubble.setMaxWidth(400);
            
            if (!message.getSender().equals("Система") && !message.isSentByMe()) {
                Label senderLabel = new Label(message.getSender());
                senderLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
                senderLabel.setTextFill(Color.CORNFLOWERBLUE);
                bubble.getChildren().add(senderLabel);
            }
            
            Label contentLabel = new Label(message.getContent());
            contentLabel.setWrapText(true);
            contentLabel.setStyle("-fx-text-fill: " + currentTheme.getText() + ";");
            bubble.getChildren().add(contentLabel);
            
            if (message.getTimestamp() != null && !message.getTimestamp().isEmpty()) {
                Label timeLabel = new Label(message.getTimestamp());
                timeLabel.setFont(Font.font(10));
                timeLabel.setTextFill(Color.GRAY);
                HBox timeContainer = new HBox(timeLabel);
                timeContainer.setAlignment(Pos.CENTER_RIGHT);
                bubble.getChildren().add(timeContainer);
            }
            
            HBox wrapper = new HBox();
            String bubbleStyle = "-fx-background-radius: 15; -fx-padding: 8; -fx-border-radius: 15; -fx-border-width: 1;";
            
            if (message.isSentByMe()) {
                bubble.setStyle(bubbleStyle + 
                    "-fx-background-color: " + currentTheme.getAccent() + ";" +
                    "-fx-border-color: " + currentTheme.getAccent() + ";");
                wrapper.setAlignment(Pos.CENTER_RIGHT);
            } else {
                bubble.setStyle(bubbleStyle + 
                    "-fx-background-color: " + currentTheme.getSecondary() + ";" +
                    "-fx-border-color: " + currentTheme.getTertiary() + ";");
                wrapper.setAlignment(Pos.CENTER_LEFT);
            }
            
            if (message.getSender().equals("Система")) {
                bubble.setStyle("-fx-background-color: transparent;");
                contentLabel.setStyle("-fx-text-fill: " + currentTheme.getMuted() + "; -fx-font-style: italic;");
                wrapper.setAlignment(Pos.CENTER);
            }
            
            wrapper.getChildren().add(bubble);
            wrapper.setPadding(new Insets(5, 10, 5, 10));
            return wrapper;
        }
        private Node createFileOfferBubble(ChatMessage message) {
            ImageView preview = new ImageView();
            String previewData = message.getFilePreviewData();
            if (previewData != null && previewData.startsWith("img:")) {
                try {
                    byte[] imageBytes = Base64.getDecoder().decode(previewData.substring(4));
                    preview.setImage(new Image(new ByteArrayInputStream(imageBytes)));
                } catch(Exception e) { 
                    Image icon = iconCache.get("image");
                    if (icon != null) {
                        preview.setImage(icon);
            } else {
                        // Создаем текстовую иконку если изображение не загружено
                        preview.setImage(null);
                        preview.setStyle("-fx-background-color: #ddd; -fx-alignment: center;");
                    }
                }
            } else {
                Image icon = iconCache.getOrDefault(previewData != null ? previewData.substring(5) : "file", iconCache.get("file"));
                if (icon != null) {
                    preview.setImage(icon);
                } else {
                    // Создаем текстовую иконку если изображение не загружено
                    preview.setImage(null);
                    preview.setStyle("-fx-background-color: #ddd; -fx-alignment: center;");
                }
            }
            preview.setFitHeight(80);
            preview.setFitWidth(80);
            preview.setPreserveRatio(true);
            MediaType mediaType = getMediaType(message.getFileName());
            if (message.getDownloadUrl() != null && mediaType != MediaType.OTHER) {
                preview.setStyle("-fx-cursor: hand;");
                preview.setOnMouseClicked(e -> {
                    if (mediaType == MediaType.IMAGE) showImagePreview(message);
                    else if (mediaType == MediaType.VIDEO) showVideoPreview(message);
                });
            }
            Label fileNameLabel = new Label(message.getFileName());
            fileNameLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
            Label fileSizeLabel = new Label(String.format("%.2f KB", message.getFileSize() / 1024.0));
            VBox fileInfoBox = new VBox(5, fileNameLabel, fileSizeLabel);
            HBox fileBox = new HBox(10, preview, fileInfoBox);
            fileBox.setAlignment(Pos.CENTER_LEFT);
            HBox actionPane = new HBox(10);
            actionPane.setAlignment(Pos.CENTER_LEFT);
            if (message.getDownloadUrl() != null) {
                Hyperlink downloadLink = new Hyperlink("Скачать файл");
                downloadLink.setOnAction(e -> getHostServices().showDocument(message.getDownloadUrl()));
                actionPane.getChildren().add(downloadLink);
            } else {
                if (message.isSentByMe()) {
                    actionPane.getChildren().add(new Label("Предложение отправлено"));
                } else {
                    Button acceptBtn = new Button("Принять");
                    Button declineBtn = new Button("Отклонить");
                    actionPane.getChildren().addAll(acceptBtn, declineBtn);
                    acceptBtn.setOnAction(e -> {
                        out.println("FILE_ACCEPT§§" + message.getSender() + "§§" + message.getFileName());
                        actionPane.getChildren().setAll(new Label("Ожидание загрузки..."));
                    });
                    declineBtn.setOnAction(e -> {
                        out.println("FILE_DECLINE§§" + message.getSender() + "§§" + message.getFileName());
                        actionPane.getChildren().setAll(new Label("Отклонено"));
                    });
                }
            }
            VBox bubbleContent = new VBox(10, fileBox, actionPane);
            VBox bubble = new VBox(bubbleContent);
            bubble.setPadding(new Insets(10));
            bubble.setStyle("-fx-background-radius: 15; -fx-border-color: #ccc; -fx-border-width: 1px; -fx-border-radius: 15; -fx-background-color: #f5f5f5;");
            HBox wrapper = new HBox(bubble);
            wrapper.setPadding(new Insets(5, 10, 5, 10));
            wrapper.setAlignment(message.isSentByMe() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            return wrapper;
        }
    }
    
    // ===== НОВЫЕ МЕТОДЫ ДЛЯ РАСШИРЕННОГО ФУНКЦИОНАЛА =====
    
    private void showThemeSelector() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Выбор темы");
        alert.setHeaderText("Выберите тему оформления:");
        alert.setContentText("Текущая тема: " + currentTheme.name());
        
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
                applyTheme(selectedTheme);
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
    
    private void updateWindowStyles(Theme theme) {
        // Обновляем стили окна звонка
        if (callStage != null && callStage.getScene() != null) {
            Node root = callStage.getScene().getRoot();
            if (root instanceof VBox) {
                VBox vbox = (VBox) root;
                vbox.setStyle("-fx-background-color: " + theme.getPrimary() + "; -fx-text-fill: " + theme.getText() + ";");
                
                // Обновляем стили всех элементов в окне звонка
                for (Node child : vbox.getChildren()) {
                    if (child instanceof Label) {
                        Label label = (Label) child;
                        if (label.getText().contains("Звоним") || label.getText().contains("Входящий") || label.getText().contains("Разговор")) {
                            label.setStyle("-fx-text-fill: " + theme.getText() + "; -fx-font-size: 16;");
                        } else {
                            label.setStyle("-fx-text-fill: " + theme.getText() + "; -fx-font-size: 24; -fx-font-weight: bold;");
                        }
                    } else if (child instanceof HBox) {
                        updateButtonStylesInContainer(child, theme);
                    }
                }
            }
        }
        
        // Обновляем стили окна демонстрации экрана
        if (screenShareStage != null && screenShareStage.getScene() != null) {
            Node root = screenShareStage.getScene().getRoot();
            if (root instanceof VBox) {
                VBox vbox = (VBox) root;
                vbox.setStyle("-fx-background-color: " + theme.getPrimary() + ";");
                
                for (Node child : vbox.getChildren()) {
                    if (child instanceof Label) {
                        child.setStyle("-fx-text-fill: " + theme.getText() + "; -fx-font-size: 14;");
                    } else if (child instanceof Button) {
                        Button button = (Button) child;
                        button.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white;");
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
    
    private void updateTextFieldStyles(Node node, Theme theme) {
        if (node instanceof TextField) {
            TextField textField = (TextField) node;
            textField.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: %s; -fx-border-color: %s; -fx-border-radius: 3; -fx-background-radius: 3;",
                theme.getSecondary(), theme.getText(), theme.getTertiary()
            ));
        } else if (node instanceof javafx.scene.Parent) {
            for (Node child : ((javafx.scene.Parent) node).getChildrenUnmodifiable()) {
                updateTextFieldStyles(child, theme);
            }
        }
    }
    
    private void updateThemeStyles(Theme theme) {
        if (primaryStage != null && primaryStage.getScene() != null) {
            Node root = primaryStage.getScene().getRoot();
            if (root instanceof BorderPane) {
                BorderPane borderPane = (BorderPane) root;
                
                // Применяем стили ко всем панелям
                String primaryStyle = "-fx-background-color: " + theme.getPrimary() + ";";
                String secondaryStyle = "-fx-background-color: " + theme.getSecondary() + ";";
                String textStyle = "-fx-text-fill: " + theme.getText() + ";";
                
                // Основная панель
                borderPane.setStyle(primaryStyle);
                
                // Левая панель
                if (borderPane.getLeft() != null) {
                    borderPane.getLeft().setStyle(secondaryStyle + textStyle);
                }
                
                // Центральная панель - НЕ применяем стиль, чтобы не перекрывать сообщения
                if (borderPane.getCenter() != null) {
                    // Убираем стиль с центральной панели
                    borderPane.getCenter().setStyle("");
                }
                
                // Верхняя панель
                if (borderPane.getTop() != null) {
                    borderPane.getTop().setStyle(secondaryStyle + textStyle);
                }
                
                // Нижняя панель
                if (borderPane.getBottom() != null) {
                    borderPane.getBottom().setStyle(secondaryStyle + textStyle);
                }
            }
        }
        
        // Стили для списка сообщений - прозрачный фон
        if (messageListView != null) {
            messageListView.setStyle(
                "-fx-background-color: transparent;" +
                "-fx-text-fill: " + theme.getText() + ";"
            );
        }
        
        // Стили для списка пользователей
        if (userListView != null) {
            userListView.setStyle(
                "-fx-background-color: " + theme.getSecondary() + ";" +
                "-fx-text-fill: " + theme.getText() + ";"
            );
        }
    }
    
    private void updateButtonStyles(Theme theme) {
        // Обновление стилей кнопок
        String buttonStyle = String.format(
            "-fx-background-color: %s; -fx-text-fill: %s; -fx-border-color: %s; -fx-border-radius: 5; -fx-background-radius: 5;",
            theme.getAccent(), theme.getText(), theme.getTertiary()
        );
        
        // Применяем стили к кнопкам в верхней панели
        if (primaryStage != null && primaryStage.getScene() != null) {
            Node root = primaryStage.getScene().getRoot();
            if (root instanceof BorderPane) {
                BorderPane borderPane = (BorderPane) root;
                if (borderPane.getTop() != null) {
                    applyButtonStyles(borderPane.getTop(), theme);
                }
                if (borderPane.getLeft() != null) {
                    applyButtonStyles(borderPane.getLeft(), theme);
                }
            }
        }
    }
    
    private void applyButtonStyles(Node node, Theme theme) {
        if (node instanceof Button) {
            Button button = (Button) node;
            button.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: %s; -fx-border-color: %s; -fx-border-radius: 5; -fx-background-radius: 5;",
                theme.getAccent(), theme.getText(), theme.getTertiary()
            ));
        } else if (node instanceof javafx.scene.Parent) {
            for (Node child : ((javafx.scene.Parent) node).getChildrenUnmodifiable()) {
                applyButtonStyles(child, theme);
            }
        }
    }
    
    private void showAddFriendDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Добавить друга");
        dialog.setHeaderText("Введите имя пользователя:");
        dialog.setContentText("Имя:");
        
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(username -> {
            if (!username.isEmpty() && !username.equals(currentUsername)) {
                if (userList.contains(username)) {
                    showAlert(Alert.AlertType.WARNING, "Уже в друзьях", 
                             "Пользователь " + username + " уже в вашем списке друзей.");
                } else {
                    out.println("ADD_FRIEND§§" + username);
                }
            } else if (username.equals(currentUsername)) {
                showAlert(Alert.AlertType.WARNING, "Ошибка", 
                         "Вы не можете добавить себя в друзья.");
            }
        });
    }
    
    private void saveChatToFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить чат");
        fileChooser.setInitialFileName("chat_" + activeChat + "_" + 
                                     LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")) + ".txt");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Текстовые файлы", "*.txt")
        );
        
        File file = fileChooser.showSaveDialog(primaryStage);
        if (file != null) {
            try (PrintWriter writer = new PrintWriter(file, "UTF-8")) {
                writer.println("Чат: " + activeChat);
                writer.println("Экспорт: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                writer.println("==================================================");
                writer.println();
                
                // Фильтруем сообщения для текущего чата и сортируем по времени
                allMessages.stream()
                    .filter(msg -> {
                        if (activeChat.equals("Общий чат")) {
                            return msg.getConversationPartner() == null;
                        } else {
                            return activeChat.equals(msg.getConversationPartner());
                        }
                    })
                    .sorted((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()))
                    .forEach(msg -> {
                        String time = msg.getTimestamp() != null ? msg.getTimestamp() : "";
                        writer.println("[" + time + "] " + msg.getSender() + ": " + msg.getContent());
                    });
                
                writer.println();
                writer.println("==================================================");
                writer.println("Всего сообщений: " + allMessages.stream()
                    .filter(msg -> {
                        if (activeChat.equals("Общий чат")) {
                            return msg.getConversationPartner() == null;
                        } else {
                            return activeChat.equals(msg.getConversationPartner());
                        }
                    }).count());
                
                showAlert(Alert.AlertType.INFORMATION, "Чат сохранен", 
                         "Чат успешно сохранен в файл: " + file.getName());
            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "Ошибка", 
                         "Не удалось сохранить чат: " + e.getMessage());
            }
        }
    }
    
    private void createScreenShareWindow() {
        if (screenShareStage != null) {
            screenShareStage.toFront();
            return;
        }
        
        screenShareStage = new Stage();
        screenShareStage.setTitle("Демонстрация экрана");
        
        screenShareView = new ImageView();
        screenShareView.setFitWidth(800);
        screenShareView.setFitHeight(600);
        screenShareView.setPreserveRatio(true);
        screenShareView.setStyle("-fx-background-color: #000; -fx-border-color: #333; -fx-border-width: 2;");
        
        Label statusLabel = new Label("Ожидание демонстрации экрана...");
        statusLabel.setStyle("-fx-text-fill: #fff; -fx-font-size: 14;");
        
        Button closeBtn = new Button("Закрыть");
        closeBtn.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white;");
        closeBtn.setOnAction(e -> {
            screenShareStage.close();
            screenShareStage = null;
            screenShareView = null;
        });
        
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #2f3136;");
        root.getChildren().addAll(
            statusLabel,
            screenShareView,
            closeBtn
        );
        
        Scene scene = new Scene(root, 900, 750);
        screenShareStage.setScene(scene);
        
        screenShareStage.setOnCloseRequest(e -> {
            screenShareStage = null;
            screenShareView = null;
        });
        
        screenShareStage.show();
    }
    
    private void loadSessionSettings() {
        try {
            if (settingsFile.exists()) {
                sessionSettings.load(new FileInputStream(settingsFile));
                
                // Загружаем тему
                String themeName = sessionSettings.getProperty("currentTheme", "DISCORD_DARK");
                try {
                    currentTheme = Theme.valueOf(themeName);
                } catch (IllegalArgumentException e) {
                    currentTheme = Theme.DISCORD_DARK;
                }
                
                // Загружаем размеры окна
                String widthStr = sessionSettings.getProperty("window_width", "900");
                String heightStr = sessionSettings.getProperty("window_height", "600");
                try {
                    int width = Integer.parseInt(widthStr);
                    int height = Integer.parseInt(heightStr);
                    if (primaryStage != null) {
                        primaryStage.setWidth(width);
                        primaryStage.setHeight(height);
                    }
                } catch (NumberFormatException e) {
                    // Игнорируем ошибки парсинга размеров
                }
                
                // Загружаем данные входа
                savedUsername = sessionSettings.getProperty("savedUsername", "");
                savedPassword = sessionSettings.getProperty("savedPassword", "");
                
                // Загружаем профиль
                displayName = sessionSettings.getProperty("displayName", "");
                profileEmail = sessionSettings.getProperty("profileEmail", "");
                avatarPath = sessionSettings.getProperty("avatarPath", "");
            }
        } catch (IOException e) {
            System.out.println("Не удалось загрузить настройки сессии: " + e.getMessage());
        }
    }
    
    private void saveSessionSettings() {
        try {
            sessionSettings.setProperty("currentTheme", currentTheme.name());
            if (primaryStage != null) {
                sessionSettings.setProperty("window_width", String.valueOf((int) primaryStage.getWidth()));
                sessionSettings.setProperty("window_height", String.valueOf((int) primaryStage.getHeight()));
            }
            
            // Сохраняем данные входа
            sessionSettings.setProperty("savedUsername", savedUsername);
            sessionSettings.setProperty("savedPassword", savedPassword);
            
            // Сохраняем профиль
            sessionSettings.setProperty("displayName", displayName);
            sessionSettings.setProperty("profileEmail", profileEmail);
            sessionSettings.setProperty("avatarPath", avatarPath);
            
            sessionSettings.store(new FileOutputStream(settingsFile), "Session Settings");
        } catch (IOException e) {
            System.out.println("Не удалось сохранить настройки сессии: " + e.getMessage());
        }
    }
    
    private void sortMessagesByTime() {
        allMessages.sort((a, b) -> {
            if (a.getTimestamp() == null) return 1;
            if (b.getTimestamp() == null) return -1;
            return a.getTimestamp().compareTo(b.getTimestamp());
        });
    }
    
    // ===== МЕТОДЫ ДЛЯ РАБОТЫ С ГРУППАМИ И СЕРВЕРАМИ =====
    
    private void showCreateGroupDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Создать группу");
        dialog.setHeaderText("Введите название группы:");
        dialog.setContentText("Название:");
        
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(groupName -> {
            if (!groupName.isEmpty()) {
                out.println("CREATE_GROUP§§" + groupName);
                showAlert(Alert.AlertType.INFORMATION, "Группа создана", 
                         "Группа '" + groupName + "' успешно создана!");
            }
        });
    }
    
    private void showCreateServerDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Создать сервер");
        dialog.setHeaderText("Введите название сервера:");
        dialog.setContentText("Название:");
        
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(serverName -> {
            if (!serverName.isEmpty()) {
                out.println("CREATE_SERVER§§" + serverName);
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
            
            // Сохраняем настройки
            saveSessionSettings();
            
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
}