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

package stroom.proxy.app;

import stroom.proxy.app.handler.ForwardFileConfig;
import stroom.proxy.app.pipeline.config.PipelineStagesConfig;
import stroom.proxy.app.pipeline.config.ProxyPipelineConfig;
import stroom.proxy.app.pipeline.stage.receive.ReceiveStageConfig;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A node that does not receive must not be fed by the scanner or SQS, which would hand everything to
 * a receiver that refuses it.
 */
class TestProxyConfigReceiveSources {

    @Test
    void testADisabledReceiveStageWithTheScannerOnIsRefused() {
        assertThat(config(false, true).isReceiveStageEnabledForSources()).isFalse();
        assertThat(config(false, false).isReceiveStageEnabledForSources()).isTrue();
        assertThat(config(true, true).isReceiveStageEnabledForSources()).isTrue();
    }

    @Test
    void testAnUnstatedReceiveStageIsLeftToThePipelineValidator() {
        final ProxyConfig proxyConfig = ProxyConfig.builder()
                .pipelineConfig(ProxyPipelineConfig.unconfigured())
                .dirScannerConfig(new DirScannerConfig(null, null, true, null))
                .build();

        assertThat(proxyConfig.isReceiveStageEnabledForSources()).isTrue();
    }

    @Test
    void testInstantForwardingIsExempt() {
        final ProxyConfig proxyConfig = ProxyConfig.builder()
                .pipelineConfig(pipeline(false))
                .dirScannerConfig(new DirScannerConfig(null, null, true, null))
                .addForwardFileDestination(ForwardFileConfig.builder()
                        .enabled()
                        .withInstant(true)
                        .withName("instant")
                        .withPath("dest")
                        .build())
                .build();

        assertThat(proxyConfig.isReceiveStageEnabledForSources()).isTrue();
    }

    private static ProxyConfig config(final boolean receiveEnabled, final boolean scannerEnabled) {
        return ProxyConfig.builder()
                .pipelineConfig(pipeline(receiveEnabled))
                .dirScannerConfig(new DirScannerConfig(null, null, scannerEnabled, null))
                .build();
    }

    private static ProxyPipelineConfig pipeline(final boolean receiveEnabled) {
        final ProxyPipelineConfig defaults = new ProxyPipelineConfig();
        final PipelineStagesConfig stages = defaults.getStages();
        return new ProxyPipelineConfig(
                defaults.getQueues(),
                new PipelineStagesConfig(
                        new ReceiveStageConfig(receiveEnabled,
                                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                                ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE,
                                ProxyPipelineConfig.RECEIVE_STORE),
                        stages.getSplitZip(),
                        stages.getAggregate(),
                        stages.getForward()),
                defaults.getFileStores());
    }
}
