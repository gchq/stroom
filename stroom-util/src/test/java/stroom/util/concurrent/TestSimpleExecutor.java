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

package stroom.util.concurrent;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.thread.ThreadUtil;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestSimpleExecutor {
    @Test
    public void testSimpleShutDownNow() {
        SimpleExecutor simpleExecutor = new SimpleExecutor(5);
        for (int i = 0; i < 100; i++) {
            // 10 sec task
            simpleExecutor.execute(new TestRunnable(10000));
        }
        Thread.yield();

        simpleExecutor.stop(true);

        Assert.assertEquals(100, simpleExecutor.getExecutorSubmitCount());
        Assert.assertEquals(5, simpleExecutor.getExecutorCompleteCount());
    }

    @Test
    public void testSimpleShutDown() {
        SimpleExecutor simpleExecutor = new SimpleExecutor(5);
        for (int i = 0; i < 10; i++) {
            // Submit 10 very quick tasks
            simpleExecutor.execute(new TestRunnable(10));
        }

        simpleExecutor.stop(false);

        Assert.assertEquals(10, simpleExecutor.getExecutorSubmitCount());
        Assert.assertEquals(10, simpleExecutor.getExecutorCompleteCount());
    }

    @Test
    public void testSimpleShutDownNowAndMoreTasksAdded() {
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

        Assert.assertEquals(5, simpleExecutor.getExecutorCompleteCount());
    }

    @Test
    public void testWaitForCompleteAndAddMore() {
        final SimpleExecutor simpleExecutor = new SimpleExecutor(5);
        for (int i = 0; i < 10; i++) {
            // Submit 10 quick tasks
            simpleExecutor.execute(new TestRunnable(10));
            Assert.assertFalse(simpleExecutor.isStopped());
        }

        simpleExecutor.waitForComplete();
        Assert.assertFalse(simpleExecutor.isStopped());

        Assert.assertEquals(10, simpleExecutor.getExecutorSubmitCount());
        Assert.assertEquals(10, simpleExecutor.getExecutorCompleteCount());

        simpleExecutor.waitForComplete();
        Assert.assertFalse(simpleExecutor.isStopped());

        for (int i = 0; i < 10; i++) {
            // Submit 10 quick tasks
            simpleExecutor.execute(new TestRunnable(10));
        }

        simpleExecutor.waitForComplete();

        Assert.assertEquals(20, simpleExecutor.getExecutorSubmitCount());
        Assert.assertEquals(20, simpleExecutor.getExecutorCompleteCount());

        Assert.assertFalse(simpleExecutor.isStopped());
        simpleExecutor.stop(true);
        Assert.assertTrue(simpleExecutor.isStopped());
    }

    static class TestRunnable implements Runnable {
        int time;
        boolean complete = false;

        public TestRunnable(int time) {
            this.time = time;
        }

        @Override
        public void run() {
            ThreadUtil.sleep(time);
            complete = true;
        }

        public boolean isComplete() {
            return complete;
        }
    }

}
