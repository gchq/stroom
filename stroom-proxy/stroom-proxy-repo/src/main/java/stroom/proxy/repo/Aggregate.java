package stroom.proxy.repo;

public class Aggregate {

    private final long id;
    private final String feedName;
    private final String typeName;

    public Aggregate(final long id,
                     final String feedName,
                     final String typeName) {
        this.id = id;
        this.feedName = feedName;
        this.typeName = typeName;
    }

    public long getId() {
        return id;
    }

    public String getFeedName() {
        return feedName;
    }

    public String getTypeName() {
        return typeName;
    }
}
