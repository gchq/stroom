/*
 * Copyright 2017 Crown Copyright
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

package stroom.index.impl;


import stroom.index.shared.AllPartition;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.Partition;
import stroom.util.concurrent.AtomicSequence;

public final class IndexShardKeyUtil {

    private static final AtomicSequence SEQUENCE = new AtomicSequence();

    private IndexShardKeyUtil() {
        // Utility class
    }

    public static IndexShardKey createTestKey(final IndexDoc index) {
        final int shardNo = SEQUENCE.next(index.getShardsPerPartition());

        return IndexShardKey
                .builder()
                .indexUuid(index.getUuid())
                .partition(AllPartition.INSTANCE)
                .shardNo(shardNo)
                .build();
    }

    public static IndexShardKey createKey(final IndexDoc index,
                                          final Partition partition) {
        final int shardNo = SEQUENCE.next(index.getShardsPerPartition());
        return IndexShardKey
                .builder()
                .indexUuid(index.getUuid())
                .partition(partition)
                .shardNo(shardNo)
                .build();
    }
}
