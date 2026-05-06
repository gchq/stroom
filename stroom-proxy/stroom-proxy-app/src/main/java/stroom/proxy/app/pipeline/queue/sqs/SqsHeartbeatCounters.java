/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.proxy.app.pipeline.queue.sqs;

import stroom.proxy.app.pipeline.monitor.PipelineMetricsRegistrar;
import stroom.proxy.app.pipeline.stage.FileGroupQueueWorkerCounters;

import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe counters for SQS visibility heartbeat operations.
 * <p>
 * Follows the same {@link LongAdder} pattern as {@link FileGroupQueueWorkerCounters}.
 * These counters are wired into the existing heartbeat lambda in
 * {@link SqsFileGroupQueue} and exported as Prometheus metrics by
 * {@link PipelineMetricsRegistrar}.
 * </p>
 */
public class SqsHeartbeatCounters {

    private final LongAdder attemptCount = new LongAdder();
    private final LongAdder successCount = new LongAdder();
    private final LongAdder failureCount = new LongAdder();
    private final LongAdder cancelledCount = new LongAdder();

    public void incrementAttemptCount() {
        attemptCount.increment();
    }

    public void incrementSuccessCount() {
        successCount.increment();
    }

    public void incrementFailureCount() {
        failureCount.increment();
    }

    public void incrementCancelledCount() {
        cancelledCount.increment();
    }

    public long getAttemptCount() {
        return attemptCount.sum();
    }

    public long getSuccessCount() {
        return successCount.sum();
    }

    public long getFailureCount() {
        return failureCount.sum();
    }

    public long getCancelledCount() {
        return cancelledCount.sum();
    }

    public Snapshot snapshot() {
        return new Snapshot(
                getAttemptCount(),
                getSuccessCount(),
                getFailureCount(),
                getCancelledCount());
    }

    public record Snapshot(
            long attemptCount,
            long successCount,
            long failureCount,
            long cancelledCount) {
    }
}
