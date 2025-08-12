package anti.messanger.sxdpandoram;

public class GroupInfo {
    private final long id;
    private final String name;
    private final String description;
    private final String createdBy;
    private final String createdAt;
    private final boolean isAdmin;

    public GroupInfo(long id, String name, String description, String createdBy, String createdAt, boolean isAdmin) {
        this.id = id;
        this.name = name;
        this.description = description == null ? "" : description;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.isAdmin = isAdmin;
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCreatedBy() { return createdBy; }
    public String getCreatedAt() { return createdAt; }
    public boolean isAdmin() { return isAdmin; }

    @Override
    public String toString() {
        return name;
    }
} 