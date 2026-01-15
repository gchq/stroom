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

package stroom.planb.impl.db.trace;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.lmdb.stream.LmdbIterable;
import stroom.lmdb.stream.LmdbIterable.EntryConsumer;
import stroom.planb.impl.db.AbstractDb;
import stroom.planb.impl.db.HashClashCommitRunnable;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.shared.StateSettings;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.function.Function;

public class PathwaysDb {

    protected static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractDb.class);

    protected final PlanBEnv env;
    protected final ByteBuffers byteBuffers;
    protected final SimpleDb processingStatus;
    protected final SimpleDb pathways;

    private PathwaysDb(final PlanBEnv env,
                       final ByteBuffers byteBuffers) {
        this.env = env;
        this.byteBuffers = byteBuffers;

        // Read and validate that the schema is as expected.
        processingStatus = new SimpleDb(
                env,
                env.openDbi("processing-status", DbiFlags.MDB_CREATE),
                new PutFlags[]{});
        pathways = new SimpleDb(
                env,
                env.openDbi("pathways", DbiFlags.MDB_CREATE),
                new PutFlags[]{});
    }

    public SimpleDb getProcessingStatus() {
        return processingStatus;
    }

    public SimpleDb getPathways() {
        return pathways;
    }

    public LmdbWriter createWriter() {
        return env.createWriter();
    }

    public static PathwaysDb create(final Path path,
                                    final ByteBuffers byteBuffers,
                                    final boolean readOnly) {
        final StateSettings settings = new StateSettings.Builder().build();
        final HashClashCommitRunnable hashClashCommitRunnable = new HashClashCommitRunnable();
        final PlanBEnv env = new PlanBEnv(path,
                settings.getMaxStoreSize(),
                20,
                readOnly,
                hashClashCommitRunnable);
        try {
            return new PathwaysDb(
                    env,
                    byteBuffers);
        } catch (final RuntimeException e) {
            // Close the env if we get any exceptions to prevent them staying open.
            try {
                env.close();
            } catch (final Exception e2) {
                LOGGER.debug(LogUtil.message("message={}", e.getMessage()), e);
            }
            throw e;
        }
    }


    public static class SimpleDb {

        private final PlanBEnv env;
        private final Dbi<ByteBuffer> dbi;
        private final PutFlags[] putFlags;

        public SimpleDb(final PlanBEnv env,
                        final Dbi<ByteBuffer> dbi,
                        final PutFlags[] putFlags) {
            this.env = env;
            this.dbi = dbi;
            this.putFlags = putFlags;
        }

        public void insert(final LmdbWriter writer, final ByteBuffer keyByteBuffer, final ByteBuffer valueByteBuffer) {
            final Txn<ByteBuffer> writeTxn = writer.getWriteTxn();
            dbi.put(writeTxn, keyByteBuffer, valueByteBuffer, putFlags);
            writer.tryCommit();
        }

        public void iterate(final EntryConsumer consumer) {
            env.read(txn -> {
                iterate(txn, consumer);
                return null;
            });
        }

        public void iterate(final Txn<ByteBuffer> txn,
                            final EntryConsumer consumer) {
            LmdbIterable.iterate(txn, dbi, consumer);
        }

        public <R> R get(final Txn<ByteBuffer> txn,
                         final ByteBuffer keyByteBuffer,
                         final Function<ByteBuffer, R> byteBufferConsumer) {
            return byteBufferConsumer.apply(dbi.get(txn, keyByteBuffer));
        }

        public <R> R get(final ByteBuffer keyByteBuffer,
                         final Function<ByteBuffer, R> byteBufferConsumer) {
            return env.read(readTxn -> get(readTxn, keyByteBuffer, byteBufferConsumer));
        }
    }
}
