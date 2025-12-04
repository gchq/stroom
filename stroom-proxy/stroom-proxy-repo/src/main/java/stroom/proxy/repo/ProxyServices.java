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

package stroom.proxy.repo;

import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;

import io.dropwizard.lifecycle.Managed;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Singleton
public class ProxyServices implements Managed {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyServices.class);

    private final List<Managed> services = new ArrayList<>();
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    public ParallelExecutor addParallelExecutor(final String threadName,
                                                final Supplier<Runnable> runnableSupplier,
                                                final int threadCount) {
        LOGGER.info("Creating parallel executor '{}', threadCount: {}", threadName, threadCount);
        final ParallelExecutor executor = new ParallelExecutor(
                threadName,
                runnableSupplier,
                threadCount);
        addManaged(executor);
        return executor;
    }

    public void addFrequencyExecutor(final String threadName,
                                     final Supplier<Runnable> runnableSupplier,
                                     final long frequencyMs) {
        LOGGER.info("Creating frequency executor  '{}', frequency: {}",
                threadName,
                ModelStringUtil.formatDurationString(frequencyMs, true));
        final FrequencyExecutor executor = new FrequencyExecutor(
                threadName,
                runnableSupplier,
                frequencyMs);
        addManaged(executor);
    }

    private void addManaged(final Managed managed) {
        Objects.requireNonNull(managed);
        LOGGER.debug(() -> LogUtil.message("Registering managed service {} {}",
                managed.getClass().getSimpleName(), managed));
        services.add(managed);
    }

    @Override
    public void start() throws Exception {
        LOGGER.info("Starting Stroom Proxy");

        for (final Managed service : services) {
            service.start();
        }

        LOGGER.info("Started Stroom Proxy");
    }

    @Override
    public void stop() {
        LOGGER.info("Stopping Stroom Proxy");

        isShuttingDown.set(true);

        for (int i = services.size() - 1; i >= 0; i--) {
            try {
                services.get(i).stop();
            } catch (final InterruptedException | UncheckedInterruptedException e) {
                LOGGER.debug(e::getMessage, e);
            } catch (final Exception e) {
                LOGGER.error("error", e);
            }
        }

        // This method is part of DW  Managed which is managed by Jersey, so we need to ensure any interrupts
        // are cleared before it goes back to Jersey
        final boolean interrupted = Thread.interrupted();
        LOGGER.debug("Was interrupted = " + interrupted);

        LOGGER.info("Stopped Stroom Proxy");
    }

    /**
     * @return True if proxy is shutting down
     */
    public boolean isShuttingDown() {
        return isShuttingDown.get();
    }
}
