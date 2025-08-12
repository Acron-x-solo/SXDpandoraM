package anti.messanger.sxdpandoram;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Стабильный просмотрщик экрана в одном окне с обновлением ImageView
 */
public class ScreenViewer {
    
    private final Stage stage;
    private final ImageView imageView;
    private final Label statusLabel;
    private final Label fpsLabel;
    private final AtomicBoolean isActive = new AtomicBoolean(false);
    private String currentSender;
    private long lastFrameTime = 0;
    private int frameCount = 0;
    private long startTime = 0;
    
    public ScreenViewer() {
        // Создаем основное окно
        stage = new Stage();
        stage.setTitle("Просмотр экрана");
        stage.initStyle(StageStyle.DECORATED);
        stage.setResizable(true);
        stage.setMinWidth(800);
        stage.setMinHeight(600);
        
        // Создаем ImageView для отображения кадров
        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(800);
        imageView.setFitHeight(600);
        imageView.setStyle("-fx-background-color: black;");
        
        // Статус и FPS
        statusLabel = new Label("Ожидание демонстрации экрана...");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");
        
        fpsLabel = new Label("FPS: 0");
        fpsLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
        
        // Кнопки управления
        Button closeButton = new Button("Закрыть");
        closeButton.setOnAction(e -> close());
        
        Button fullscreenButton = new Button("Полный экран");
        fullscreenButton.setOnAction(e -> toggleFullscreen());
        
        // Верхняя панель с информацией
        HBox topBar = new HBox(10);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(10));
        topBar.getChildren().addAll(statusLabel, fpsLabel);
        
        // Нижняя панель с кнопками
        HBox bottomBar = new HBox(10);
        bottomBar.setAlignment(Pos.CENTER_RIGHT);
        bottomBar.setPadding(new Insets(10));
        bottomBar.getChildren().addAll(fullscreenButton, closeButton);
        
        // Основной контейнер
        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setCenter(imageView);
        root.setBottom(bottomBar);
        
        Scene scene = new Scene(root, 800, 600);
        stage.setScene(scene);
        
        // Обработка изменения размера окна
        stage.widthProperty().addListener((obs, oldVal, newVal) -> {
            imageView.setFitWidth(newVal.doubleValue() - 40);
        });
        
        stage.heightProperty().addListener((obs, oldVal, newVal) -> {
            imageView.setFitHeight(newVal.doubleValue() - 100);
        });
    }
    
    /**
     * Показывает окно просмотра
     */
    public void show() {
        if (!stage.isShowing()) {
            stage.show();
        }
        stage.toFront();
    }
    
    /**
     * Закрывает окно просмотра
     */
    public void close() {
        isActive.set(false);
        if (stage.isShowing()) {
            stage.close();
        }
    }
    
    /**
     * Переключает полноэкранный режим
     */
    private void toggleFullscreen() {
        stage.setFullScreen(!stage.isFullScreen());
    }
    
    /**
     * Обрабатывает новый кадр экрана
     */
    public void handleFrame(String sender, String base64Data) {
        if (!isActive.get()) {
            isActive.set(true);
            currentSender = sender;
            startTime = System.currentTimeMillis();
            frameCount = 0;
        }
        
        Platform.runLater(() -> {
            try {
                // Декодируем Base64 и создаем изображение
                byte[] imageData = Base64.getDecoder().decode(base64Data);
                Image image = new Image(new ByteArrayInputStream(imageData));
                
                // Обновляем ImageView
                imageView.setImage(image);
                
                // Обновляем статус
                statusLabel.setText("Демонстрация экрана от: " + sender);
                
                // Обновляем FPS
                updateFPS();
                
            } catch (Exception e) {
                System.err.println("Ошибка обработки кадра: " + e.getMessage());
            }
        });
    }
    
    /**
     * Останавливает просмотр
     */
    public void stopViewing() {
        isActive.set(false);
        Platform.runLater(() -> {
            statusLabel.setText("Демонстрация экрана остановлена");
            fpsLabel.setText("FPS: 0");
            imageView.setImage(null);
        });
    }
    
    /**
     * Обновляет счетчик FPS
     */
    private void updateFPS() {
        frameCount++;
        long currentTime = System.currentTimeMillis();
        
        if (currentTime - startTime >= 1000) { // Обновляем FPS каждую секунду
            double fps = (double) frameCount * 1000 / (currentTime - startTime);
            fpsLabel.setText(String.format("FPS: %.1f", fps));
            
            // Сбрасываем счетчики
            frameCount = 0;
            startTime = currentTime;
        }
    }
    
    /**
     * Проверяет, активно ли окно просмотра
     */
    public boolean isActive() {
        return isActive.get() && stage.isShowing();
    }
    
    /**
     * Получает текущего отправителя
     */
    public String getCurrentSender() {
        return currentSender;
    }
}
