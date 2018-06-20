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

import stroom.data.store.ScanVolumePathResult;
import stroom.util.shared.ModelStringUtil;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

class FileSystemCleanProgress {
    private final AtomicLong scanDirCount = new AtomicLong();
    private final AtomicLong scanFileCount = new AtomicLong();
    private final AtomicLong scanDeleteCount = new AtomicLong();
    private final AtomicLong scanTooNewToDeleteCount = new AtomicLong();
    private final AtomicLong scanPending = new AtomicLong(0);
    private final AtomicLong scanComplete = new AtomicLong(0);
    private final Queue<String> lineQueue = new ConcurrentLinkedQueue<>();

    FileSystemCleanProgress() {
    }

    void addResult(final ScanVolumePathResult result) {
        scanDirCount.incrementAndGet();
        scanFileCount.addAndGet(result.getFileCount());
        scanDeleteCount.addAndGet(result.getDeleteList().size());
        scanTooNewToDeleteCount.addAndGet(result.getTooNewToDeleteCount());
        lineQueue.addAll(result.getDeleteList());
    }

    String traceInfo() {
        return "scanDirCount " + ModelStringUtil.formatCsv(scanDirCount) + ", scanFileCount "
                + ModelStringUtil.formatCsv(scanFileCount) + ", scanDeleteCount "
                + ModelStringUtil.formatCsv(scanDeleteCount) + ", scanTooNewToDeleteCount "
                + ModelStringUtil.formatCsv(scanTooNewToDeleteCount);
    }

    AtomicLong getScanDirCount() {
        return scanDirCount;
    }

    AtomicLong getScanDeleteCount() {
        return scanDeleteCount;
    }

    AtomicLong getScanFileCount() {
        return scanFileCount;
    }

    void addScanPending(int value) {
        scanPending.addAndGet(value);
    }

    void addScanComplete() {
        scanComplete.incrementAndGet();
        scanPending.decrementAndGet();
    }

//    long getScanPending() {
//        return scanPending.get();
//    }
//
//    long getScanComplete() {
//        return scanComplete.get();
//    }

    Queue<String> getLineQueue() {
        return lineQueue;
    }
}
