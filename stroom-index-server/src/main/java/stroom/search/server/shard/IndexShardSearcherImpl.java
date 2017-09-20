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

package stroom.search.server.shard;

import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.store.NoLockFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.index.server.IndexShardUtil;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.search.server.SearchException;
import stroom.util.io.FileUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class IndexShardSearcherImpl implements IndexShardSearcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexShardSearcherImpl.class);

    private final IndexShard indexShard;

    /**
     * Lucene stuff
     */
    private final Directory directory;
    private final IndexWriter indexWriter;
    private final SearcherManager searcherManager;

    public IndexShardSearcherImpl(final IndexShard indexShard) {
        this(indexShard, null);
    }

    IndexShardSearcherImpl(final IndexShard indexShard, final IndexWriter indexWriter) {
        this.indexShard = indexShard;
        this.indexWriter = indexWriter;

        Directory directory = null;
        SearcherManager searcherManager = null;

        try {
            // First try and open the reader with the current writer if one is in
            // use. If a writer is available this will give us the benefit of being
            // able to search documents that have not yet been flushed to disk.
            if (indexWriter != null) {
                try {
                    searcherManager = openWithWriter(indexWriter);
                } catch (final Exception e) {
                    LOGGER.error(e.getMessage());
                }
            }

            // If we failed to open a reader with an existing writer then just try
            // and use the index shard directory.
            if (searcherManager == null) {
                final Path dir = IndexShardUtil.getIndexPath(indexShard);

                if (!Files.isDirectory(dir)) {
                    throw new SearchException("Index directory not found for searching: " + FileUtil.getCanonicalPath(dir));
                }

                directory = new NIOFSDirectory(dir, NoLockFactory.INSTANCE);
//                indexReader = DirectoryReader.open(directory);
                searcherManager = new SearcherManager(directory, new SearcherFactory());

                // Check the document count in the index matches the DB.
                IndexSearcher indexSearcher = searcherManager.acquire();
                try {
                    final int actualDocumentCount = indexSearcher.getIndexReader().numDocs();
                    if (indexShard.getDocumentCount() != actualDocumentCount) {
                        // We should only worry about document mismatch if the shard
                        // is closed. However the shard
                        // may still have been written to since we got this
                        // reference.
                        if (IndexShardStatus.CLOSED.equals(indexShard.getStatus())) {
                            LOGGER.warn("open() - Mismatch document count.  Index says " + actualDocumentCount + " DB says "
                                    + indexShard.getDocumentCount());
                        } else if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("open() - Mismatch document count.  Index says " + actualDocumentCount
                                    + " DB says " + indexShard.getDocumentCount());
                        }
                    }
                } finally {
                    searcherManager.release(indexSearcher);
                }
            }
        } catch (final IOException e) {
            throw new SearchException(e.getMessage(), e);
        }

        this.directory = directory;
        this.searcherManager = searcherManager;
    }

    @Override
    public SearcherManager getSearcherManager() {
        return searcherManager;
    }

    private SearcherManager openWithWriter(final IndexWriter indexWriter) throws IOException {
        final SearcherManager searcherManager = new SearcherManager(indexWriter, false, new SearcherFactory());

        // Check the document count in the index matches the DB. We are using
        // the writer so chances are there is a mismatch.
        if (LOGGER.isDebugEnabled()) {
            IndexSearcher indexSearcher = searcherManager.acquire();
            try {
                final int actualDocumentCount = indexSearcher.getIndexReader().numDocs();
                if (indexShard.getDocumentCount() != actualDocumentCount) {
                    LOGGER.debug("openWithWriter() - Mismatch document count.  Index says " + actualDocumentCount
                            + " DB says " + indexShard.getDocumentCount());
                }
            } finally {
                searcherManager.release(indexSearcher);
            }
        }

        return searcherManager;
    }

    @Override
    public synchronized void destroy() {
        try {
            try {
                searcherManager.close();
            } finally {
                if (directory != null) {
                    directory.close();
                }
            }
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
            throw SearchException.wrap(e);
        }
    }

    @Override
    public String toString() {
        return "indexShard=" +
                indexShard +
                ", documentCount=" +
                indexShard.getDocumentCount();
    }

    @Override
    public IndexShard getIndexShard() {
        return indexShard;
    }

    public IndexWriter getWriter() {
        return indexWriter;
    }
}
