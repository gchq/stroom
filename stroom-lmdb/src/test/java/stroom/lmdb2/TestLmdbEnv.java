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

import stroom.bytebuffer.ByteBufferUtils;
import stroom.lmdb.LmdbConfig;
import stroom.lmdb.stream.LmdbEntry;
import stroom.util.io.ByteSize;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.EnvFlags;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestLmdbEnv {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestLmdbEnv.class);

    @TempDir
    private Path tempDir;

    @Mock
    private LmdbConfig mockLmdbConfig;

    @Test
    void testGetDbNames() {
        Mockito.when(mockLmdbConfig.getMaxStoreSize())
                .thenReturn(ByteSize.ofKibibytes(512));
        Mockito.when(mockLmdbConfig.getMaxReaders())
                .thenReturn(1);

        try (final LmdbEnv lmdbEnv = createEnv()) {
            assertThat(lmdbEnv.getDbNames())
                    .isEmpty();
            assertThat(lmdbEnv.hasDb("foo"))
                    .isFalse();

            lmdbEnv.openDb("foo");
            assertThat(lmdbEnv.getDbNames())
                    .containsExactlyInAnyOrder("foo");
            assertThat(lmdbEnv.hasDb("foo"))
                    .isTrue();

            // Un-named DB exists by default
            final LmdbDb unNamedDb = lmdbEnv.openDb(null, new DbiFlags[0]);
            lmdbEnv.read(readTxn -> {
                try (final Stream<LmdbEntry> stream = unNamedDb.stream(readTxn)) {
                    stream.forEach(entry -> {
                        final ByteBuffer key = entry.getKey();
                        final ByteBuffer val = entry.getVal();
                        LOGGER.info("key: {}, val:{}",
                                ByteBufferUtils.byteBufferInfo(key),
                                ByteBufferUtils.byteBufferInfo(val));
                    });
                }
            });
            assertThat(lmdbEnv.getDbNames())
                    .containsExactlyInAnyOrder("foo");
        }
    }

    private LmdbEnv createEnv() {
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
