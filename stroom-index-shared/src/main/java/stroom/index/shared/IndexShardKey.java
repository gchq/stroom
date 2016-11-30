/*
 * Copyright 2016 Crown Copyright
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

import stroom.util.shared.EqualsBuilder;

public class IndexShardKey {
    private final Index index;
    private final String partition;
    private final int shardNo;
    private final int hashCode;

    // The time that the partition that this shard belongs to starts
    private final Long partitionFromTime;
    // The time that the partition that this shard belongs to ends
    private final Long partitionToTime;

    public IndexShardKey(final Index index, final String partition, final Long partitionFromTime,
            final Long partitionToTime, final int shardNo) {
        this.index = index;
        this.partition = partition;
        this.partitionFromTime = partitionFromTime;
        this.partitionToTime = partitionToTime;
        this.shardNo = shardNo;

        int code = index.hashCode();
        code = (code * 31) + partition.hashCode();
        code = (code * 31) + Integer.valueOf(shardNo).hashCode();
        hashCode = code;
    }

    public Index getIndex() {
        return index;
    }

    public String getPartition() {
        return partition;
    }

    public Long getPartitionFromTime() {
        return partitionFromTime;
    }

    public Long getPartitionToTime() {
        return partitionToTime;
    }

    public int getShardNo() {
        return shardNo;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof IndexShardKey)) {
            return false;
        }

        final IndexShardKey key = (IndexShardKey) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(index, key.index);
        builder.append(partition, key.partition);
        builder.append(shardNo, key.shardNo);
        return builder.isEquals();
    }
}
