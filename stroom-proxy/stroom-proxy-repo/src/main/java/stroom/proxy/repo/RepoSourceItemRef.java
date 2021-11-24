package stroom.proxy.repo;

public class RepoSourceItemRef {

    private final long id;
    private final String feedName;
    private final String typeName;
    private final long totalByteSize;

    public RepoSourceItemRef(final long id,
                             final String feedName,
                             final String typeName,
                             final long totalByteSize) {
        this.id = id;
        this.feedName = feedName;
        this.typeName = typeName;
        this.totalByteSize = totalByteSize;
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

    public long getTotalByteSize() {
        return totalByteSize;
    }
}
