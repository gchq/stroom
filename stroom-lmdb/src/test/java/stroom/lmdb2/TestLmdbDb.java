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

package stroom.lmdb2;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.ByteBufferPoolFactory;
import stroom.bytebuffer.PooledByteBufferPair;
import stroom.lmdb.LmdbConfig;
import stroom.lmdb.stream.LmdbEntry;
import stroom.lmdb.stream.LmdbKeyRange;
import stroom.util.io.ByteSize;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.lmdbjava.EnvFlags;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class TestLmdbDb {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestLmdbEnv.class);
    public static final String DB_NAME = "db";

    @Mock
    private LmdbConfig mockLmdbConfig;
    private final ByteBufferPool byteBufferPool = new ByteBufferPoolFactory().getByteBufferPool();

    @Test
    void testDropDb(@TempDir final Path tempDir) {
        Mockito.when(mockLmdbConfig.getMaxStoreSize())
                .thenReturn(ByteSize.ofKibibytes(512));
        Mockito.when(mockLmdbConfig.getMaxReaders())
                .thenReturn(1);

        try (final LmdbEnv lmdbEnv = createEnv(tempDir)) {
            final LmdbDb db = lmdbEnv.openDb(DB_NAME);
            putData(lmdbEnv, db);

            assertThat(getCount(lmdbEnv, db))
                    .isEqualTo(10);

            lmdbEnv.write(writeTxn -> {
                db.drop(writeTxn);
                writeTxn.commit();
            });

            assertThat(getCount(lmdbEnv, db))
                    .isEqualTo(0);

            putData(lmdbEnv, db);

            assertThat(getCount(lmdbEnv, db))
                    .isEqualTo(10);
        }
    }

    private void putData(final LmdbEnv lmdbEnv, final LmdbDb db) {
        lmdbEnv.write(writeTxn -> {
            try (final PooledByteBufferPair pair = byteBufferPool.getPooledBufferPair(
                    20, 20)) {
                final ByteBuffer keyBuf = pair.getKeyBuffer();
                final ByteBuffer valBuf = pair.getValueBuffer();

                for (int i = 0; i < 10; i++) {
                    keyBuf.clear();
                    valBuf.clear();
                    keyBuf.put(("key-" + i).getBytes(StandardCharsets.UTF_8));
                    keyBuf.flip();
                    valBuf.put(("val-" + i).getBytes(StandardCharsets.UTF_8));
                    valBuf.flip();
                    db.put(writeTxn, keyBuf, valBuf);
                }
                writeTxn.commit();
            }
        });
    }

    private static int getCount(final LmdbEnv lmdbEnv, final LmdbDb db) {
        final AtomicInteger counter = new AtomicInteger();
        lmdbEnv.read(readTxn -> {
            try (final Stream<LmdbEntry> stream  = db.stream(readTxn)) {
                counter.set((int) stream.count());
            }
        });
        return counter.get();
    }

    private LmdbEnv createEnv(final Path tempDir) {
        final LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(tempDir, true);
        try {
            final LmdbEnv lmdbEnv = LmdbEnv
                    .builder()
                    .config(mockLmdbConfig)
                    .lmdbEnvDir(lmdbEnvDir)
                    .maxDbs(2)
                    .addEnvFlag(EnvFlags.MDB_NOTLS)
                    .build();
            LOGGER.debug("Created LmdbEnv in {} with config {}", lmdbEnvDir, mockLmdbConfig);
            return lmdbEnv;
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message("Error creating/opening LMDB Env in {} with config {} - ",
                    lmdbEnvDir, mockLmdbConfig, LogUtil.exceptionMessage(e)), e);
        }
    }
}
