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
 * Consumed by the admin monitoring servlet to render pipeline queue/stage
 * metrics alongside the legacy DirQueue metrics.
 * </p>
 */
public record PipelineMonitorSnapshot(
        boolean pipelineEnabled,
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
            String type) {

        /**
         * @return A formatted summary string for diagnostic output.
         */
        public String toSummary() {
            return name + " (type=" + type + ")";
        }
    }

    public record FileStoreSnapshot(
            String name) {

        /**
         * @return A formatted summary string for diagnostic output.
         */
        public String toSummary() {
            return name;
        }
    }
}
