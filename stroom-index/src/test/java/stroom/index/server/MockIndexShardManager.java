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

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.util.spring.StroomSpringProfiles;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Profile(StroomSpringProfiles.TEST)
@Component("indexShardManager")
public class MockIndexShardManager implements IndexShardManager, Indexer {
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
    public void addDocument(final IndexShardKey key, final Document document) {
        try {
            get(key).addDocument(document);
        } catch (final IOException e) {
            throw new IndexException(e.getMessage(), e);
        }
    }

    private IndexShardWriter get(final IndexShardKey key) {
        return writers.computeIfAbsent(key, k -> new MockIndexShardWriter());
    }

    public Map<IndexShardKey, IndexShardWriter> getWriters() {
        return writers;
    }

    @Override
    public IndexWriter getWriter(final IndexShard indexShard) {
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

    public void clear() {
        writers.clear();
    }
}
