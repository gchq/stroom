/*
 * Copyright 2017 Crown Copyright
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.concurrent.SimpleExecutor;
import stroom.util.shared.Monitor;

public abstract class StroomZipRepositorySimpleExecutorProcessor extends StroomZipRepositoryProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomZipRepositorySimpleExecutorProcessor.class);

    private final int threadCount;

    private SimpleExecutor simpleExecutor;

    public StroomZipRepositorySimpleExecutorProcessor(final Monitor monitor, final int threadCount) {
        super(monitor);
        this.threadCount = threadCount;
    }

    @Override
    public final synchronized void startExecutor() {
        if (simpleExecutor != null && !simpleExecutor.isStopped()) {
            throw new RuntimeException("simpleExecutor is still running?");
        }
        // Start up the thread worker pool
        simpleExecutor = new SimpleExecutor(threadCount);
    }

    @Override
    public final synchronized void stopExecutor(final boolean now) {
        if (simpleExecutor != null) {
            simpleExecutor.stop(now);
        }
    }

    @Override
    public void waitForComplete() {
        simpleExecutor.waitForComplete();
    }

    @Override
    public void execute(final String message, final Runnable runnable) {
        try {
            runnable.run();
        } catch (final Exception ex) {
            LOGGER.error("doRunWork()", ex);
        }
    }
}
