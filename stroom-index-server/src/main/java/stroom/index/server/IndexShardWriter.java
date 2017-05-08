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
import stroom.cache.AbstractCacheBean.Destroyable;
import stroom.index.shared.Index;
import stroom.index.shared.IndexShard;

public interface IndexShardWriter extends Destroyable {
    void check();

    boolean open(boolean create);

    boolean isOpen();

    boolean isClosed();

    boolean isDeleted();

    boolean close();

    boolean flush();

    boolean delete();

    boolean deleteFromDisk();

    void updateIndex(Index index);

    boolean addDocument(Document document);

    boolean isFull();

    boolean isCorrupt();

    int getDocumentCount();

    Long getFileSize();

    IndexShard getIndexShard();

    String getPartition();

    IndexWriter getWriter();
}
