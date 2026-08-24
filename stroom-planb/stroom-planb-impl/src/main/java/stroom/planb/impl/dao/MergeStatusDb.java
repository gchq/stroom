/*
 * Copyright 2026 Crown Copyright
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

package stroom.planb.impl.dao;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.lmdb.stream.LmdbIterable;
import stroom.lmdb.stream.LmdbIterable.EntryConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Tracks the merge status of sources merged into an additive store (histogram and metric stores), keyed by
 * the source's instance UUID, so that merging the same source twice does not double count. A source is
 * either {@code IN_PROGRESS}, with a cursor holding the raw bytes of the last source key included in a
 * commit, or {@code COMPLETE}. All status writes happen in the same transaction as the merged data they
 * describe, so the status is always exact. See docs/merge-idempotency-design.md.
 */
public class MergeStatusDb {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MergeStatusDb.class);

    private static final String NAME = "merge_status_db";
    private static final byte STATE_IN_PROGRESS = 0;
    private static final byte STATE_COMPLETE = 1;
    private static final int STATE_LENGTH = Byte.BYTES + Long.BYTES;

    private final Dbi<ByteBuffer> dbi;
    private final ByteBuffers byteBuffers;

    public MergeStatusDb(final PlanBEnv env,
                         final ByteBuffers byteBuffers) {
        this.dbi = env.openDbi(NAME, DbiFlags.MDB_CREATE);
        this.byteBuffers = byteBuffers;
    }

    /**
     * Start merging the source identified by sourceUuid, resuming from its recorded status if there is one.
     *
     * @param sourceUuid The instance UUID of the source being merged, or null for a legacy source created
     *                   before instance UUIDs, for which no status can be tracked.
     */
    public MergeTracker startMerge(final LmdbWriter writer, final String sourceUuid) {
        if (sourceUuid == null) {
            return new MergeTracker(writer, null, false, null);
        }
        final ByteBuffer value = useKey(sourceUuid, keyByteBuffer ->
                dbi.get(writer.getWriteTxn(), keyByteBuffer));
        if (value == null) {
            return new MergeTracker(writer, sourceUuid, false, null);
        }
        final ByteBuffer duplicate = value.duplicate();
        final byte state = duplicate.get();
        // Skip the timestamp.
        duplicate.getLong();
        if (state == STATE_COMPLETE) {
            return new MergeTracker(writer, sourceUuid, true, null);
        }
        final byte[] resumeKey = new byte[duplicate.remaining()];
        duplicate.get(resumeKey);
        LOGGER.info(() -> LogUtil.message("Resuming interrupted merge of source {}", sourceUuid));
        return new MergeTracker(writer, sourceUuid, false, resumeKey);
    }

    /**
     * Delete status records with a timestamp before deleteBefore. The caller must ensure that no replayable
     * copy of any source still exists, i.e. the staging store is drained and the doc's merge queue dir is
     * empty on disk, as a replayed source that finds no status record will be fully re-merged.
     *
     * @return The number of records deleted.
     */
    public long deleteOldStatus(final LmdbWriter writer, final Instant deleteBefore) {
        final List<byte[]> toDelete = new ArrayList<>();
        LmdbIterable.iterate(writer.getWriteTxn(), dbi, (key, val) -> {
            final ByteBuffer duplicate = val.duplicate();
            // Skip the state.
            duplicate.get();
            final Instant time = Instant.ofEpochMilli(duplicate.getLong());
            if (time.isBefore(deleteBefore)) {
                toDelete.add(ByteBufferUtils.toBytes(key));
            }
        });
        for (final byte[] key : toDelete) {
            byteBuffers.useBytes(key, keyByteBuffer -> {
                dbi.delete(writer.getWriteTxn(), keyByteBuffer);
            });
        }
        return toDelete.size();
    }

    private void putProgress(final Txn<ByteBuffer> txn, final String sourceUuid, final byte[] lastMergedKey) {
        put(txn, sourceUuid, STATE_IN_PROGRESS, lastMergedKey);
    }

    private void putComplete(final Txn<ByteBuffer> txn, final String sourceUuid) {
        put(txn, sourceUuid, STATE_COMPLETE, null);
    }

    private void put(final Txn<ByteBuffer> txn,
                     final String sourceUuid,
                     final byte state,
                     final byte[] cursor) {
        final int length = STATE_LENGTH + (cursor == null
                ? 0
                : cursor.length);
        useKey(sourceUuid, keyByteBuffer -> {
            byteBuffers.use(length, valueByteBuffer -> {
                valueByteBuffer.put(state);
                valueByteBuffer.putLong(Instant.now().toEpochMilli());
                if (cursor != null) {
                    valueByteBuffer.put(cursor);
                }
                valueByteBuffer.flip();
                dbi.put(txn, keyByteBuffer, valueByteBuffer);
            });
            return null;
        });
    }

    private <R> R useKey(final String sourceUuid, final Function<ByteBuffer, R> function) {
        return byteBuffers.useBytes(sourceUuid.getBytes(StandardCharsets.UTF_8), function);
    }

    private static boolean bytesEqual(final ByteBuffer byteBuffer, final byte[] bytes) {
        return byteBuffer.remaining() == bytes.length &&
               ByteBufferUtils.equals(byteBuffer, byteBuffer.position(), ByteBuffer.wrap(bytes), 0, bytes.length);
    }

    /**
     * Tracks the progress of one merge. Not thread safe; a merge is single threaded under the shard write
     * lock.
     */
    public class MergeTracker {

        private final LmdbWriter writer;
        private final String sourceUuid;
        private final boolean alreadyComplete;
        private final byte[] resumeKey;

        private boolean skipping;
        private byte[] lastMergedKey;

        private MergeTracker(final LmdbWriter writer,
                             final String sourceUuid,
                             final boolean alreadyComplete,
                             final byte[] resumeKey) {
            this.writer = writer;
            this.sourceUuid = sourceUuid;
            this.alreadyComplete = alreadyComplete;
            this.resumeKey = resumeKey;
            this.skipping = resumeKey != null;
        }

        /**
         * @return True if this source has already been fully merged, so the merge should be skipped
         * entirely. The caller still deletes the source dir as a successful merge would.
         */
        public boolean isAlreadyComplete() {
            return alreadyComplete;
        }

        /**
         * Wrap the per entry merge logic with progress tracking. On resume, entries up to and including the
         * recorded cursor are skipped; iteration order over the immutable source is identical on every
         * replay so the scan resumes exactly after the last committed entry. Each batch commit writes the
         * cursor in the same transaction, so target data and cursor can never disagree.
         */
        public EntryConsumer wrap(final EntryConsumer delegate) {
            if (sourceUuid == null) {
                // Legacy source with no instance UUID: no progress tracking is possible. The merge remains
                // a single transaction, aborted on failure, so it is still exact, just unbounded in size.
                return delegate;
            }
            return (key, val) -> {
                if (skipping) {
                    if (bytesEqual(key, resumeKey)) {
                        skipping = false;
                    }
                    return;
                }
                // Copy the key before the delegate potentially consumes the buffer.
                final byte[] keyCopy = ByteBufferUtils.toBytes(key);
                delegate.accept(key, val);
                lastMergedKey = keyCopy;
                writer.incrementChangeCount();
                if (writer.shouldCommit()) {
                    putProgress(writer.getWriteTxn(), sourceUuid, lastMergedKey);
                    writer.commit();
                }
            };
        }

        /**
         * Mark the source as fully merged. Written to the current transaction, which the caller commits
         * with the final batch of merged data, so completion and the data are atomic.
         */
        public void complete() {
            if (sourceUuid != null) {
                if (skipping) {
                    // The recorded cursor was never seen, so every entry was skipped. This should be
                    // impossible as sources are immutable, so the previously merged entries cannot be
                    // separated from unmerged ones. Mark complete rather than retry forever, but shout.
                    LOGGER.error(() -> LogUtil.message(
                            "Resume cursor not found in source {}. The source content appears to have " +
                            "changed since the interrupted merge. No further entries have been merged.",
                            sourceUuid));
                }
                putComplete(writer.getWriteTxn(), sourceUuid);
            }
        }
    }
}
