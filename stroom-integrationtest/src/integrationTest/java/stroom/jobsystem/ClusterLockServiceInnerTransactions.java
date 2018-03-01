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
import org.springframework.transaction.annotation.Transactional;
import stroom.util.thread.ThreadUtil;

import javax.inject.Inject;
import java.util.List;

@Transactional
public class ClusterLockServiceInnerTransactions {
    private final ClusterLockService clusterLockService;

    @Inject
    ClusterLockServiceInnerTransactions(final ClusterLockService clusterLockService) {
        this.clusterLockService = clusterLockService;
    }

    public void thread1(final String lock, final List<Integer> sequence) {
        // This thread should acquire the lock first stopping the second
        // thread from adding to the sequence until after this thread
        // completes.
        sequence.add(1);
        clusterLockService.lock(lock);
        Assert.assertTrue(ThreadUtil.sleep(2000));
        sequence.add(2);
    }

    public void thread2(final String lock, final List<Integer> sequence) {
        Assert.assertTrue(ThreadUtil.sleep(500));
        clusterLockService.lock(lock);
        sequence.add(3);
    }
}
