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

package stroom.search.shard;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class TransferList<E> {
    // Maximum number of items in the list
    private final int capacity;
    // Main lock guarding all access
    private final ReentrantLock lock = new ReentrantLock();
    // Condition for waiting takes
    private final Condition notEmpty = lock.newCondition();
    // Condition for waiting puts
    private final Condition notFull = lock.newCondition();
    private List<E> addList;
    private List<E> removeList;

    public TransferList(final int capacity) {
        this.capacity = capacity;
        addList = new ArrayList<>();
        removeList = new ArrayList<>();
    }

    public boolean offer(final E e) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return add(e);
        } finally {
            lock.unlock();
        }
    }

    public boolean offer(final E e, final long timeout, final TimeUnit unit) throws InterruptedException {
        final long nanos = unit.toNanos(timeout);
        return offer(e, nanos);
    }

    public boolean offer(final E e, final long nanos) throws InterruptedException {
        long n = nanos;
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            while (!add(e)) {
                if (n <= 0) {
                    return false;
                }
                n = notFull.awaitNanos(n);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    public List<E> swap() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return doSwap();
        } finally {
            lock.unlock();
        }
    }

    public List<E> swap(final long timeout, final TimeUnit unit) throws InterruptedException {
        final long nanos = unit.toNanos(timeout);
        return swap(nanos);
    }

    public List<E> swap(final long nanos) throws InterruptedException {
        long n = nanos;
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            List<E> x;
            while ((x = doSwap()) == null) {
                if (n <= 0)
                    return null;
                n = notEmpty.awaitNanos(n);
            }
            return x;
        } finally {
            lock.unlock();
        }
    }

    private boolean add(final E e) {
        if (addList.size() >= capacity) {
            return false;
        }
        addList.add(e);
        notEmpty.signal();
        return true;
    }

    private List<E> doSwap() {
        if (addList.size() == 0) {
            return null;
        }
        final List<E> oldAddList = addList;
        final List<E> oldRemoveList = removeList;

        addList = oldRemoveList;
        removeList = oldAddList;

        // Ensure the add list is cleared so we can insert new items.
        addList.clear();

        notFull.signal();
        return removeList;
    }
}
