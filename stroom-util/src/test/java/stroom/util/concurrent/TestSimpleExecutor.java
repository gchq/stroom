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

package stroom.util.concurrent;


import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestSimpleExecutor {

    @Test
    void testSimpleShutDownNow() throws InterruptedException {
        final SimpleExecutor simpleExecutor = new SimpleExecutor(5);
        for (int i = 0; i < 100; i++) {
            // 10 sec task
            simpleExecutor.execute(new TestRunnable(10000));
        }
        Thread.yield();

        simpleExecutor.stop(true);

        assertThat(simpleExecutor.getExecutorSubmitCount()).isEqualTo(100);
        assertThat(simpleExecutor.getExecutorCompleteCount()).isEqualTo(5);
    }

    @Test
    void testSimpleShutDown() throws InterruptedException {
        final SimpleExecutor simpleExecutor = new SimpleExecutor(5);
        for (int i = 0; i < 10; i++) {
            // Submit 10 very quick tasks
            simpleExecutor.execute(new TestRunnable(10));
        }

        simpleExecutor.stop(false);

        assertThat(simpleExecutor.getExecutorSubmitCount()).isEqualTo(10);
        assertThat(simpleExecutor.getExecutorCompleteCount()).isEqualTo(10);
    }

    @Test
    void testSimpleShutDownNowAndMoreTasksAdded() throws InterruptedException {
        final SimpleExecutor simpleExecutor = new SimpleExecutor(5);
        for (int i = 0; i < 10; i++) {
            // Submit 10 slow tasks
            simpleExecutor.execute(new TestRunnable(1000) {
                @Override
                public void run() {
                    super.run();
                    // Run again
                    simpleExecutor.execute(new TestRunnable(1000));
                }
            });
            Thread.yield();
        }

        simpleExecutor.stop(true);

        assertThat(simpleExecutor.getExecutorCompleteCount()).isEqualTo(5);
    }

    @Test
    void testWaitForCompleteAndAddMore() throws InterruptedException {
        final SimpleExecutor simpleExecutor = new SimpleExecutor(5);
        for (int i = 0; i < 10; i++) {
            // Submit 10 quick tasks
            simpleExecutor.execute(new TestRunnable(10));
            assertThat(simpleExecutor.isStopped()).isFalse();
        }

        simpleExecutor.waitForComplete();
        assertThat(simpleExecutor.isStopped()).isFalse();

        assertThat(simpleExecutor.getExecutorSubmitCount()).isEqualTo(10);
        assertThat(simpleExecutor.getExecutorCompleteCount()).isEqualTo(10);

        simpleExecutor.waitForComplete();
        assertThat(simpleExecutor.isStopped()).isFalse();

        for (int i = 0; i < 10; i++) {
            // Submit 10 quick tasks
            simpleExecutor.execute(new TestRunnable(10));
        }

        simpleExecutor.waitForComplete();

        assertThat(simpleExecutor.getExecutorSubmitCount()).isEqualTo(20);
        assertThat(simpleExecutor.getExecutorCompleteCount()).isEqualTo(20);

        assertThat(simpleExecutor.isStopped()).isFalse();
        simpleExecutor.stop(true);
        assertThat(simpleExecutor.isStopped()).isTrue();
    }

    static class TestRunnable implements Runnable {

        int time;

        TestRunnable(final int time) {
            this.time = time;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(time);
            } catch (final InterruptedException e) {
                // Continue to interrupt this thread.
                Thread.currentThread().interrupt();
            }
        }
    }

}
