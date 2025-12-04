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
import stroom.index.impl.IndexShardWriter;
import stroom.index.shared.IndexException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MockIndexShardWriter implements IndexShardWriter {

    private final List<IndexDocument> documents = new ArrayList<>();

    private final long indexShardId;
    private final long creationTime;

    private final int maxDocumentCount;
    private final AtomicInteger documentCount = new AtomicInteger();

    MockIndexShardWriter(final long indexShardId, final int maxDocumentCount) {
        this.indexShardId = indexShardId;
        this.maxDocumentCount = maxDocumentCount;
        this.creationTime = System.currentTimeMillis();
    }

    @Override
    public void addDocument(final IndexDocument document) throws IndexException {
        try {
            if (documentCount.getAndIncrement() >= maxDocumentCount) {
                throw new IndexException("Shard is full");
            }

            // Create a new document and copy the fields.
            documents.add(document);

        } catch (final RuntimeException e) {
            documentCount.decrementAndGet();
            throw e;
        }
    }

    public List<IndexDocument> getDocuments() {
        return documents;
    }

    @Override
    public int getDocumentCount() {
        return documents.size();
    }

    @Override
    public void setMaxDocumentCount(final int maxDocumentCount) {

    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

    @Override
    public long getIndexShardId() {
        return indexShardId;
    }

    @Override
    public long getCreationTime() {
        return creationTime;
    }
}
