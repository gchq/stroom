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

import stroom.index.shared.LuceneIndexDoc.PartitionBy;

import java.util.Objects;

public class TimePartition implements Partition {

    private final PartitionBy partitionBy;
    private final int partitionSize;
    private final long partitionFromTime;
    private final long partitionToTime;
    private final String label;

    public TimePartition(final PartitionBy partitionBy,
                         final int partitionSize,
                         final long partitionFromTime,
                         final long partitionToTime,
                         final String label) {
        this.partitionBy = partitionBy;
        this.partitionSize = partitionSize;
        this.partitionFromTime = partitionFromTime;
        this.partitionToTime = partitionToTime;
        this.label = label;
    }

    public PartitionBy getPartitionBy() {
        return partitionBy;
    }

    public int getPartitionSize() {
        return partitionSize;
    }

    @Override
    public Long getPartitionFromTime() {
        return partitionFromTime;
    }

    @Override
    public Long getPartitionToTime() {
        return partitionToTime;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TimePartition that = (TimePartition) o;
        return partitionFromTime == that.partitionFromTime && partitionToTime == that.partitionToTime;
    }

    @Override
    public int hashCode() {
        return Objects.hash(partitionFromTime, partitionToTime);
    }

    @Override
    public String toString() {
        return "TimePartition{" +
                "partitionBy=" + partitionBy +
                ", partitionSize=" + partitionSize +
                ", partitionFromTime=" + partitionFromTime +
                ", partitionToTime=" + partitionToTime +
                ", label='" + label + '\'' +
                '}';
    }
}
