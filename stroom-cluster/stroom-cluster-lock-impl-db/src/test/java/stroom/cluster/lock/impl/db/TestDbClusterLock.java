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

package stroom.cluster.lock.impl.db;


import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

class TestDbClusterLock {
    @Test
    void test() throws InterruptedException {
        final DbClusterLock dbClusterLock = new DbClusterLock(
                new ClusterLockDbModule().getConnectionProvider(ClusterLockConfig::new));
        final DbClusterLockThreads dbClusterLockThreads = new DbClusterLockThreads(dbClusterLock);

        final CountDownLatch countDownLatch = new CountDownLatch(2);
        final List<Integer> sequence = new ArrayList<>(3);

        // This thread should acquire the lock first stopping the second thread
        // from adding to the sequence until after this thread completes.
        final Thread thread1 = new Thread(() -> {
            dbClusterLockThreads.thread1("TEST", sequence);
            countDownLatch.countDown();
        });

        final Thread thread2 = new Thread(() -> {
            dbClusterLockThreads.thread2("TEST", sequence);
            countDownLatch.countDown();
        });

        // Start the threads.
        thread1.start();
        thread2.start();

        // Now make sure the sequence is as expected.
        countDownLatch.await();
        assertThat(sequence.size()).isEqualTo(3);
        assertThat(sequence.get(0).intValue()).isEqualTo(1);
        assertThat(sequence.get(1).intValue()).isEqualTo(2);
        assertThat(sequence.get(2).intValue()).isEqualTo(3);
    }
}
