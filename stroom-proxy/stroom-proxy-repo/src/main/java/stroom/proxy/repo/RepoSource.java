package stroom.proxy.repo;

import java.util.Objects;

public class RepoSource {

    private final long id;
    private final String sourcePath;
    private final String feedName;
    private final String typeName;
    private final long lastModifiedTimeMs;

    public RepoSource(final long id,
                      final String sourcePath,
                      final String feedName,
                      final String typeName,
                      final long lastModifiedTimeMs) {
        this.id = id;
        this.sourcePath = sourcePath;
        this.feedName = feedName;
        this.typeName = typeName;
        this.lastModifiedTimeMs = lastModifiedTimeMs;
    }

    public long getId() {
        return id;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public String getFeedName() {
        return feedName;
    }

    public String getTypeName() {
        return typeName;
    }

    public long getLastModifiedTimeMs() {
        return lastModifiedTimeMs;
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
                ", sourcePath='" + sourcePath + '\'' +
                ", feedName='" + feedName + '\'' +
                ", typeName='" + typeName + '\'' +
                ", lastModifiedTimeMs=" + lastModifiedTimeMs +
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
        private String sourcePath;
        private String feedName;
        private String typeName;
        private long lastModifiedTimeMs;

        private Builder() {
        }

        private Builder(final RepoSource repoSource) {
            this.id = repoSource.id;
            this.sourcePath = repoSource.sourcePath;
            this.feedName = repoSource.feedName;
            this.typeName = repoSource.typeName;
            this.lastModifiedTimeMs = repoSource.lastModifiedTimeMs;
        }

        public Builder id(final long id) {
            this.id = id;
            return this;
        }

        public Builder sourcePath(final String sourcePath) {
            this.sourcePath = sourcePath;
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

        public Builder lastModifiedTimeMs(final long lastModifiedTimeMs) {
            this.lastModifiedTimeMs = lastModifiedTimeMs;
            return this;
        }

        public RepoSource build() {
            return new RepoSource(id, sourcePath, feedName, typeName, lastModifiedTimeMs);
        }
    }
}
