package anti.messanger.sxdpandoram;

public class ChatMessage {
    private final String content;
    private final String sender;
    private final String timestamp;
    private final boolean sentByMe;
    private final String conversationPartner; // null для публичных, имя собеседника для ЛС

    public ChatMessage(String content, String sender, String timestamp, boolean sentByMe, String conversationPartner) {
        this.content = content;
        this.sender = sender;
        this.timestamp = timestamp;
        this.sentByMe = sentByMe;
        this.conversationPartner = conversationPartner;
    }

    public String getContent() { return content; }
    public String getSender() { return sender; }
    public String getTimestamp() { return timestamp; }
    public boolean isSentByMe() { return sentByMe; }
    public String getConversationPartner() { return conversationPartner; }
}