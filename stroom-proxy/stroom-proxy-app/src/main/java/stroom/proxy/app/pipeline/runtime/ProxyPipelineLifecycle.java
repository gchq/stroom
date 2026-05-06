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

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Lifecycle manager for the reference-message proxy pipeline.
 * <p>
 * Creates a {@link PipelineStageRunner} for each enabled queue-consuming stage
 * in the runtime model and provides coordinated start/stop across all runners.
 * </p>
 * <p>
 * Stages without a configured {@link FileGroupQueueWorker} (i.e. the receive
 * stage, which is HTTP-driven rather than queue-driven) do not get a runner.
 * </p>
 */
public class ProxyPipelineLifecycle implements AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyPipelineLifecycle.class);
    public static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);

    private final List<PipelineStageRunner> runners;
    private final Duration shutdownTimeout;

    private ProxyPipelineLifecycle(final List<PipelineStageRunner> runners,
                                   final Duration shutdownTimeout) {
        this.runners = List.copyOf(Objects.requireNonNull(runners, "runners"));
        this.shutdownTimeout = Objects.requireNonNull(shutdownTimeout, "shutdownTimeout");
    }

    /**
     * Create a lifecycle manager from a pipeline runtime.
     * <p>
     * A runner is created for every stage that has a worker. The thread count
     * for each runner is taken from the stage's
     * {@link PipelineStageThreadsConfig#getConsumerThreads()}.
     * </p>
     *
     * @param runtime The assembled pipeline runtime.
     * @return A lifecycle manager ready to be started.
     */
    public static ProxyPipelineLifecycle fromRuntime(final ProxyPipelineRuntime runtime) {
        return fromRuntime(runtime, DEFAULT_SHUTDOWN_TIMEOUT);
    }

    /**
     * Create a lifecycle manager from a pipeline runtime with a custom shutdown
     * timeout.
     *
     * @param runtime The assembled pipeline runtime.
     * @param shutdownTimeout Maximum time to wait for each runner to drain
     *                        during shutdown.
     * @return A lifecycle manager ready to be started.
     */
    public static ProxyPipelineLifecycle fromRuntime(final ProxyPipelineRuntime runtime,
                                                      final Duration shutdownTimeout) {
        Objects.requireNonNull(runtime, "runtime");

        final List<PipelineStageRunner> runners = new ArrayList<>();

        runtime.streamStages()
                .filter(ProxyPipelineRuntime.RuntimeStage::hasWorker)
                .forEach(stage -> {
                    final FileGroupQueueWorker worker = stage.getWorker().orElseThrow();
                    final int threadCount = stage.getThreads().getConsumerThreads();

                    LOGGER.debug(() -> LogUtil.message(
                            "Creating stage runner for {} with {} consumer thread(s)",
                            stage.getConfigName(),
                            threadCount));

                    runners.add(new PipelineStageRunner(
                            stage.stageName(),
                            worker,
                            threadCount));
                });

        return new ProxyPipelineLifecycle(runners, shutdownTimeout);
    }

    /**
     * Start all stage runners.
     */
    public void start() {
        LOGGER.info(() -> LogUtil.message(
                "Starting proxy pipeline lifecycle with {} stage runner(s)",
                runners.size()));

        for (final PipelineStageRunner runner : runners) {
            runner.start();
        }

        LOGGER.info(() -> "Proxy pipeline lifecycle started");
    }

    /**
     * Stop all stage runners, waiting up to the configured shutdown timeout
     * for each.
     */
    public void stop() {
        LOGGER.info(() -> LogUtil.message(
                "Stopping proxy pipeline lifecycle ({} runner(s), timeout {})",
                runners.size(),
                shutdownTimeout));

        // Stop in reverse order so downstream consumers stop before
        // upstream producers, reducing in-flight work.
        final List<PipelineStageRunner> reversed = new ArrayList<>(runners);
        Collections.reverse(reversed);

        for (final PipelineStageRunner runner : reversed) {
            final boolean clean = runner.stop(shutdownTimeout);
            if (!clean) {
                LOGGER.warn(() -> LogUtil.message(
                        "Stage runner {} did not shut down cleanly",
                        runner.getStageName().getConfigName()));
            }
        }

        LOGGER.info(() -> "Proxy pipeline lifecycle stopped");
    }

    /**
     * @return An unmodifiable list of the stage runners managed by this
     * lifecycle.
     */
    public List<PipelineStageRunner> getRunners() {
        return runners;
    }

    /**
     * @return True if all stage runners are currently running.
     */
    public boolean isRunning() {
        return !runners.isEmpty() && runners.stream().allMatch(PipelineStageRunner::isRunning);
    }

    @Override
    public void close() {
        stop();
    }
}
