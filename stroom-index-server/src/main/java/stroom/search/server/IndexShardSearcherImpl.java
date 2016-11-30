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

package stroom.search.server;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.store.NoLockFactory;
import stroom.index.server.AbstractIndexShard;
import stroom.index.server.IndexShardUtil;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.util.logging.StroomLogger;

import java.io.File;
import java.io.IOException;

public class IndexShardSearcherImpl implements IndexShardSearcher {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(AbstractIndexShard.class);

    private final IndexShard indexShard;
    private final IndexWriter indexWriter;
    /**
     * Lucene stuff
     */
    private Directory directory;
    private IndexReader indexReader;

    public IndexShardSearcherImpl(final IndexShard indexShard) {
        this(indexShard, null);
    }

    public IndexShardSearcherImpl(final IndexShard indexShard, final IndexWriter indexWriter) {
        this.indexShard = indexShard;
        this.indexWriter = indexWriter;
    }

    @Override
    public IndexReader getReader() {
        if (indexReader == null) {
            throw new SearchException("Index is not open for searching");
        }
        return indexReader;
    }

    @Override
    public void open() {
        // First try and open the reader with the current writer if one is in
        // use. If a writer is available this will give us the benefit of being
        // able to search documents that have not yet been flushed to disk.
        if (indexReader == null && indexWriter != null) {
            try {
                indexReader = openWithWriter(indexWriter);
            } catch (final Exception e) {
                LOGGER.error(e.getMessage());
            }
        }

        // If we failed to open a reader with an existing writer then just try
        // and use the index shard directory.
        if (indexReader == null) {
            try {
                final File dir = IndexShardUtil.getIndexDir(indexShard);

                if (dir == null || !dir.isDirectory()) {
                    throw new SearchException("Index directory not found for searching: " + dir.getAbsolutePath());
                }

                directory = new NIOFSDirectory(dir, NoLockFactory.getNoLockFactory());

                indexReader = DirectoryReader.open(directory);

                // Check the document count in the index matches the DB.
                final int actualDocumentCount = indexReader.numDocs();
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
            } catch (final IOException e) {
                LOGGER.error(e.getMessage());
                throw SearchException.wrap(e);
            }
        }
    }

    private IndexReader openWithWriter(final IndexWriter indexWriter) throws IOException {
        final IndexReader indexReader = DirectoryReader.open(indexWriter, false);

        // Check the document count in the index matches the DB. We are using
        // the writer so chances are there is a mismatch.
        final int actualDocumentCount = indexReader.numDocs();
        if (indexShard.getDocumentCount() != actualDocumentCount) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("openWithWriter() - Mismatch document count.  Index says " + actualDocumentCount
                        + " DB says " + indexShard.getDocumentCount());
            }
        }

        return indexReader;
    }

    @Override
    public void close() {
        try {
            if (indexReader != null) {
                indexReader.close();
            }
        } catch (final IOException e) {
            LOGGER.error(e, e);
            throw SearchException.wrap(e);
        } finally {
            indexReader = null;

            try {
                if (directory != null) {
                    directory.close();
                }
            } catch (final IOException e) {
                LOGGER.error(e, e);
                throw SearchException.wrap(e);
            } finally {
                directory = null;
            }
        }
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("indexShard=");
        builder.append(indexShard);
        builder.append(", documentCount=");
        builder.append(indexShard.getDocumentCount());
        return builder.toString();
    }

    @Override
    public int getDocumentCount() {
        return indexShard.getDocumentCount();
    }

    @Override
    public IndexShard getIndexShard() {
        return indexShard;
    }
}
