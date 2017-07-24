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
import org.apache.lucene.store.AlreadyClosedException;
import org.springframework.stereotype.Component;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexShardKey;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import java.util.concurrent.locks.Lock;

/**
 * Pool API into open index shards.
 */
@Component("indexer")
public class IndexerImpl implements Indexer {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexerImpl.class);
    private static final int MAX_ATTEMPTS = 10000;

    private final IndexShardKeyCache indexShardKeyCache;
    private final IndexShardWriterCache indexShardWriterCache;
    private final IndexShardManager indexShardManager;

    private final StripedLock keyLocks = new StripedLock();

    @Inject
    IndexerImpl(final IndexShardKeyCache indexShardKeyCache,
                final IndexShardWriterCache indexShardWriterCache,
                final IndexShardManager indexShardManager) {
        this.indexShardKeyCache = indexShardKeyCache;
        this.indexShardWriterCache = indexShardWriterCache;
        this.indexShardManager = indexShardManager;
    }

    @Override
    public void addDocument(final IndexShardKey indexShardKey, final Document document) {
        if (document != null) {
            // Try and add the document silently without locking.
            boolean success = false;
            try {
                final IndexShard indexShard = indexShardKeyCache.getOrCreate(indexShardKey);
                final IndexShardWriter indexShardWriter = indexShardWriterCache.getOrCreate(indexShard.getId());
                indexShardWriter.addDocument(document);
                success = true;
            } catch (final Throwable t) {
                LOGGER.trace(t::getMessage, t);
            }

            // Attempt a few more times under lock.
            for (int attempt = 0; !success && attempt < MAX_ATTEMPTS; attempt++) {
                // If we failed then try under lock to make sure we get a new writer.
                final Lock lock = keyLocks.getLockForKey(indexShardKey);
                lock.lock();
                try {
                    // Ask the cache for the current one (it might have been changed by another thread) and try again.
                    final IndexShard indexShard = indexShardKeyCache.getOrCreate(indexShardKey);
                    final IndexShardWriter indexShardWriter = indexShardWriterCache.getOrCreate(indexShard.getId());
                    success = addDocument(indexShard, indexShardWriter, document);

                    if (!success) {
                        LOGGER.info(() -> "Removing key{" + indexShardKey + "} shard{" + indexShard + "} writer{" + indexShardWriter + "}");

                        // Ensure the writer is destroyed as we cannot guarantee that cache removal will perform
                        // immediate destruction.
                        indexShardWriter.destroy();

                        // Remove the writer from the cache.
                        indexShardWriterCache.remove(indexShard.getId());

                        // Failed to add it so remove this shard key from the cache and try to get another one.
                        indexShardKeyCache.remove(indexShardKey);
                    }

                } catch (final Throwable t) {
                    LOGGER.trace(t::getMessage, t);

                    // If we've already tried once already then give up.
                    if (attempt > 0) {
                        throw t;
                    }
                } finally {
                    lock.unlock();
                }
            }

            // One final try that will throw an index exception if needed.
            if (!success) {
                try {
                    final IndexShard indexShard = indexShardKeyCache.getOrCreate(indexShardKey);
                    final IndexShardWriter indexShardWriter = indexShardWriterCache.getOrCreate(indexShard.getId());
                    indexShardWriter.addDocument(document);
                } catch (final IndexException e) {
                    throw e;
                } catch (final Throwable e) {
                    throw new IndexException(e.getMessage(), e);
                }
            }
        }
    }

    private boolean addDocument(final IndexShard indexShard, final IndexShardWriter indexShardWriter, final Document document) {
        boolean success = false;
        try {
            indexShardWriter.addDocument(document);
            success = true;
        } catch (final ShardFullException e) {
            LOGGER.debug(e::getMessage, e);

        } catch (final AlreadyClosedException | IndexException e) {
            LOGGER.trace(e::getMessage, e);

        } catch (final Throwable t) {
            LOGGER.error(t::getMessage, t);

            // Mark the shard as corrupt as this should be the
            // only reason we can't add a document.
            if (indexShardManager != null) {
                indexShardManager.setStatus(indexShard.getId(), IndexShardStatus.CORRUPT);
            }
        }

        return success;
    }
}