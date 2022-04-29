package stroom.proxy.repo;

import java.util.Objects;

public class RepoSource {

    private final long id;
    private final long fileStoreId;
    private final String feedName;
    private final String typeName;

    public RepoSource(final long id,
                      final long fileStoreId,
                      final String feedName,
                      final String typeName) {
        this.id = id;
        this.fileStoreId = fileStoreId;
        this.feedName = feedName;
        this.typeName = typeName;
    }

    public long getId() {
        return id;
    }

    public long getFileStoreId() {
        return fileStoreId;
    }

    public String getFeedName() {
        return feedName;
    }

    public String getTypeName() {
        return typeName;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RepoSource source = (RepoSource) o;
        return id == source.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RepoSource{" +
                "id=" + id +
                ", fileStoreId='" + fileStoreId + '\'' +
                ", feedName='" + feedName + '\'' +
                ", typeName='" + typeName + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static class Builder {

        private long id;
        private long fileStoreId;
        private String feedName;
        private String typeName;

        private Builder() {
        }

        private Builder(final RepoSource repoSource) {
            this.id = repoSource.id;
            this.fileStoreId = repoSource.fileStoreId;
            this.feedName = repoSource.feedName;
            this.typeName = repoSource.typeName;
        }

        public Builder id(final long id) {
            this.id = id;
            return this;
        }

        public Builder fileStoreId(final long fileStoreId) {
            this.fileStoreId = fileStoreId;
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

        public RepoSource build() {
            return new RepoSource(id, fileStoreId, feedName, typeName);
        }
    }
}
