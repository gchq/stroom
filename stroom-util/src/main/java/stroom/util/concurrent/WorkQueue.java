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

import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class WorkQueue {

    private final int threadCount;
    private final ArrayBlockingQueue<Optional<Runnable>> queue;
    private final CompletableFuture<Void>[] futures;

    @SuppressWarnings("unchecked")
    public WorkQueue(final Executor executor,
                     final int threadCount,
                     final int capacity) {
        this.threadCount = threadCount;
        queue = new ArrayBlockingQueue<>(capacity);
        futures = new CompletableFuture[threadCount];
        for (int i = 0; i < threadCount; i++) {
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    Optional<Runnable> optional = queue.take();
                    while (optional.isPresent()) {
                        optional.get().run();
                        optional = queue.take();
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e.getMessage(), e);
                }
            }, executor);
        }
    }

    public void exec(final Runnable runnable) {
        try {
            queue.put(Optional.of(runnable));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public void join() {
        for (int i = 0; i < threadCount; i++) {
            try {
                queue.put(Optional.empty());
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        CompletableFuture.allOf(futures).join();
    }
}
