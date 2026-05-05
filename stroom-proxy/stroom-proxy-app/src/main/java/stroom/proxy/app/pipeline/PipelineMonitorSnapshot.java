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

package stroom.proxy.app.pipeline;

import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of pipeline runtime state for monitoring and diagnostics.
 * <p>
 * Consumed by the admin monitoring servlet to render pipeline queue/stage metrics.
 * </p>
 */
public record PipelineMonitorSnapshot(
        List<StageSnapshot> stages,
        List<QueueSnapshot> queues,
        List<FileStoreSnapshot> fileStores) {

    public record StageSnapshot(
            String name,
            boolean hasWorker,
            int threadCount,
            FileGroupQueueWorkerCounters.Snapshot counters) {

        /**
         * @return A formatted summary string for diagnostic output.
         */
        public String toSummary() {
            if (!hasWorker) {
                return name + " (HTTP-driven, no worker)";
            }
            if (counters == null) {
                return name + " (threads=" + threadCount + ", no counters)";
            }
            return name
                   + " (threads=" + threadCount
                   + ", polled=" + counters.pollCount()
                   + ", processed=" + counters.itemProcessedCount()
                   + ", acked=" + counters.itemAcknowledgedCount()
                   + ", failed=" + counters.itemFailedCount()
                   + ", errors=" + (counters.processorErrorCount()
                                    + counters.acknowledgeErrorCount()
                                    + counters.failErrorCount()
                                    + counters.closeErrorCount())
                   + ")";
        }
    }

    public record QueueSnapshot(
            String name,
            String type,
            boolean healthy,
            String healthDetail,
            Map<String, Long> depths,
            SqsHeartbeatCounters.Snapshot heartbeatCounters) {

        /**
         * Backward-compatible constructor used by existing code.
         */
        public QueueSnapshot(final String name, final String type) {
            this(name, type, true, null, null, null);
        }

        /**
         * @return A formatted summary string for diagnostic output.
         */
        public String toSummary() {
            final StringBuilder sb = new StringBuilder(name)
                    .append(" (type=").append(type);
            if (!healthy) {
                sb.append(", UNHEALTHY: ").append(healthDetail);
            }
            if (depths != null && !depths.isEmpty()) {
                sb.append(", pending=").append(depths.getOrDefault("pending", 0L))
                  .append(", inflight=").append(depths.getOrDefault("inflight", 0L))
                  .append(", failed=").append(depths.getOrDefault("failed", 0L));
            }
            if (heartbeatCounters != null) {
                sb.append(", heartbeats=").append(heartbeatCounters.attemptCount())
                  .append("/").append(heartbeatCounters.successCount())
                  .append("/").append(heartbeatCounters.failureCount());
            }
            return sb.append(")").toString();
        }
    }

    public record FileStoreSnapshot(
            String name,
            boolean healthy,
            String healthDetail) {

        /**
         * Backward-compatible constructor used by existing code.
         */
        public FileStoreSnapshot(final String name) {
            this(name, true, null);
        }

        /**
         * @return A formatted summary string for diagnostic output.
         */
        public String toSummary() {
            if (!healthy) {
                return name + " (UNHEALTHY: " + healthDetail + ")";
            }
            return name;
        }
    }
}
