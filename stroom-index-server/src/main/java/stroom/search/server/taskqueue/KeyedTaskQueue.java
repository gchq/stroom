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

package stroom.search.server.taskqueue;

import stroom.util.shared.Task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class KeyedTaskQueue implements TaskQueue {
    public static final int DEFAULT_CAPACITY = 1000;
    /**
     * Main lock guarding all access
     */
    final ReentrantLock lock = new ReentrantLock();
    private final KeyProvider keyProvider;
    private final Prioritiser prioritiser;
    private final List<Object> keyList = new ArrayList<>();
    private final Map<Object, List<Task<?>>> map = new HashMap<>();
    /**
     * Condition for waiting takes
     */
    private final Condition notEmpty = lock.newCondition();
    /**
     * Condition for waiting puts
     */
    private final Condition notFull = lock.newCondition();
    private volatile int keyIndex;
    private volatile int capacity;
    private volatile int size;
    public KeyedTaskQueue() {
        this(new UserKeyProvider(), new BasicPrioritiser(), DEFAULT_CAPACITY);
    }
    public KeyedTaskQueue(final KeyProvider keyProvider, final Prioritiser prioritiser, final int capacity) {
        this.keyProvider = keyProvider;
        this.prioritiser = prioritiser;
        this.capacity = capacity;
    }

    @Override
    public boolean offer(final Task<?> task) {
        if (task.isTerminated()) {
            return true;
        }

        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return tryAdd(task);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean offer(final Task<?> task, final long timeout, final TimeUnit unit) throws InterruptedException {
        if (task.isTerminated()) {
            return true;
        }

        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            while (!tryAdd(task)) {
                if (nanos <= 0)
                    return false;
                nanos = notFull.awaitNanos(nanos);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Task<?> poll() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return tryRemove();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Task<?> poll(final long timeout, final TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            Task<?> x;
            while ((x = tryRemove()) == null) {
                if (nanos <= 0)
                    return null;
                nanos = notEmpty.awaitNanos(nanos);
            }
            return x;
        } finally {
            lock.unlock();
        }
    }

    private boolean tryAdd(final Task<?> task) {
        if (size >= capacity) {
            return false;
        }

        final Object key = keyProvider.getKey(task);
        List<Task<?>> tasks = map.get(key);
        if (tasks == null) {
            tasks = new ArrayList<>();
            keyList.add(key);
            map.put(key, tasks);
        }

        tasks.add(task);
        ++size;
        notEmpty.signal();
        return true;
    }

    private Task<?> tryRemove() {
        Task<?> task = null;

        if (keyList.size() > 0) {
            if (keyIndex >= keyList.size()) {
                keyIndex = 0;
            }

            final Object key = keyList.get(keyIndex);
            final List<Task<?>> tasks = map.get(key);

            final int before = tasks.size();
            task = prioritiser.select(tasks);
            final int removed = before - tasks.size();

            size -= removed;

            if (tasks.size() == 0) {
                keyList.remove(keyIndex);
                map.remove(key);
            } else {
                keyIndex++;
            }
        }

        notFull.signal();
        return task;
    }

    public void setCapacity(final int capacity) {
        this.capacity = capacity;
    }

    public interface KeyProvider {
        Object getKey(Task<?> task);
    }

    public static class UserKeyProvider implements KeyProvider {
        @Override
        public Object getKey(final Task<?> task) {
            return task.getUserToken();
        }
    }
}
