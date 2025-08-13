package anti.messanger.sxdpandoram;

public class UserProfile {
    private final String username;
    private final String displayName;
    private final String status;
    private final String email;
    private final byte[] avatarBytes;

    public UserProfile(String username, String displayName, String status, String email, byte[] avatarBytes) {
        this.username = username;
        this.displayName = displayName == null ? "" : displayName;
        this.status = status == null ? "" : status;
        this.email = email == null ? "" : email;
        this.avatarBytes = avatarBytes;
    }

    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getStatus() { return status; }
    public String getEmail() { return email; }
    public byte[] getAvatarBytes() { return avatarBytes; }
} 