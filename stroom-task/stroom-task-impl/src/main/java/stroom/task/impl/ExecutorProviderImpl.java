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

package stroom.task.impl;

import stroom.task.api.ExecutorProvider;
import stroom.task.api.SimpleThreadPool;
import stroom.task.shared.ThreadPool;
import stroom.util.concurrent.ScalingThreadPoolExecutor;
import stroom.util.thread.CustomThreadFactory;
import stroom.util.thread.StroomThreadGroup;

import javax.inject.Singleton;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Singleton
public class ExecutorProviderImpl implements ExecutorProvider {
    public static final ThreadPool DEFAULT_THREAD_POOL = new SimpleThreadPool(2);
    private final AtomicInteger currentAsyncTaskCount = new AtomicInteger();

    // The thread pools that will be used to execute tasks.
    private final ConcurrentHashMap<ThreadPool, ThreadPoolExecutor> threadPoolMap = new ConcurrentHashMap<>();
    private final ReentrantLock poolCreationLock = new ReentrantLock();
    private final AtomicBoolean stop = new AtomicBoolean();

    @Override
    public Executor get() {
        return get(DEFAULT_THREAD_POOL);
    }

    @Override
    public Executor get(final ThreadPool threadPool) {
        return command -> {
            currentAsyncTaskCount.incrementAndGet();
            try {
                final Runnable outer = () -> {
                    try {
                        command.run();
                    } finally {
                        currentAsyncTaskCount.decrementAndGet();
                    }
                };
                getRealExecutor(threadPool).execute(outer);
            } catch (final Throwable t) {
                currentAsyncTaskCount.decrementAndGet();
                throw t;
            }
        };
    }

    private Executor getRealExecutor(final ThreadPool threadPool) {
        Objects.requireNonNull(threadPool, "Null thread pool");
        ThreadPoolExecutor executor = threadPoolMap.get(threadPool);
        if (executor == null) {
            poolCreationLock.lock();
            try {
                // Don't create a thread pool if we are supposed to be stopping
                if (stop.get()) {
                    throw new RejectedExecutionException("Stopping");
                }

                executor = threadPoolMap.computeIfAbsent(threadPool, k -> {
                    // Create a thread factory for the thread pool
                    final ThreadGroup poolThreadGroup = new ThreadGroup(StroomThreadGroup.instance(),
                            threadPool.getName());
                    final CustomThreadFactory taskThreadFactory = new CustomThreadFactory(
                            threadPool.getName() + " #", poolThreadGroup, threadPool.getPriority());

                    // Create the new thread pool for this priority
                    return ScalingThreadPoolExecutor.newScalingThreadPool(
                            threadPool.getCorePoolSize(),
                            threadPool.getMaxPoolSize(),
                            threadPool.getMaxQueueSize(),
                            60L,
                            TimeUnit.SECONDS,
                            taskThreadFactory);
                });
            } finally {
                poolCreationLock.unlock();
            }
        }
        return executor;
    }

    void setStop(final boolean stop) {
        this.stop.set(stop);
    }

    void shutdownExecutors() {
        poolCreationLock.lock();
        try {
            final Iterator<ThreadPool> iter = threadPoolMap.keySet().iterator();
            iter.forEachRemaining(threadPool -> {
                final ThreadPoolExecutor executor = threadPoolMap.get(threadPool);
                if (executor != null) {
                    executor.shutdown();
                    threadPoolMap.remove(threadPool);
                }
            });
        } finally {
            poolCreationLock.unlock();
        }
    }

    public int getCurrentTaskCount() {
        return currentAsyncTaskCount.get();
    }
}
