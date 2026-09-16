/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.pipeline.config;

import stroom.proxy.app.pipeline.queue.QueueDefinition;
import stroom.proxy.app.pipeline.stage.aggregate.AggregateStageConfig;
import stroom.proxy.app.pipeline.stage.aggregate.AggregateStageThreadsConfig;
import stroom.proxy.app.pipeline.stage.forward.FanOutStage;
import stroom.proxy.app.pipeline.store.FileStoreDefinition;
import stroom.util.time.StroomDuration;

import java.util.Map;
import java.util.TreeMap;

/**
 * Pipeline configurations for tests.
 */
public final class PipelineConfigs {

    private PipelineConfigs() {
    }

    /**
     * The standard full pipeline with the aggregate stage's bounds set as given.
     */
    public static ProxyPipelineConfig fullPipelineWithAggregateBounds(final int maxItemsPerAggregate,
                                                                      final String maxUncompressedByteSize,
                                                                      final StroomDuration aggregationFrequency) {
        return new ProxyPipelineConfig(
                null,
                new PipelineStagesConfig(
                        ProxyPipelineConfig.defaultReceiveStage(),
                        ProxyPipelineConfig.defaultSplitZipStage(),
                        new AggregateStageConfig(
                                true,
                                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE,
                                ProxyPipelineConfig.AGGREGATE_STORE,
                                maxItemsPerAggregate,
                                maxUncompressedByteSize,
                                aggregationFrequency,
                                new AggregateStageThreadsConfig()),
                        ProxyPipelineConfig.defaultForwardStage()),
                null);
    }

    /**
     * The full pipeline with a queue and a store per forward destination, which is what more than one
     * enabled destination needs: the forward stage fans out to them
     * ({@code designs/stages/forward.md} §4.4).
     */
    public static ProxyPipelineConfig fullPipelineWithFanOut(final int maxItemsPerAggregate,
                                                             final String maxUncompressedByteSize,
                                                             final StroomDuration aggregationFrequency,
                                                             final String... destinationNames) {
        final ProxyPipelineConfig base = fullPipelineWithAggregateBounds(
                maxItemsPerAggregate, maxUncompressedByteSize, aggregationFrequency);
        final Map<String, QueueDefinition> queues = new TreeMap<>(base.getQueues());
        final Map<String, FileStoreDefinition> stores = new TreeMap<>(base.getFileStores());
        for (final String name : destinationNames) {
            queues.put(FanOutStage.queueNameFor(name), new QueueDefinition());
            stores.put(FanOutStage.storeNameFor(name), new FileStoreDefinition());
        }
        return new ProxyPipelineConfig(base.getMode(), queues, base.getStages(), stores);
    }
}
