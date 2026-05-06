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

package stroom.proxy.app.pipeline;

import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe counters for a {@link FileGroupQueueWorker}.
 * <p>
 * These counters deliberately avoid binding the worker to a particular metrics
 * implementation. The worker can update these counters directly and a later
 * metrics/monitoring adapter can periodically read {@link #snapshot()} to expose
 * the values through the normal Stroom metrics mechanisms.
 * </p>
 */
public class FileGroupQueueWorkerCounters {

    private final LongAdder pollCount = new LongAdder();
    private final LongAdder emptyPollCount = new LongAdder();
    private final LongAdder itemReceivedCount = new LongAdder();
    private final LongAdder itemProcessedCount = new LongAdder();
    private final LongAdder itemAcknowledgedCount = new LongAdder();
    private final LongAdder itemFailedCount = new LongAdder();
    private final LongAdder processorErrorCount = new LongAdder();
    private final LongAdder acknowledgeErrorCount = new LongAdder();
    private final LongAdder failErrorCount = new LongAdder();
    private final LongAdder closeErrorCount = new LongAdder();

    public void incrementPollCount() {
        pollCount.increment();
    }

    public void incrementEmptyPollCount() {
        emptyPollCount.increment();
    }

    public void incrementItemReceivedCount() {
        itemReceivedCount.increment();
    }

    public void incrementItemProcessedCount() {
        itemProcessedCount.increment();
    }

    public void incrementItemAcknowledgedCount() {
        itemAcknowledgedCount.increment();
    }

    public void incrementItemFailedCount() {
        itemFailedCount.increment();
    }

    public void incrementProcessorErrorCount() {
        processorErrorCount.increment();
    }

    public void incrementAcknowledgeErrorCount() {
        acknowledgeErrorCount.increment();
    }

    public void incrementFailErrorCount() {
        failErrorCount.increment();
    }

    public void incrementCloseErrorCount() {
        closeErrorCount.increment();
    }

    public long getPollCount() {
        return pollCount.sum();
    }

    public long getEmptyPollCount() {
        return emptyPollCount.sum();
    }

    public long getItemReceivedCount() {
        return itemReceivedCount.sum();
    }

    public long getItemProcessedCount() {
        return itemProcessedCount.sum();
    }

    public long getItemAcknowledgedCount() {
        return itemAcknowledgedCount.sum();
    }

    public long getItemFailedCount() {
        return itemFailedCount.sum();
    }

    public long getProcessorErrorCount() {
        return processorErrorCount.sum();
    }

    public long getAcknowledgeErrorCount() {
        return acknowledgeErrorCount.sum();
    }

    public long getFailErrorCount() {
        return failErrorCount.sum();
    }

    public long getCloseErrorCount() {
        return closeErrorCount.sum();
    }

    public Snapshot snapshot() {
        return new Snapshot(
                getPollCount(),
                getEmptyPollCount(),
                getItemReceivedCount(),
                getItemProcessedCount(),
                getItemAcknowledgedCount(),
                getItemFailedCount(),
                getProcessorErrorCount(),
                getAcknowledgeErrorCount(),
                getFailErrorCount(),
                getCloseErrorCount());
    }

    public record Snapshot(
            long pollCount,
            long emptyPollCount,
            long itemReceivedCount,
            long itemProcessedCount,
            long itemAcknowledgedCount,
            long itemFailedCount,
            long processorErrorCount,
            long acknowledgeErrorCount,
            long failErrorCount,
            long closeErrorCount) {

        public boolean hasErrors() {
            return processorErrorCount > 0
                   || acknowledgeErrorCount > 0
                   || failErrorCount > 0
                   || closeErrorCount > 0;
        }
    }
}
