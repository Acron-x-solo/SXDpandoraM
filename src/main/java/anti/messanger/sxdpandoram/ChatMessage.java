package anti.messanger.sxdpandoram;

public class ChatMessage {
    private final String content;
    private final String sender;
    private final String timestamp;
    private final boolean sentByMe;
    private final String conversationPartner;

    private final boolean isFileOffer;
    private final String filePreviewData;
    private final String fileName;
    private final long fileSize;
    private final String downloadUrl; // Поле для ссылки

    // Конструктор для обычных текстовых сообщений
    public ChatMessage(String content, String sender, String timestamp, boolean sentByMe, String conversationPartner) {
        this.content = content;
        this.sender = sender;
        this.timestamp = timestamp;
        this.sentByMe = sentByMe;
        this.conversationPartner = conversationPartner;
        this.isFileOffer = false;
        this.filePreviewData = null;
        this.fileName = null;
        this.fileSize = 0;
        this.downloadUrl = null;
    }

    // Конструктор для ПРЕДЛОЖЕНИЙ файлов (когда кнопки "Принять/Отклонить")
    public ChatMessage(String sender, String timestamp, boolean sentByMe, String conversationPartner, String fileName, long fileSize, String filePreviewData) {
        this.sender = sender;
        this.timestamp = timestamp;
        this.sentByMe = sentByMe;
        this.conversationPartner = conversationPartner;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.filePreviewData = filePreviewData;
        this.isFileOffer = true;
        this.content = "Предложение файла: " + fileName;
        this.downloadUrl = null;
    }

    // НОВЫЙ Конструктор для СООБЩЕНИЙ СО ССЫЛКОЙ (когда кнопка "Скачать")
    public ChatMessage(String sender, String timestamp, boolean sentByMe, String conversationPartner, String fileName, long fileSize, String filePreviewData, String downloadUrl) {
        this.sender = sender;
        this.timestamp = timestamp;
        this.sentByMe = sentByMe;
        this.conversationPartner = conversationPartner;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.filePreviewData = filePreviewData;
        this.isFileOffer = true;
        this.content = "Ссылка на файл: " + fileName;
        this.downloadUrl = downloadUrl; // Сохраняем ссылку
    }

    // Геттеры для всех полей
    public String getContent() { return content; }
    public String getSender() { return sender; }
    public String getTimestamp() { return timestamp; }
    public boolean isSentByMe() { return sentByMe; }
    public String getConversationPartner() { return conversationPartner; }
    public boolean isFileOffer() { return isFileOffer; }
    public String getFilePreviewData() { return filePreviewData; }
    public String getFileName() { return fileName; }
    public long getFileSize() { return fileSize; }
    public String getDownloadUrl() { return downloadUrl; }
    
    public boolean isSystemMessage() {
        return sender != null && sender.equals("Система");
    }
    
    public boolean isFileMessage() {
        return isFileOffer && fileName != null;
    }
}