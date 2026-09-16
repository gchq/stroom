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

package stroom.proxy.app.pipeline.monitor;

import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.execution.Loop;
import stroom.proxy.app.execution.LoopTask;
import stroom.proxy.app.execution.Phase;
import stroom.proxy.app.execution.WorkRegistry;
import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.runtime.ProxyPipelineRuntime;

import com.codahale.metrics.health.HealthCheck;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The aggregate health check kept each component's boolean and, when unhealthy, its message —
 * and discarded {@code getDetails()} entirely.
 * <p>
 * What was thrown away is not decoration. {@code SqsFileGroupQueue} returns
 * {@code approximateMessages}, {@code approximateInFlight} and {@code activeHeartbeats} from a live
 * {@code GetQueueAttributes}, and {@code KafkaFileGroupQueue} returns its topic and partitions — so
 * the proxy paid for an AWS call on every health-check request and threw the answer away, and queue
 * depth was reported nowhere else for either backend. An operator asking a proxy how deep its queue
 * was got a boolean.
 * </p>
 */
class TestPipelineHealthChecks {

    @Test
    void testAQueuesOwnHealthDetailsSurviveIntoTheAggregateResult() {
        final FileGroupQueue queue = Mockito.mock(FileGroupQueue.class);
        Mockito.when(queue.healthCheck()).thenReturn(HealthCheck.Result.builder()
                .healthy()
                .withDetail("approximateMessages", 42L)
                .withDetail("approximateInFlight", 7L)
                .build());

        final ProxyPipelineRuntime runtime = Mockito.mock(ProxyPipelineRuntime.class);
        Mockito.when(runtime.getQueues()).thenReturn(Map.of("receiveOutput", queue));
        Mockito.when(runtime.getFileStores()).thenReturn(Map.of());

        final HealthCheck.Result result = new PipelineHealthChecks(
                ProxyConfig.builder().build(),
                () -> null,
                new WorkRegistry()).checkRuntime(runtime);

        assertThat(result.isHealthy()).isTrue();

        @SuppressWarnings("unchecked")
        final Map<String, Object> components = (Map<String, Object>) result.getDetails().get("components");
        assertThat(components)
                .as("the queue's own numbers must reach the operator, namespaced by queue")
                .containsEntry("queue.receiveOutput.approximateMessages", 42L)
                .containsEntry("queue.receiveOutput.approximateInFlight", 7L)
                .containsEntry("queue.receiveOutput.healthy", true);
    }

    @Test
    void testALoopWithFewerLiveThreadsThanConfiguredIsUnhealthy() {
        final ProxyPipelineRuntime runtime = Mockito.mock(ProxyPipelineRuntime.class);
        Mockito.when(runtime.getQueues()).thenReturn(Map.of());
        Mockito.when(runtime.getFileStores()).thenReturn(Map.of());

        final WorkRegistry registry = new WorkRegistry();
        // A task that interrupts its own thread is the only way a loop thread can leave.
        final AtomicInteger runs = new AtomicInteger();
        final Loop loop = registry.loop("stage-forward", Phase.FORWARD, 1, () -> {
            runs.incrementAndGet();
            Thread.currentThread().interrupt();
            return LoopTask.Outcome.NOTHING;
        });
        registry.start();
        try {
            final Instant deadline = Instant.now().plusSeconds(5);
            while (!(runs.get() == 1 && loop.liveThreads() == 0) && Instant.now().isBefore(deadline)) {
                Thread.onSpinWait();
            }
            final HealthCheck.Result result = new PipelineHealthChecks(
                    ProxyConfig.builder().build(), () -> null, registry).checkRuntime(runtime);

            assertThat(result.isHealthy()).isFalse();
            @SuppressWarnings("unchecked")
            final Map<String, Object> components = (Map<String, Object>) result.getDetails().get("components");
            assertThat(components)
                    .containsEntry("loop.stage-forward.threads.configured", 1)
                    .containsEntry("loop.stage-forward.threads.live", 0);
        } finally {
            registry.stop();
        }
    }
}
