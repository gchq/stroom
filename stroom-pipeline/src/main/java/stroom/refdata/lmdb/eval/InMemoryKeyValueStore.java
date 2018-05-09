/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.refdata.lmdb.eval;

import com.google.common.base.Preconditions;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryKeyValueStore implements KeyValueStore {

    private final ConcurrentMap<String, String> map;

    public InMemoryKeyValueStore() {
        map = new ConcurrentHashMap<>();
    }

    @Override
    public void put(final String key, final String value) {
        Preconditions.checkNotNull(key);
        Preconditions.checkNotNull(value);
        map.put(key, value);
    }

    @Override
    public void putBatch(final List<Map.Entry<String, String>> entries) {
        Preconditions.checkNotNull(entries);

        entries.forEach(entry -> {
            String key = entry.getKey();
            String value = entry.getValue();
            Preconditions.checkNotNull(key);
            Preconditions.checkNotNull(value);
            map.put(key, value);
        });
    }

    @Override
    public Optional<String> get(final String key) {
        Preconditions.checkNotNull(key);
        return Optional.ofNullable(map.get(key));
    }

    @Override
    public Optional<String> getWithTxn(String key) {
        //no concept of a txn here
        return get(key);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public void close() throws Exception {
        //do nothing
    }
}
