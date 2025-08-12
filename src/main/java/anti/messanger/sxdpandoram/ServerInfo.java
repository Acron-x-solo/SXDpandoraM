package anti.messanger.sxdpandoram;

public class ServerInfo {
    private final long id;
    private final String name;
    private final String description;
    private final String createdBy;

    public ServerInfo(long id, String name, String description, String createdBy) {
        this.id = id;
        this.name = name;
        this.description = description == null ? "" : description;
        this.createdBy = createdBy;
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCreatedBy() { return createdBy; }

    @Override
    public String toString() {
        return name;
    }
}
