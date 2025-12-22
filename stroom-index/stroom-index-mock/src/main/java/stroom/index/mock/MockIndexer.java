/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.index.mock;

import stroom.index.impl.IndexDocument;
import stroom.index.impl.IndexShardCreator;
import stroom.index.impl.IndexShardWriter;
import stroom.index.impl.IndexShardWriterCache;
import stroom.index.impl.Indexer;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;

import jakarta.inject.Inject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MockIndexer implements Indexer {

    private final IndexShardCreator indexShardCreator;
    private final IndexShardWriterCache indexShardWriterCache;
    private final Map<IndexShardKey, IndexShard> indexShardMap = new ConcurrentHashMap<>();

    @Inject
    public MockIndexer(final IndexShardCreator indexShardCreator,
                       final IndexShardWriterCache indexShardWriterCache) {
        this.indexShardCreator = indexShardCreator;
        this.indexShardWriterCache = indexShardWriterCache;
    }

    @Override
    public void addDocument(final IndexShardKey key, final IndexDocument document) {
        final IndexShard indexShard = indexShardMap.computeIfAbsent(key, k ->
                indexShardCreator.createIndexShard(k, null));
        final IndexShardWriter indexShardWriter = indexShardWriterCache.getOrOpenWriter(indexShard.getId());
        indexShardWriter.addDocument(document);
    }
}
