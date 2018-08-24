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

package stroom.index;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexableField;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MockIndexShardWriter implements IndexShardWriter {
    //    private final IndexShardManager indexShardManager;
    private final List<Document> documents = new ArrayList<>();

    private final IndexShardKey indexShardKey;
    private final long indexShardId;
    private final long creationTime;

    private final int maxDocumentCount;
    private final AtomicInteger documentCount = new AtomicInteger();
    private volatile long lastUsedTime;

    MockIndexShardWriter(final IndexShardKey indexShardKey, final IndexShard indexShard, final int maxDocumentCount) {
//        this.indexShardManager = indexShardManager;
        this.indexShardKey = indexShardKey;
        this.indexShardId = indexShard.getId();
//        indexShardManager.setStatus(indexShardId, IndexShardStatus.OPEN);

        this.maxDocumentCount = maxDocumentCount;
        this.creationTime = System.currentTimeMillis();
        this.lastUsedTime = creationTime;
    }

    @Override
    public void addDocument(final Document document) {
        try {
            if (documentCount.getAndIncrement() >= maxDocumentCount) {
                throw new IndexException("Shard is full");
            }

            // Create a new document and copy the fields.
            final Document doc = new Document();
            for (final IndexableField field : document.getFields()) {
                doc.add(field);
            }
            documents.add(doc);

        } catch (final RuntimeException e) {
            documentCount.decrementAndGet();
            throw e;
        }
    }

    List<Document> getDocuments() {
        return documents;
    }

    @Override
    public IndexWriter getWriter() {
        return null;
    }

    @Override
    public int getDocumentCount() {
        return documents.size();
    }

    @Override
    public void updateIndexStructure(final IndexStructure indexStructure) {
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

    @Override
    public IndexShardKey getIndexShardKey() {
        return indexShardKey;
    }

    @Override
    public long getIndexShardId() {
        return indexShardId;
    }

    @Override
    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public long getLastUsedTime() {
        return lastUsedTime;
    }
}
