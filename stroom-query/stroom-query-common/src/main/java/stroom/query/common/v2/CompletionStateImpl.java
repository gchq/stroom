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

package stroom.query.common.v2;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CompletionStateImpl implements CompletionState {

    private final AtomicBoolean complete = new AtomicBoolean();
    private final CountDownLatch countDownLatch = new CountDownLatch(1);

    @Override
    public void signalComplete() {
        complete.set(true);
        countDownLatch.countDown();
    }

    @Override
    public boolean isComplete() {
        return complete.get();
    }

    @Override
    public void awaitCompletion() throws InterruptedException {
        countDownLatch.await();
    }

    @Override
    public boolean awaitCompletion(final long timeout, final TimeUnit unit) throws InterruptedException {
        return countDownLatch.await(timeout, unit);
    }

    @Override
    public String toString() {
        return "CompletionStateImpl{" +
                "complete=" + complete +
                ", countDownLatch=" + countDownLatch +
                '}';
    }
}
