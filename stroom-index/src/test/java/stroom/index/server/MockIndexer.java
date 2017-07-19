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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.util.spring.StroomSpringProfiles;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

//@Profile(StroomSpringProfiles.TEST)
//@Component("indexer")
public class MockIndexer implements Indexer {
    private final IndexShardKeyCache indexShardKeyCache;
    private final IndexShardWriterCache indexShardWriterCache;

    MockIndexer() {
        this.indexShardKeyCache = new MockIndexShardKeyCache(new MockIndexShardService());
        this.indexShardWriterCache = new MockIndexShardWriterCache();
    }

//    @Inject
    MockIndexer(final IndexShardKeyCache indexShardKeyCache, final IndexShardWriterCache indexShardWriterCache) {
        this.indexShardKeyCache = indexShardKeyCache;
        this.indexShardWriterCache = indexShardWriterCache;
    }

    @Override
    public void addDocument(final IndexShardKey key, final Document document) {
        try {
            final IndexShard indexShard = indexShardKeyCache.getOrCreate(key);
            final IndexShardWriter indexShardWriter = indexShardWriterCache.getOrCreate(indexShard);
            indexShardWriter.addDocument(document);
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
