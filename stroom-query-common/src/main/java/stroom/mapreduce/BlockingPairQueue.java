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

package stroom.mapreduce;

import stroom.util.shared.HasTerminate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class BlockingPairQueue<K, V> implements PairQueue<K, V> {
    private static final long serialVersionUID = 3205692727588879153L;

    private static final int MAX_SIZE = 1000000;

    private final HasTerminate monitor;
    private final AtomicInteger size = new AtomicInteger();
    private final ReentrantLock lock = new ReentrantLock();
    private volatile List<Pair<K, V>> queue;
    private transient Iterator<Pair<K, V>> emptyIter;

    public BlockingPairQueue(final HasTerminate monitor) {
        this.monitor = monitor;
    }

    @Override
    public void collect(final K key, final V value) {
        final Pair<K, V> pair = new Pair<>(key, value);
        while (!offer(pair) && (monitor == null || !monitor.isTerminated())) {
            try {
                Thread.sleep(100);
            } catch (final InterruptedException e) {
                // Ignore.
            }
        }
    }

    private boolean offer(final Pair<K, V> pair) {
        boolean success = false;
        lock.lock();
        try {
            // We won't allow more than max results to go into the queue.
            if (size.get() >= MAX_SIZE) {
                success = false;

            } else {
                if (queue == null) {
                    queue = new ArrayList<>();
                }

                queue.add(pair);
                size.incrementAndGet();
                success = true;
            }
        } finally {
            lock.unlock();
        }
        return success;
    }

    @Override
    public Iterator<Pair<K, V>> iterator() {
        List<Pair<K, V>> local = null;
        lock.lock();
        try {
            local = queue;
            queue = null;
            size.set(0);
        } finally {
            lock.unlock();
        }

        if (local != null) {
            return local.iterator();
        } else {
            return getEmptyIter();
        }
    }

    private Iterator<Pair<K, V>> getEmptyIter() {
        if (emptyIter == null) {
            emptyIter = new Iterator<Pair<K, V>>() {
                @Override
                public boolean hasNext() {
                    return false;
                }

                @Override
                public Pair<K, V> next() {
                    return null;
                }

                @Override
                public void remove() {
                }
            };
        }
        return emptyIter;
    }
}
