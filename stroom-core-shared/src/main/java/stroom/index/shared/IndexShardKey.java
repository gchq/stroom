/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.index.shared;

import java.util.Objects;

public class IndexShardKey {

    private final String indexUuid;
    private final Partition partition;

    public IndexShardKey(final String indexUuid,
                         final Partition partition) {
        this.indexUuid = indexUuid;
        this.partition = partition;
    }

    public static IndexShardKey createKey(final LuceneIndexDoc index) {
        return IndexShardKey
                .builder()
                .indexUuid(index.getUuid())
                .partition(AllPartition.INSTANCE)
                .build();
    }

    public static IndexShardKey createKey(final LuceneIndexDoc index,
                                          final Partition partition) {
        return IndexShardKey
                .builder()
                .indexUuid(index.getUuid())
                .partition(partition)
                .build();
    }

    public String getIndexUuid() {
        return indexUuid;
    }

    public Partition getPartition() {
        return partition;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final IndexShardKey that = (IndexShardKey) o;
        return Objects.equals(indexUuid, that.indexUuid) &&
                Objects.equals(partition, that.partition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(indexUuid, partition);
    }

    @Override
    public String toString() {
        return "IndexShardKey{" +
                "indexUuid='" + indexUuid + '\'' +
                ", partition='" + partition + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private String indexUuid;
        private Partition partition;

        private Builder() {
        }

        private Builder(final IndexShardKey indexShardKey) {
            this.indexUuid = indexShardKey.indexUuid;
            this.partition = indexShardKey.partition;
        }

        public Builder indexUuid(final String indexUuid) {
            this.indexUuid = indexUuid;
            return this;
        }

        public Builder partition(final Partition partition) {
            this.partition = partition;
            return this;
        }

        public IndexShardKey build() {
            Objects.requireNonNull(indexUuid);
            Objects.requireNonNull(partition);
            return new IndexShardKey(indexUuid, partition);
        }
    }
}
