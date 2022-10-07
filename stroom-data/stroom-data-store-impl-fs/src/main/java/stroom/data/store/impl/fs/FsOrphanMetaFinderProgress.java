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

package stroom.data.store.impl.fs;

import stroom.task.api.TaskContext;

import java.util.concurrent.atomic.AtomicLong;

class FsOrphanMetaFinderProgress {

    private final TaskContext taskContext;
    private final AtomicLong minId = new AtomicLong();
    private final long maxId;
    private final AtomicLong id = new AtomicLong();
    private final AtomicLong orphanCount = new AtomicLong(0);
    private final int batchSize;

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

    void setId(final long id) {
        this.id.set(id);
    }

    public int getBatchSize() {
        return batchSize;
    }

    public long getMaxId() {
        return maxId;
    }

    void foundOrphan() {
        orphanCount.incrementAndGet();
    }

    void log() {
        taskContext.info(() -> "Checking meta id " +
                id.get() +
                ", batch (" +
                minId.get() +
                " => " +
                maxId +
                "), found " +
                orphanCount.get() +
                " orphans (batch size " + batchSize + ")");
    }
}
