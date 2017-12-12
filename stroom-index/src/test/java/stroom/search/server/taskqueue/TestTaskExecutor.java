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

package stroom.search.server.taskqueue;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.thread.ThreadUtil;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class TestTaskExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestTaskExecutor.class);

    private static class TestTaskProducer extends TaskProducer {
        private final LinkedBlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();

        TestTaskProducer(final TaskExecutor taskExecutor, final int maxThreadsPerTask, final Executor executor, final String name) {
            super(taskExecutor, maxThreadsPerTask, executor);

            for (int i = 0; i < 100; i++) {
                getTasksTotal().incrementAndGet();
                final int count = i;
                taskQueue.add(() -> {
                    LOGGER.info("> " + name + " " + count);
                    ThreadUtil.sleep(1);
                    LOGGER.info("< " + name + " " + count);

                    if (count == 50) {
                        // Throw exceptions to try and break stuff.
                        throw new RuntimeException("Deliberate exception");
                    }
                });
            }

            // Attach to the supplied executor.
            attach();

            // Tell the supplied executor that we are ready to deliver tasks.
            signalAvailable();
        }

        @Override
        protected Runnable getNext() {
            return taskQueue.poll();
        }
    }

    @Test
    public void test() {
        final ExecutorService executorService = Executors.newCachedThreadPool();

        final TaskExecutor taskExecutor = new TaskExecutor("Test Executor");
        taskExecutor.setMaxThreads(3);

        final TaskProducer taskProducer1 = new TestTaskProducer(taskExecutor, 3, executorService, "tp1");
        final TaskProducer taskProducer2 = new TestTaskProducer(taskExecutor, 3, executorService, "tp2");

        while (!taskProducer1.isComplete() && !taskProducer2.isComplete()) {
            LOGGER.info("Remaining tasks (tp1 = " + taskProducer1.getRemainingTasks() + ", tp2 = " + taskProducer2.getRemainingTasks() + ")");
            ThreadUtil.sleep(10);
        }

        executorService.shutdown();
    }
}
