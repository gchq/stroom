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
import org.apache.lucene.index.IndexableField;
import stroom.index.shared.Index;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class MockIndexShardWriter implements IndexShardWriter {
    private final List<Document> documents = new ArrayList<Document>();
    private static AtomicLong idGen = new AtomicLong();
    private IndexShardStatus status = IndexShardStatus.CLOSED;

    @Override
    public boolean open(final boolean create) {
        setStatus(IndexShardStatus.OPEN);
        return true;
    }

    @Override
    public IndexShard getIndexShard() {
        final IndexShard indexShard = new IndexShard();
        indexShard.setId(idGen.getAndIncrement());
        return indexShard;
    }

    @Override
    public void destroy() {
        close();
    }

    @Override
    public boolean close() {
        setStatus(IndexShardStatus.CLOSED);
        return true;
    }

    @Override
    public void check() {
    }

    @Override
    public boolean delete() {
        setStatus(IndexShardStatus.DELETED);
        return true;
    }

    @Override
    public boolean deleteFromDisk() {
        return false;
    }

    @Override
    public void addDocument(final Document document) {
        // Create a new document and copy the fields.
        final Document doc = new Document();
        for (final IndexableField field : document.getFields()) {
            doc.add(field);
        }
        documents.add(doc);
    }

    @Override
    public void updateIndex(final Index index) {
    }

    @Override
    public boolean flush() {
        return true;
    }

    public List<Document> getDocuments() {
        return documents;
    }

    @Override
    public int getDocumentCount() {
        return documents.size();
    }

    @Override
    public boolean isFull() {
        return false;
    }

    @Override
    public String getPartition() {
        return null;
    }

    @Override
    public IndexWriter getWriter() {
        return null;
    }

    @Override
    public IndexShardStatus getStatus() {
        return status;
    }

    @Override
    public void setStatus(final IndexShardStatus status) {
        this.status = status;
    }
}
