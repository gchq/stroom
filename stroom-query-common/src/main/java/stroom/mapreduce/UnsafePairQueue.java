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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class UnsafePairQueue<K, V> implements PairQueue<K, V> {
    private static final long serialVersionUID = 3205692727588879153L;

    private List<Pair<K, V>> queue = new ArrayList<>();

    public UnsafePairQueue() {
    }

    @Override
    public void collect(final K key, final V value) {
        final Pair<K, V> pair = new Pair<>(key, value);
        queue.add(pair);
    }

    @Override
    public Iterator<Pair<K, V>> iterator() {
        return queue.iterator();
    }

    public int size() {
        return queue.size();
    }
}
