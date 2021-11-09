package stroom.proxy.repo;

import java.util.ArrayList;
import java.util.List;

public class RepoSourceItem {

    private final RepoSource source;
    private final String name;
    private final String feedName;
    private final String typeName;
    private final Long aggregateId;
    private long totalByteSize;
    private final List<RepoSourceEntry> entries;

    private RepoSourceItem(final RepoSource source,
                           final String name,
                           final String feedName,
                           final String typeName,
                           final Long aggregateId,
                           final long totalByteSize,
                           final List<RepoSourceEntry> entries) {
        this.source = source;
        this.name = name;
        this.feedName = feedName;
        this.typeName = typeName;
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

    public String getFeedName() {
        return feedName;
    }

    public String getTypeName() {
        return typeName;
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
        this.totalByteSize += entry.getByteSize();
    }

    @Override
    public String toString() {
        return "RepoSourceItem{" +
                ", source=" + source +
                ", name='" + name + '\'' +
                ", feedName='" + feedName + '\'' +
                ", typeName='" + typeName + '\'' +
                ", aggregateId=" + aggregateId +
                ", totalByteSize=" + totalByteSize +
                ", entries=" + entries +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static class Builder {

        private RepoSource source;
        private String name;
        private String feedName;
        private String typeName;
        private Long aggregateId;
        private long totalByteSize;
        private final List<RepoSourceEntry> entries;

        private Builder() {
            entries = new ArrayList<>();
        }

        private Builder(final RepoSourceItem item) {
            source = item.source;
            name = item.name;
            feedName = item.feedName;
            typeName = item.typeName;
            aggregateId = item.aggregateId;
            totalByteSize = item.totalByteSize;
            entries = new ArrayList<>(item.entries);
        }

        public Builder source(final RepoSource source) {
            this.source = source;
            return this;
        }

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public Builder feedName(final String feedName) {
            this.feedName = feedName;
            return this;
        }

        public Builder typeName(final String typeName) {
            this.typeName = typeName;
            return this;
        }

        public Builder aggregateId(final Long aggregateId) {
            this.aggregateId = aggregateId;
            return this;
        }

        public Builder addEntry(final RepoSourceEntry entry) {
            this.entries.add(entry);
            this.totalByteSize += entry.getByteSize();
            return this;
        }

        public RepoSourceItem build() {
            return new RepoSourceItem(
                    source,
                    name,
                    feedName,
                    typeName,
                    aggregateId,
                    totalByteSize,
                    entries);
        }
    }
}
