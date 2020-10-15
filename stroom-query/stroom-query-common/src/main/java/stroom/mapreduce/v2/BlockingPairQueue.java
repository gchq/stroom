/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.mapreduce.v2;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;

public class BlockingPairQueue<K, V> implements PairQueue<K, V> {
    private static final long serialVersionUID = 3205692727588879153L;

    private final LinkedBlockingQueue<Pair<K, V>> queue;

    public BlockingPairQueue(final int capacity) {
        queue = new LinkedBlockingQueue<>(capacity);
    }

    @Override
    public void collect(final K key, final V value) {
        final Pair<K, V> pair = new Pair<>(key, value);

        try {
            queue.put(pair);
        } catch (final InterruptedException e) {
            // Continue to interrupt this thread.
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public Iterator<Pair<K, V>> iterator() {
        final List<Pair<K, V>> local = new ArrayList<>(queue.size());
        queue.drainTo(local);
        return local.iterator();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final BlockingPairQueue<?, ?> that = (BlockingPairQueue<?, ?>) o;
        return Objects.equals(queue, that.queue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queue);
    }
}
