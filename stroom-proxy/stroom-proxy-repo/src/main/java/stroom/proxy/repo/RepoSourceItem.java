package stroom.proxy.repo;

import java.util.List;

public class RepoSourceItem {

    private final RepoSource source;
    private final String name;
    private final long feedId;
    private final Long aggregateId;
    private long totalByteSize;
    private final List<RepoSourceEntry> entries;

    public RepoSourceItem(final RepoSource source,
                          final String name,
                          final long feedId,
                          final Long aggregateId,
                          final long totalByteSize,
                          final List<RepoSourceEntry> entries) {
        this.source = source;
        this.name = name;
        this.feedId = feedId;
        this.aggregateId = aggregateId;
        this.totalByteSize = totalByteSize;
        this.entries = entries;
    }

    public RepoSource getSource() {
        return source;
    }

    public String getName() {
        return name;
    }

    public long getFeedId() {
        return feedId;
    }

    public Long getAggregateId() {
        return aggregateId;
    }

    public long getTotalByteSize() {
        return totalByteSize;
    }

    public List<RepoSourceEntry> getEntries() {
        return entries;
    }

    public void addEntry(final RepoSourceEntry entry) {
        this.entries.add(entry);
        this.totalByteSize += entry.byteSize();
    }

    @Override
    public String toString() {
        return "RepoSourceItem{" +
                ", source=" + source +
                ", name='" + name + '\'' +
                ", feedId='" + feedId + '\'' +
                ", aggregateId=" + aggregateId +
                ", totalByteSize=" + totalByteSize +
                ", entries=" + entries +
                '}';
    }
}
