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

package stroom.index.server;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import stroom.cache.AbstractCacheBean.Destroyable;
import stroom.index.shared.IndexShard;
import stroom.util.spring.StroomSpringProfiles;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Profile(StroomSpringProfiles.TEST)
@Component("indexShardWriterCache")
public class MockIndexShardWriterCache implements IndexShardWriterCache {
    private final int maxDocumentCount;
    private final Map<IndexShard, IndexShardWriter> writers = new ConcurrentHashMap<>();

    MockIndexShardWriterCache() {
        this(Integer.MAX_VALUE);
    }

    MockIndexShardWriterCache(final int maxDocumentCount) {
        this.maxDocumentCount = maxDocumentCount;
    }

    @Override
    public IndexShardWriter getOrCreate(final IndexShard key) {
        return writers.computeIfAbsent(key, k -> new MockIndexShardWriter(maxDocumentCount));
    }

    @Override
    public IndexShardWriter getQuiet(final IndexShard key) {
        return writers.get(key);
    }

    @Override
    public void remove(final IndexShard key) {
        writers.remove(key);
    }

    @Override
    public void clear() {
        writers.values().forEach(Destroyable::destroy);
        writers.clear();
    }

    @Override
    public void flushAll() {
        writers.values().forEach(IndexShardWriter::flush);
    }

    public Map<IndexShard, IndexShardWriter> getWriters() {
        return writers;
    }
}
