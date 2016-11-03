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

package stroom.streamtask.server;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import stroom.streamstore.server.ScanVolumePathResult;
import stroom.util.shared.ModelStringUtil;

public class FileSystemCleanProgress {
    private final AtomicLong scanDirCount = new AtomicLong();
    private final AtomicLong scanFileCount = new AtomicLong();
    private final AtomicLong scanDeleteCount = new AtomicLong();
    private final AtomicLong scanTooNewToDeleteCount = new AtomicLong();
    private final AtomicLong scanPending = new AtomicLong(0);
    private final AtomicLong scanComplete = new AtomicLong(0);
    private final Queue<String> lineQueue = new ConcurrentLinkedQueue<>();

    public FileSystemCleanProgress() {
    }

    public void addResult(final ScanVolumePathResult result) {
        scanDirCount.incrementAndGet();
        scanFileCount.addAndGet(result.getFileCount());
        scanDeleteCount.addAndGet(result.getDeleteList().size());
        scanTooNewToDeleteCount.addAndGet(result.getTooNewToDeleteCount());
        lineQueue.addAll(result.getDeleteList());
    }

    public String traceInfo() {
        return "scanDirCount " + ModelStringUtil.formatCsv(scanDirCount) + ", scanFileCount "
                + ModelStringUtil.formatCsv(scanFileCount) + ", scanDeleteCount "
                + ModelStringUtil.formatCsv(scanDeleteCount) + ", scanTooNewToDeleteCount "
                + ModelStringUtil.formatCsv(scanTooNewToDeleteCount);
    }

    public AtomicLong getScanDirCount() {
        return scanDirCount;
    }

    public AtomicLong getScanDeleteCount() {
        return scanDeleteCount;
    }

    public AtomicLong getScanFileCount() {
        return scanFileCount;
    }

    public void addScanPending(int value) {
        scanPending.addAndGet(value);
    }

    public void addScanComplete() {
        scanComplete.incrementAndGet();
        scanPending.decrementAndGet();
    }

    public long getScanPending() {
        return scanPending.get();
    }

    public long getScanComplete() {
        return scanComplete.get();
    }

    public Queue<String> getLineQueue() {
        return lineQueue;
    }
}
