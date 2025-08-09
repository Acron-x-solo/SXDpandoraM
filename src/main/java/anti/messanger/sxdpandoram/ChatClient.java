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
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class ChatClient extends Application {

    private enum MediaType { IMAGE, VIDEO, OTHER }

    private PrintWriter out;
    private BufferedReader in;
    private Stage primaryStage;
    private String currentUsername;
    private String activeChat = "Общий чат";

    private final ObservableList<ChatMessage> allMessages = FXCollections.observableArrayList();
    private final FilteredList<ChatMessage> filteredMessages = new FilteredList<>(allMessages);
    private final ObservableList<String> userList = FXCollections.observableArrayList();
    private Label feedbackLabel;

    private final Map<String, File> offeredFiles = new HashMap<>();

    private static final String SERVER_CHAT_ADDRESS = "into-eco.gl.at.ply.gg";
    private static final int SERVER_CHAT_PORT = 59462;

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        primaryStage.setTitle("Мессенджер");
        primaryStage.setScene(createLoginScene());
        primaryStage.show();
        new Thread(this::connectToServer).start();
    }

    @Override
    public void stop() throws Exception {
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

    /**
     * Улучшенный просмотрщик изображений с индикатором загрузки и умной загрузкой.
     */
    private void showImagePreview(ChatMessage message) {
        Stage previewStage = new Stage();
        previewStage.setTitle("Просмотр: " + message.getFileName());

        // Запрашиваем у JavaFX уменьшенную версию, если это возможно, что ускоряет загрузку
        Image image = new Image(message.getDownloadUrl(), 1920, 1080, true, true, true);

        ImageView imageView = new ImageView(image);
        imageView.setPreserveRatio(true);

        // Индикатор загрузки ("крутилка")
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.progressProperty().bind(image.progressProperty());
        // Скрываем индикатор, когда загрузка завершена (или если была ошибка)
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

        // StackPane позволяет наложить индикатор поверх картинки
        StackPane root = new StackPane(scrollPane, progressIndicator);
        root.setStyle("-fx-background-color: #2e2e2e;");
        Scene scene = new Scene(root, 1024, 768);
        previewStage.setScene(scene);
        previewStage.show();
    }

    /**
     * Улучшенный просмотрщик видео с запасным вариантом "Открыть в плеере".
     */
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
            // --- НОВАЯ КНОПКА-ЗАПАСНОЙ ВАРИАНТ ---
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

            mediaPlayer.setOnError(() -> {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Ошибка воспроизведения",
                        "Не удалось воспроизвести видео. Скорее всего, формат или кодек не поддерживается.\n" +
                                "Пожалуйста, используйте кнопку 'Открыть в плеере' или скачайте файл."));
                playButton.setDisable(true);
                timeSlider.setDisable(true);
            });

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

    // ... Все остальные методы (createLoginScene, sendMessage, и т.д.) остаются без изменений ...
    // Ниже идет полный код без сокращений для вашего удобства.

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
        TextField inputField = new TextField();
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
        Button fileButton = new Button("📎");
        fileButton.setOnAction(e -> sendFileAction());
        HBox.setHgrow(inputField, Priority.ALWAYS);
        HBox bottomBar = new HBox(10, fileButton, inputField, sendButton);
        bottomBar.setPadding(new Insets(10));
        centerLayout.setBottom(bottomBar);
        return centerLayout;
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

    private class MessageCell extends ListCell<ChatMessage> {
        private final Map<String, Image> iconCache = new HashMap<>();

        public MessageCell() {
            try {
                iconCache.put("file", new Image(getClass().getResourceAsStream("icons/file_icon.png")));
                iconCache.put("archive", new Image(getClass().getResourceAsStream("icons/archive_icon.png")));
                iconCache.put("doc", new Image(getClass().getResourceAsStream("icons/doc_icon.png")));
                iconCache.put("image", new Image(getClass().getResourceAsStream("icons/image_icon.png")));
                iconCache.put("video", new Image(getClass().getResourceAsStream("icons/video_icon.png")));
            } catch (Exception e) {
                System.err.println("КРИТИЧЕСКАЯ ОШИБКА: Не удалось загрузить иконки! Убедитесь, что они лежат в папке ресурсов.");
            }
        }

        @Override
        protected void updateItem(ChatMessage message, boolean empty) {
            super.updateItem(message, empty);
            if (empty || message == null) {
                setGraphic(null);
                return;
            }
            if (message.isFileOffer()) {
                setGraphic(createFileOfferBubble(message));
            } else {
                setGraphic(createSimpleMessageBubble(message));
            }
        }

        private Node createSimpleMessageBubble(ChatMessage message) {
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
            if (message.getTimestamp() != null && !message.getTimestamp().isEmpty()) {
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
            return wrapper;
        }

        private Node createFileOfferBubble(ChatMessage message) {
            ImageView preview = new ImageView();
            String previewData = message.getFilePreviewData();

            if (previewData.startsWith("img:")) {
                try {
                    byte[] imageBytes = Base64.getDecoder().decode(previewData.substring(4));
                    preview.setImage(new Image(new ByteArrayInputStream(imageBytes)));
                } catch(Exception e) {
                    preview.setImage(iconCache.get("image"));
                }
            } else {
                String type = previewData.substring(5);
                preview.setImage(iconCache.getOrDefault(type, iconCache.get("file")));
            }
            preview.setFitHeight(80);
            preview.setFitWidth(80);
            preview.setPreserveRatio(true);

            MediaType mediaType = getMediaType(message.getFileName());
            if (message.getDownloadUrl() != null && mediaType != MediaType.OTHER) {
                preview.setStyle("-fx-cursor: hand;");
                preview.setOnMouseClicked(e -> {
                    if (mediaType == MediaType.IMAGE) {
                        showImagePreview(message);
                    } else if (mediaType == MediaType.VIDEO) {
                        showVideoPreview(message);
                    }
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

    private void addSystemMessage(String text, String partner) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
        allMessages.add(new ChatMessage(text, "Система", timestamp, false, partner));
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
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

                // --- ИСПРАВЛЕННЫЙ КОД ЗДЕСЬ ---
                case "PUB_MSG": // Формат от сервера: PUB_MSG§§timestamp§§sender§§content
                    if (parts.length == 4) {
                        String timestamp = parts[1];
                        String sender = parts[2];
                        String content = parts[3];
                        boolean isMe = sender.equals(currentUsername);
                        allMessages.add(new ChatMessage(content, sender, timestamp, isMe, null));
                    }
                    break;
                case "PRIV_MSG": // Формат от сервера: PRIV_MSG§§timestamp§§sender§§recipient§§content
                    if (parts.length == 5) {
                        String timestamp = parts[1];
                        String sender = parts[2];
                        String recipient = parts[3];
                        String content = parts[4];
                        boolean isMe = sender.equals(currentUsername);
                        String partner = isMe ? recipient : sender;
                        allMessages.add(new ChatMessage(content, sender, timestamp, isMe, partner));
                    }
                    break;
                // ------------------------------------

                case "SYS_MSG":
                    if (parts.length >= 2) addSystemMessage(parts[1], null);
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

                case "FILE_INCOMING":
                    if (parts.length == 5) {
                        String sender = parts[1];
                        String filename = parts[2];
                        long filesize = Long.parseLong(parts[3]);
                        String previewData = parts[4];
                        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
                        allMessages.add(new ChatMessage(sender, timestamp, false, sender, filename, filesize, previewData));
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
            }
        });
    }

    private void uploadFileInChunks(File file, String recipientName) {
        final int CHUNK_SIZE = 8192; // 8 KB

        Platform.runLater(() -> addSystemMessage("Загрузка файла '" + file.getName() + "' на сервер...", recipientName));

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) > 0) {
                byte[] actualChunk;
                if (bytesRead < CHUNK_SIZE) {
                    actualChunk = new byte[bytesRead];
                    System.arraycopy(buffer, 0, actualChunk, 0, bytesRead);
                } else {
                    actualChunk = buffer;
                }
                String encodedChunk = Base64.getEncoder().encodeToString(actualChunk);
                out.println(String.format("FILE_CHUNK§§%s§§%s§§%s", recipientName, file.getName(), encodedChunk));
            }
            out.println(String.format("FILE_END§§%s§§%s", recipientName, file.getName()));
            Platform.runLater(() -> addSystemMessage("Файл '" + file.getName() + "' полностью отправлен. Ожидание ссылки...", recipientName));
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
                    e.printStackTrace();
                }
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
                Platform.runLater(() -> addSystemMessage("!!! ПОТЕРЯНО СОЕДИНЕНИЕ С СЕРВЕРОМ !!!", null));
            }
        }).start();
    }
}