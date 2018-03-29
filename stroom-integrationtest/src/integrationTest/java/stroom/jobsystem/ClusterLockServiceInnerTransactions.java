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

import stroom.persist.EntityManagerSupport;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class ClusterLockServiceInnerTransactions {
    private final ClusterLockService clusterLockService;
    private final EntityManagerSupport entityManagerSupport;
    private final CountDownLatch countDownLatch = new CountDownLatch(1);

    @Inject
    ClusterLockServiceInnerTransactions(final ClusterLockService clusterLockService,
                                        final EntityManagerSupport entityManagerSupport) {
        this.clusterLockService = clusterLockService;
        this.entityManagerSupport = entityManagerSupport;
    }

    public void thread1(final String lock, final List<Integer> sequence) {
        entityManagerSupport.transaction(entityManager -> {
            // This thread should acquire the lock first stopping the second
            // thread from adding to the sequence until after this thread
            // completes.
            sequence.add(1);
            clusterLockService.lock(lock);
            try {
                countDownLatch.countDown();
                Thread.sleep(100);
            } catch (final InterruptedException e) {
                // Continue to interrupt this thread.
                Thread.currentThread().interrupt();

                throw new RuntimeException(e.getMessage(), e);
            }
            sequence.add(2);
        });
    }

    public void thread2(final String lock, final List<Integer> sequence) {
        entityManagerSupport.transaction(entityManager -> {
            try {
                countDownLatch.await();
            } catch (final InterruptedException e) {
                // Continue to interrupt this thread.
                Thread.currentThread().interrupt();

                throw new RuntimeException(e.getMessage(), e);
            }

            clusterLockService.lock(lock);
            sequence.add(3);
        });
    }
}
