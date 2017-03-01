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

package stroom.mapreduce;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

public abstract class SimplePartitioner<K2, V2, K3, V3> implements Partitioner<K2, V2, K3, V3> {
    private final LinkedHashMap<K2, Collection<V2>> store;
    private final OutputCollector<K2, V2> storeCollector;
    private OutputCollector<K3, V3> outputCollector;

    public SimplePartitioner() {
        store = new LinkedHashMap<>(10, 0.75F);
        storeCollector = (key, value) -> {
            store.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        };
    }

    protected void collect(final K2 key, final V2 value) {
        storeCollector.collect(key, value);
    }

    @Override
    public void read(final Source<K2, V2> source) {
        for (final Pair<K2, V2> pair : source) {
            collect(pair.key, pair.value);
        }
    }

    @Override
    public void partition() {
        for (final Entry<K2, Collection<V2>> entry : store.entrySet()) {
            final K2 key = entry.getKey();
            final Collection<V2> value = entry.getValue();
            if (value != null) {
                // TODO: At this point we could asynchronously spawn
                // reduction tasks to perform reduction on the values. These
                // reduction tasks could be performed in multiple threads or
                // across multiple nodes.
                final Reducer<K2, V2, K3, V3> reducer = createReducer();
                reducer.reduce(key, value, outputCollector);
            }
        }
    }

    @Override
    public void setOutputCollector(final OutputCollector<K3, V3> outputCollector) {
        this.outputCollector = outputCollector;
    }

    protected abstract Reducer<K2, V2, K3, V3> createReducer();
}
