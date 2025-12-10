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

import stroom.data.store.impl.ScanVolumePathResult;
import stroom.task.api.TaskContext;
import stroom.util.shared.ModelStringUtil;

import java.util.concurrent.atomic.AtomicLong;

class FsOrphanFileFinderProgress {

    private final String volume;
    private final TaskContext taskContext;
    private final AtomicLong scanDirCount = new AtomicLong();
    private final AtomicLong scanFileCount = new AtomicLong();
    private final AtomicLong scanOrphanCount = new AtomicLong();
    private final AtomicLong scanTooNewToDeleteCount = new AtomicLong();
    private final AtomicLong scanPending = new AtomicLong(0);
    private final AtomicLong scanComplete = new AtomicLong(0);

    FsOrphanFileFinderProgress(final String volume,
                               final TaskContext taskContext) {
        this.volume = volume;
        this.taskContext = taskContext;
        log();
    }

    void addResult(final ScanVolumePathResult result) {
        scanDirCount.incrementAndGet();
        scanFileCount.addAndGet(result.getFileCount());
        scanOrphanCount.addAndGet(result.getDeleteList().size());
        scanTooNewToDeleteCount.addAndGet(result.getTooNewToDeleteCount());
    }

    String traceInfo() {
        return "scanDirCount " + ModelStringUtil.formatCsv(scanDirCount) + ", scanFileCount "
                + ModelStringUtil.formatCsv(scanFileCount) + ", scanDeleteCount "
                + ModelStringUtil.formatCsv(scanOrphanCount) + ", scanTooNewToDeleteCount "
                + ModelStringUtil.formatCsv(scanTooNewToDeleteCount);
    }

    void addDir() {
        scanDirCount.incrementAndGet();
        log();
    }

    void addOrphanCount() {
        scanOrphanCount.incrementAndGet();
        log();
    }

    void addFile() {
        scanFileCount.incrementAndGet();
        log();
    }

    void addScanPending(final int value) {
        scanPending.addAndGet(value);
    }

    void addScanComplete() {
        scanComplete.incrementAndGet();
        scanPending.decrementAndGet();
    }

    void log() {
        taskContext.info(() -> volume +
                " (Scan Dir/File " +
                scanDirCount.get() +
                "/" +
                scanFileCount.get() +
                ") found " +
                scanOrphanCount.get() +
                " orphans");
    }
}
