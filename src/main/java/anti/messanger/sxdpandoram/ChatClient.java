package anti.messanger.sxdpandoram;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

public class ChatClient extends Application {

    private PrintWriter out;
    private final TextArea messagesArea = new TextArea();
    private final TextField inputField = new TextField();
    private final Button sendButton = new Button("Отправить");
    // Генерируем простое случайное имя для пользователя
    private final String clientName = "Пользователь" + (int)(Math.random() * 1000);

    // --- Данные для подключения к вашему серверу ---
    private static final String SERVER_ADDRESS = "into-eco.gl.at.ply.gg";
    private static final int SERVER_PORT = 59462;
    // ------------------------------------------------

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        messagesArea.setEditable(false);
        messagesArea.setWrapText(true);

        // Блокируем ввод и отправку до успешного подключения
        inputField.setDisable(true);
        sendButton.setDisable(true);
        inputField.setPromptText("Подключение к серверу..."); // Подсказка для пользователя

        sendButton.setOnAction(e -> sendMessage());
        inputField.setOnAction(e -> sendMessage()); // Позволяет отправлять сообщения по нажатию Enter

        VBox root = new VBox(10, messagesArea, inputField, sendButton);
        root.setPadding(new Insets(10));
        Scene scene = new Scene(root, 400, 500);

        primaryStage.setTitle("JavaFX Мессенджер - " + clientName);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Корректное закрытие приложения и соединения
        primaryStage.setOnCloseRequest(e -> {
            if (out != null) {
                out.close();
            }
            Platform.exit();
            System.exit(0);
        });

        // Запускаем подключение в отдельном потоке, чтобы не блокировать GUI
        new Thread(this::connectToServer).start();
    }

    private void connectToServer() {
        try {
            // --- Вот измененная строка ---
            Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            // -----------------------------

            out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Если подключение успешно, активируем интерфейс в потоке JavaFX
            Platform.runLater(() -> {
                inputField.setDisable(false);
                sendButton.setDisable(false);
                inputField.setPromptText("Введите ваше сообщение...");
                messagesArea.appendText("✅ Успешно подключено к чату!\n");
            });

            // Отправляем имя пользователя на сервер
            out.println(clientName);

            // Цикл чтения сообщений от сервера
            String serverMessage;
            while ((serverMessage = in.readLine()) != null) {
                final String msg = serverMessage;
                // Обновляем TextArea в потоке JavaFX
                Platform.runLater(() -> messagesArea.appendText(msg + "\n\n")); // Добавил двойной перенос для читаемости
            }

        } catch (UnknownHostException e) {
            Platform.runLater(() -> messagesArea.appendText("❌ Ошибка: Не удалось найти сервер по адресу " + SERVER_ADDRESS + "\nПроверьте адрес или работу playit.gg\n"));
        } catch (IOException e) {
            Platform.runLater(() -> messagesArea.appendText("❌ Ошибка подключения. Сервер недоступен или не запущен.\nУбедитесь, что ChatServer и playit.gg работают.\n"));
        }
    }

    private void sendMessage() {
        String message = inputField.getText();
        // Проверяем, что сообщение не пустое и соединение установлено (out != null)
        if (out != null && message != null && !message.trim().isEmpty()) {
            out.println(message);
            inputField.clear();
        }
    }
}