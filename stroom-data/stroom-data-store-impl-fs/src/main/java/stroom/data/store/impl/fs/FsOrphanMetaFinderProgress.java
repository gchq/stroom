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

package stroom.data.store.impl.fs;

import stroom.task.api.TaskContext;
import stroom.util.shared.NullSafe;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

class FsOrphanMetaFinderProgress {

    private final TaskContext taskContext;
    private final AtomicLong minId = new AtomicLong();
    private final long maxId;
    private final AtomicLong id = new AtomicLong();
    private final LongAdder orphanCount = new LongAdder();
    // Count of metas checked
    private final LongAdder metaCount = new LongAdder();
    private final LongAdder cacheMissCount = new LongAdder();
    private Duration fileListDuration = Duration.ZERO;

    private final int batchSize;

    // streamType => Map(parentPath => Set<rootFilePath>)
    private final Map<String, Map<Path, Set<Path>>> dirListingMap = new HashMap<>();

    public FsOrphanMetaFinderProgress(final TaskContext taskContext,
                                      final long maxId,
                                      final int batchSize) {
        this.taskContext = taskContext;
        this.maxId = maxId;
        this.batchSize = batchSize;
    }

    void setMinId(final long minId) {
        this.minId.set(minId);
    }

    public long getId() {
        return id.get();
    }

    /**
     * Only call this once per meta
     */
    void setId(final long id) {
        this.id.set(id);
        metaCount.increment();
    }

    public int getBatchSize() {
        return batchSize;
    }

    public long getMaxId() {
        return maxId;
    }

    void foundOrphan() {
        orphanCount.increment();
    }

    public long getMetaCount() {
        return metaCount.longValue();
    }

    public long getOrphanCount() {
        return orphanCount.longValue();
    }

    public long getCacheMissCount() {
        return cacheMissCount.longValue();
    }

    public Duration getTotalFileListDuration() {
        return fileListDuration;
    }

    public Duration getAverageFileListDuration() {
        if (fileListDuration.isZero()) {
            return Duration.ZERO;
        } else {
            // Each cache miss corresponds to a file list action so we use it to get the average
            return fileListDuration.dividedBy(cacheMissCount.longValue());
        }
    }

    /**
     * Call once per meta
     */
    void recordCacheMiss() {
        cacheMissCount.increment();
    }

    void recordFileListDuration(final Duration duration) {
        if (duration != null) {
            this.fileListDuration = this.fileListDuration.plus(duration);
        }
    }

    Optional<Set<Path>> getCachedRootFiles(final String streamTypeName,
                                           final Path parentPath) {
        return NullSafe.getAsOptional(
                dirListingMap.get(streamTypeName),
                map -> map.get(parentPath));
    }

    void updateCachedRootFiles(final Map<String, Map<Path, Set<Path>>> map) {
        dirListingMap.clear();
        if (map != null) {
            dirListingMap.putAll(map);
        }
    }

    void log() {
        taskContext.info(() -> "Checking meta id " +
                               id.get() +
                               ", batch (" +
                               minId.get() +
                               " => " +
                               maxId +
                               "), found " +
                               orphanCount.longValue() +
                               " orphans (batch size " + batchSize + ")");
    }
}
