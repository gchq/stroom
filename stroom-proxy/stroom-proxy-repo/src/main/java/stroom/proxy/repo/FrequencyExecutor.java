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
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.thread.CustomThreadFactory;
import stroom.util.thread.StroomThreadGroup;

import io.dropwizard.lifecycle.Managed;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class FrequencyExecutor implements Managed {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FrequencyExecutor.class);

    private final ScheduledExecutorService executorService;
    private final Supplier<Runnable> runnableSupplier;
    private final long frequency;
    private final String threadNamePrefix;

    public FrequencyExecutor(final String threadNamePrefix,
                             final Supplier<Runnable> runnableSupplier,
                             final long frequency) {
        this.runnableSupplier = runnableSupplier;
        this.frequency = frequency;
        this.threadNamePrefix = threadNamePrefix;
        final ThreadFactory threadFactory = new CustomThreadFactory(
                threadNamePrefix + " ",
                StroomThreadGroup.instance(),
                Thread.NORM_PRIORITY - 1);
        executorService = Executors.newScheduledThreadPool(1, threadFactory);
    }

    @Override
    public void start() {
        final Runnable runnable = () -> {
            try {
                runnableSupplier.get().run();
            } catch (final UncheckedInterruptedException e) {
                // Swallow the exception to keep the scheduled executor running
                LOGGER.debug("Frequency executor interrupted '{}' task: {}",
                        threadNamePrefix, LogUtil.exceptionMessage(e), e);
            } catch (final RuntimeException e) {
                // Swallow the exception to keep the scheduled executor running
                LOGGER.error("Error running frequency executor '{}' task: {}",
                        threadNamePrefix, LogUtil.exceptionMessage(e), e);
            }
        };
        LOGGER.debug("Starting frequency executor '{}', frequency: {}", threadNamePrefix, frequency);
        executorService.scheduleWithFixedDelay(runnable, 0, frequency, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        LOGGER.info("Stopping frequency executor '{}', frequency: {}", threadNamePrefix, frequency);
        final DurationTimer timer = DurationTimer.start();
        executorService.shutdownNow();
        LOGGER.debug("Stopped frequency executor '{}', frequency: {}, duration: {}",
                threadNamePrefix, frequency, timer);
    }

    @Override
    public String toString() {
        return "FrequencyExecutor{" +
               "executorService=" + executorService +
               ", frequency=" + frequency +
               ", threadNamePrefix='" + threadNamePrefix + '\'' +
               '}';
    }
}
