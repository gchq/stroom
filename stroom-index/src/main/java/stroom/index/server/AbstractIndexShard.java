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

import java.io.File;
import java.io.IOException;

import stroom.util.logging.StroomLogger;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.store.NoLockFactory;
import org.apache.lucene.store.SimpleFSLockFactory;

import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexShardService;

/**
 * Thread safe class used to govern access to a index shard. It is assumed that
 * the Pool returns these objects to writing threads.
 */
public abstract class AbstractIndexShard {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(AbstractIndexShard.class);

    /**
     * The Data Model Object that records our location.
     */
    private final IndexShardService service;
    private IndexShard indexShard;
    private final boolean readOnly;

    /**
     * Lucene stuff
     */
    private Directory directory;

    /**
     * Status Info
     */
    private long openTime;

    public AbstractIndexShard(final IndexShardService service, final IndexShard indexShard, boolean readOnly) {
        this.readOnly = readOnly;
        this.service = service;
        this.indexShard = indexShard;
        this.openTime = System.currentTimeMillis();
    }

    protected void open() throws IOException {
        if (directory == null) {
            final File dir = getIndexDir();

            try {
                if (readOnly) {
                    directory = new NIOFSDirectory(dir, NoLockFactory.getNoLockFactory());

                } else {
                    if (!dir.isDirectory() && !dir.mkdirs()) {
                        throw new IOException("getDirectory() - Failed to create directory " + dir);
                    }

                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Opening and locking index dir = " + dir.getAbsolutePath() + " exists "
                                + dir.isDirectory());
                    }

                    directory = new NIOFSDirectory(dir, new SimpleFSLockFactory());

                    // We have opened the index so update the DB object.
                    if (directory != null) {
                        indexShard.setStatus(IndexShardStatus.OPEN);
                        indexShard = service.save(indexShard);
                    }
                }
            } finally {
                if (directory == null) {
                    LOGGER.error("Failed to open: " + dir.getAbsolutePath());
                }
            }
        }
    }

    protected void close() throws IOException {
        if (directory != null) {
            directory.close();
            directory = null;
        }
    }

    protected File getIndexDir() {
        return IndexShardUtil.getIndexDir(indexShard);
    }

    protected Directory getDirectory() {
        return directory;
    }

    public long getOpenTime() {
        return openTime;
    }

    public IndexShard getIndexShard() {
        return indexShard;
    }

    /**
     * Allow the entity to be updated .... but you can't change the version
     *
     * @param indexShard
     */
    public void setIndexShard(final IndexShard indexShard) {
        if (!this.getIndexShard().getIndex().equals(indexShard.getIndex())) {
            throw new RuntimeException("Only able to update a index shard with the same index");
        }
        this.indexShard = indexShard;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("indexShard=");
        builder.append(indexShard);
        return super.toString();
    }
}
