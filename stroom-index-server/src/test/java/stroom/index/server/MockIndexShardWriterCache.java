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
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.util.spring.StroomSpringProfiles;

import java.util.HashMap;
import java.util.Map;

@Profile(StroomSpringProfiles.TEST)
@Component("indexShardWriterCache")
public class MockIndexShardWriterCache implements IndexShardWriterCache {
    private final Map<IndexShardKey, IndexShardWriter> writers = new HashMap<>();

    @Override
    public Long findFlush(final FindIndexShardCriteria criteria) {
        return null;
    }

    @Override
    public Long findClose(final FindIndexShardCriteria criteria) {
        return null;
    }

    @Override
    public Long findDelete(final FindIndexShardCriteria criteria) {
        return null;
    }

    @Override
    public void clear() {
        writers.clear();
    }

    @Override
    public IndexShardWriter get(final IndexShardKey k) {
        IndexShardWriter writer = writers.get(k);
        if (writer == null) {
            writer = new MockIndexShardWriter();
            writers.put(k, writer);
        }

        return writer;
    }

    @Override
    public void remove(final IndexShardKey key) {
        final IndexShardWriter writer = writers.remove(key);
        if (writer != null) {
            writer.destroy();
        }
    }

    public Map<IndexShardKey, IndexShardWriter> getWriters() {
        return writers;
    }

    @Override
    public IndexShardWriter getWriter(final IndexShard indexShard) {
        return null;
    }

    @Override
    public void flushAll() {
        for (final IndexShardWriter writer : writers.values()) {
            writer.flush();
        }
    }

    @Override
    public void shutdown() {
    }
}
