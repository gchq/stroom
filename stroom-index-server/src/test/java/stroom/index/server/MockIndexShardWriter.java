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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class MockIndexShardWriter implements IndexShardWriter {
    private static AtomicLong idGen = new AtomicLong();
    private final List<Document> documents = new ArrayList<Document>();

    @Override
    public boolean open(final boolean create) {
        return true;
    }

    @Override
    public boolean isOpen() {
        return false;
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
        return true;
    }

    @Override
    public void check() {
    }

    @Override
    public boolean delete() {
        return true;
    }

    @Override
    public boolean deleteFromDisk() {
        return false;
    }

    @Override
    public boolean addDocument(final Document document) {
        // Create a new document and copy the fields.
        final Document doc = new Document();
        for (final IndexableField field : document.getFields()) {
            doc.add(field);
        }
        documents.add(doc);
        return true;
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
    public Long getFileSize() {
        return null;
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
    public boolean isClosed() {
        return false;
    }

    @Override
    public boolean isDeleted() {
        return false;
    }

    @Override
    public boolean isCorrupt() {
        return false;
    }

    @Override
    public IndexWriter getWriter() {
        return null;
    }
}
