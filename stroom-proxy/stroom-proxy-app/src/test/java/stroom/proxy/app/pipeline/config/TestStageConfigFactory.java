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

package stroom.proxy.app.pipeline.config;

import stroom.proxy.app.pipeline.stage.aggregate.AggregateStageConfig;
import stroom.proxy.app.pipeline.stage.forward.ForwardStageConfig;
import stroom.proxy.app.pipeline.stage.preaggregate.PreAggregateStageConfig;
import stroom.proxy.app.pipeline.stage.preaggregate.PreAggregateStageThreadsConfig;
import stroom.proxy.app.pipeline.stage.receive.ReceiveStageConfig;
import stroom.proxy.app.pipeline.stage.receive.ReceiveStageThreadsConfig;
import stroom.proxy.app.pipeline.stage.splitzip.SplitZipStageConfig;

/**
 * Test factory methods for constructing stage-specific config objects
 * from the old shared-constructor argument order.
 * <p>
 * This utility makes it easy to migrate tests that previously created
 * {@code PipelineStageConfig(enabled, inputQueue, outputQueue, splitZipQueue, fileStore, threads)}
 * to the new typed config classes.
 * </p>
 */
public final class TestStageConfigFactory {

    private TestStageConfigFactory() {
    }

    public static ReceiveStageConfig receiveConfig(final boolean enabled,
                                                    final String outputQueue,
                                                    final String splitZipQueue,
                                                    final String fileStore,
                                                    final ReceiveStageThreadsConfig threads) {
        return new ReceiveStageConfig(enabled, outputQueue, splitZipQueue, fileStore, threads);
    }

    public static ReceiveStageConfig receiveConfig(final boolean enabled,
                                                    final String outputQueue,
                                                    final String splitZipQueue,
                                                    final String fileStore) {
        return new ReceiveStageConfig(enabled, outputQueue, splitZipQueue, fileStore, new ReceiveStageThreadsConfig());
    }

    public static SplitZipStageConfig splitZipConfig(final boolean enabled,
                                                      final String inputQueue,
                                                      final String outputQueue,
                                                      final String fileStore,
                                                      final ConsumerStageThreadsConfig threads) {
        return new SplitZipStageConfig(enabled, inputQueue, outputQueue, fileStore, threads);
    }

    public static SplitZipStageConfig splitZipConfig(final boolean enabled,
                                                      final String inputQueue,
                                                      final String outputQueue,
                                                      final String fileStore) {
        return new SplitZipStageConfig(enabled, inputQueue, outputQueue, fileStore, new ConsumerStageThreadsConfig());
    }

    public static PreAggregateStageConfig preAggregateConfig(final boolean enabled,
                                                              final String inputQueue,
                                                              final String outputQueue,
                                                              final String fileStore,
                                                              final PreAggregateStageThreadsConfig threads) {
        return new PreAggregateStageConfig(enabled, inputQueue, outputQueue, fileStore, threads);
    }

    public static PreAggregateStageConfig preAggregateConfig(final boolean enabled,
                                                              final String inputQueue,
                                                              final String outputQueue,
                                                              final String fileStore) {
        return new PreAggregateStageConfig(enabled, inputQueue, outputQueue, fileStore,
                new PreAggregateStageThreadsConfig());
    }

    public static AggregateStageConfig aggregateConfig(final boolean enabled,
                                                        final String inputQueue,
                                                        final String outputQueue,
                                                        final String fileStore,
                                                        final ConsumerStageThreadsConfig threads) {
        return new AggregateStageConfig(enabled, inputQueue, outputQueue, fileStore, threads);
    }

    public static AggregateStageConfig aggregateConfig(final boolean enabled,
                                                        final String inputQueue,
                                                        final String outputQueue,
                                                        final String fileStore) {
        return new AggregateStageConfig(enabled, inputQueue, outputQueue, fileStore,
                new ConsumerStageThreadsConfig());
    }

    public static ForwardStageConfig forwardConfig(final boolean enabled,
                                                    final String inputQueue,
                                                    final ConsumerStageThreadsConfig threads) {
        return new ForwardStageConfig(enabled, inputQueue, threads);
    }

    public static ForwardStageConfig forwardConfig(final boolean enabled,
                                                    final String inputQueue) {
        return new ForwardStageConfig(enabled, inputQueue, new ConsumerStageThreadsConfig());
    }
}
