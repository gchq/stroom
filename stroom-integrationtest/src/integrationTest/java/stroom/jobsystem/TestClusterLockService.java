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

package stroom.jobsystem;

import org.junit.Assert;
import org.junit.Test;
import stroom.test.AbstractCoreIntegrationTest;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class TestClusterLockService extends AbstractCoreIntegrationTest {
    @Inject
    private ClusterLockServiceInnerTransactions testClusterLockServiceTransaction;

    @Test
    public void test() throws InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(2);
        final List<Integer> sequence = new ArrayList<>(3);

        // This thread should acquire the lock first stopping the second thread
        // from adding to the sequence until after this thread completes.
        final Thread thread1 = new Thread(() -> {
            testClusterLockServiceTransaction.thread1("TEST", sequence);
            countDownLatch.countDown();
        });

        final Thread thread2 = new Thread(() -> {
            testClusterLockServiceTransaction.thread2("TEST", sequence);
            countDownLatch.countDown();
        });

        // Start the threads.
        thread1.start();
        thread2.start();

        // Now make sure the sequence is as expected.
        countDownLatch.await();
        Assert.assertEquals(3, sequence.size());
        Assert.assertEquals(1, sequence.get(0).intValue());
        Assert.assertEquals(2, sequence.get(1).intValue());
        Assert.assertEquals(3, sequence.get(2).intValue());
    }
}
