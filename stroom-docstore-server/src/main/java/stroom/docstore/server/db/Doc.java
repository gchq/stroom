package stroom.docstore.server.db;

class Doc {
    // Value of a long to represent an undefined id.
    private static final long UNDEFINED_ID = -1;

    private long id = UNDEFINED_ID;
    private String type;
    private String uuid;
    private String name;
    private byte[] data;

    Doc(final long id, final String type, final String uuid, final String name, final byte[] data) {
        this.id = id;
        this.type = type;
        this.uuid = uuid;
        this.name = name;
        this.data = data;
    }

    public long getId() {
        return id;
    }

    public void setId(final long id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(final String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(final byte[] data) {
        this.data = data;
    }
}