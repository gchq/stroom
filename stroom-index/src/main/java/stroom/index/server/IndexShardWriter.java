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
import org.apache.lucene.store.AlreadyClosedException;
import stroom.index.shared.IndexShardKey;

import java.io.IOException;

public interface IndexShardWriter {
    IndexShardKey getIndexShardKey();

    long getIndexShardId();

    void addDocument(Document document) throws IOException, IndexException, AlreadyClosedException;

    void flush();

    void close();

    IndexWriter getWriter();

    int getDocumentCount();

    void updateIndexConfig(IndexConfig indexConfig);

    long getCreationTime();

    long getLastUsedTime();
}
