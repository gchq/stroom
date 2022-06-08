package stroom.proxy.repo;

import stroom.proxy.repo.dao.FeedDao;

public record RepoSourceItem(
        RepoSource repoSource,
        long itemId,
        String name,
        long feedId,
        Long aggregateId,
        long totalByteSize,
        String extensions) {

    public static class RepoSourceItemBuilder {

        private RepoSource repoSource;
        private long id;
        private String name;
        private String feedName;
        private String typeName;
        private Long aggregateId;
        private long totalByteSize;
        private String extensions = "";

        public RepoSourceItemBuilder repoSource(RepoSource repoSource) {
            this.repoSource = repoSource;
            return this;
        }

        public RepoSourceItemBuilder id(final long id) {
            this.id = id;
            return this;
        }

        public RepoSourceItemBuilder name(final String name) {
            this.name = name;
            return this;
        }

        public RepoSourceItemBuilder feedName(final String feedName) {
            this.feedName = feedName;
            return this;
        }

        public RepoSourceItemBuilder typeName(final String typeName) {
            this.typeName = typeName;
            return this;
        }

        public RepoSourceItemBuilder aggregateId(final Long aggregateId) {
            this.aggregateId = aggregateId;
            return this;
        }

        public RepoSourceItemBuilder addEntry(final String extension, final long byteSize) {
            if (extensions.length() == 0) {
                this.extensions = extension;
            } else {
                this.extensions = this.extensions + "," + extension;
            }
            this.totalByteSize += byteSize;
            return this;
        }

        public String getTypeName() {
            return typeName;
        }

        public String getFeedName() {
            return feedName;
        }

        public RepoSourceItem build(final FeedDao feedDao) {
            final long feedId = feedDao.getId(new FeedKey(feedName, typeName));
            return new RepoSourceItem(
                    repoSource,
                    id,
                    name,
                    feedId,
                    aggregateId,
                    totalByteSize,
                    extensions);
        }
    }
}
