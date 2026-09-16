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

package stroom.proxy.app.handler;

import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.execution.WorkRegistry;
import stroom.proxy.app.pipeline.runtime.ProxyPipelineAssembler;
import stroom.proxy.app.pipeline.runtime.TestProxyPipelineAssembler;
import stroom.proxy.app.pipeline.stage.forward.ForwardDestinations;
import stroom.proxy.app.pipeline.stage.forward.GiveUp;
import stroom.proxy.repo.LogStream;
import stroom.receive.common.AttributeMapFilterFactory;
import stroom.receive.common.ReceiveAllAttributeMapFilter;
import stroom.receive.common.ReceiveDataConfig;
import stroom.test.common.MockMetrics;
import stroom.util.io.SimplePathCreator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nothing in the tree constructed {@link ProxyPipelineAssembler}. Its only two references were a
 * harness in {@code TestProxyPipelineAssembler} — whose own comment says it "replicates
 * ProxyPipelineAssembler wiring" — and a {@code Mockito.mock} identity check.
 * <p>
 * A replica cannot diverge silently only if something compares it to the original, and nothing did.
 * Production moved to resolving queue and store names from stage config while the harness still
 * hard-coded the constants, and the harness's receive wiring became outright wrong, so it could not
 * exercise the fix it had been written for.
 * </p>
 * <p>
 * The config is shared with the harness rather than repeated, since repeating it is exactly what
 * went wrong.
 * </p>
 */
class TestProxyPipelineAssemblerWiring {

    @Test
    void testTheRealAssemblerBuildsItsRuntimeFromStageConfig(@TempDir final Path dataDir)
            throws Exception {
        final ProxyConfig proxyConfig = ProxyConfig.builder().build();
        final WorkRegistry workRegistry = new WorkRegistry();
        // One destination that goes nowhere, so the forward stage has a loop to register.
        final Destination destination = Mockito.mock(Destination.class);
        Mockito.when(destination.getName()).thenReturn("assembler-test");
        final ForwardDestinations.Wiring wiring = new ForwardDestinations.Wiring(
                "assembler-test",
                destination,
                new GiveUp(Mockito.mock(Destination.class)),
                new ForwardRetryConfig().toBounds(),
                Duration.ofMinutes(1),
                1);
        final ProxyPipelineAssembler assembler = new ProxyPipelineAssembler(
                TestProxyPipelineAssembler.createFullPipelineConfig(),
                new ProxyId(proxyConfig, () -> dataDir),
                List.of(wiring),
                new SimplePathCreator(() -> dataDir, () -> dataDir),
                ProxyConfig.DEFAULT_DURABILITY,
                new MockMetrics(),
                workRegistry);
        try {
            assertThat(assembler.getRuntime().getQueues().keySet())
                    .as("the queues must be the ones stage config names, not constants a harness holds")
                    .containsExactlyInAnyOrderElementsOf(
                            TestProxyPipelineAssembler.createFullPipelineConfig().getQueues().keySet());
            assertThat(assembler.getRuntime().getFileStores().keySet())
                    .containsExactlyInAnyOrderElementsOf(
                            TestProxyPipelineAssembler.createFullPipelineConfig().getFileStores().keySet());
            assertThat(assembler.getReceiveWiring())
                    .as("and the receipt entry point must be wired, which is what the harness got wrong")
                    .isNotNull();
            assertThat(assembler.getReceiveWiring().splitZipQueue())
                    .as("the configured split-zip queue")
                    .isNotNull();
        } finally {
            assembler.getRuntime().close();
        }
    }
}
