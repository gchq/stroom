/*
 * Copyright 2016 Crown Copyright
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

package stroom.util.zip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.concurrent.SimpleExecutor;
import stroom.util.shared.Monitor;
import stroom.util.thread.ThreadScopeRunnable;

public abstract class StroomZipRepositorySimpleExecutorProcessor extends StroomZipRepositoryProcessor {
    private SimpleExecutor simpleExecutor;

    private static final Logger LOGGER = LoggerFactory.getLogger(StroomZipRepositorySimpleExecutorProcessor.class);
    private int threadCount = 1;

    public StroomZipRepositorySimpleExecutorProcessor(final Monitor monitor) {
        super(monitor);
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(final int threadCount) {
        this.threadCount = threadCount;
    }

    @Override
    public final synchronized void startExecutor() {
        if (simpleExecutor != null && !simpleExecutor.isStopped()) {
            throw new RuntimeException("simpleExecutor is still running?");
        }
        // Start up the thread worker pool
        simpleExecutor = new SimpleExecutor(getThreadCount());
    }

    @Override
    public void stopExecutor(final boolean now) {
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
        simpleExecutor.execute(new ThreadScopeRunnable() {
            @Override
            protected void exec() {
                try {
                    runnable.run();
                } catch (final Exception ex) {
                    LOGGER.error("doRunWork()", ex);
                }
            }
        });
    }
}
