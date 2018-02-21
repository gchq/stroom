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
import stroom.index.shared.IndexShardKey;

public class MockIndexer implements Indexer {
    private final IndexShardWriterCache indexShardWriterCache;

    MockIndexer() {
        this.indexShardWriterCache = new MockIndexShardWriterCache();
    }

    MockIndexer(final IndexShardWriterCache indexShardWriterCache) {
        this.indexShardWriterCache = indexShardWriterCache;
    }

    @Override
    public void addDocument(final IndexShardKey key, final Document document) {
        try {
            final IndexShardWriter indexShardWriter = indexShardWriterCache.getWriterByShardKey(key);
            indexShardWriter.addDocument(document);
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
