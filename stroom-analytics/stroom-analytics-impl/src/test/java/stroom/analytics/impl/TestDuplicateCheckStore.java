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

package stroom.analytics.impl;

import stroom.analytics.impl.DuplicateCheckStore.InfoKey;
import stroom.analytics.shared.DeleteDuplicateCheckRequest;
import stroom.analytics.shared.DuplicateCheckRow;
import stroom.analytics.shared.DuplicateCheckRows;
import stroom.analytics.shared.FindDuplicateCheckCriteria;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.lmdb.LmdbConfig;
import stroom.lmdb2.LmdbDb;
import stroom.lmdb2.LmdbEnv;
import stroom.lmdb2.LmdbEnvDir;
import stroom.query.common.v2.DuplicateCheckStoreConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.string.StringUtil;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestDuplicateCheckStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestDuplicateCheckStore.class);

    private static final String UUID = java.util.UUID.randomUUID().toString();

    private static final DuplicateCheckRow ROW_A = new DuplicateCheckRow(List.of("val1a", "val2a", "val3a"));
    private static final DuplicateCheckRow ROW_B = new DuplicateCheckRow(List.of("val1b", "val2b", "val3b"));
    private static final DuplicateCheckRow ROW_C = new DuplicateCheckRow(List.of("val1c", "val2c", "val3c"));

    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = UPPER.toLowerCase(Locale.ROOT);
    private static final String DIGITS = "0123456789";
    private static final String ALPHANUMERIC = UPPER + LOWER + DIGITS;
    private static final char[] CHARS = ALPHANUMERIC.toCharArray();

    private final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
    private final ByteBuffers byteBuffers = new ByteBuffers(byteBufferFactory);
    private final SecureRandom secureRandom = new SecureRandom();

    @Mock
    private DuplicateCheckDirs mockDuplicateCheckDirs;

    @Test
    void test(@TempDir final Path tempDir) {
        final LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(tempDir, true);
        Mockito.when(mockDuplicateCheckDirs.getDir(UUID))
                .thenReturn(lmdbEnvDir);

        final DuplicateCheckStoreConfig duplicateCheckStoreConfig = new DuplicateCheckStoreConfig();
        final DuplicateCheckRowSerde serde = new DuplicateCheckRowSerde(byteBufferFactory);

        try (final ExecutorService executorService = Executors.newSingleThreadExecutor()) {
            final DuplicateCheckStore duplicateCheckStore = new DuplicateCheckStore(
                    mockDuplicateCheckDirs,
                    byteBufferFactory,
                    byteBuffers,
                    duplicateCheckStoreConfig,
                    serde,
                    () -> executorService,
                    UUID);
            duplicateCheckStore.writeColumnNames(List.of("col1", "col2", "col3"));

            assertThat(duplicateCheckStore.tryInsert(ROW_A))
                    .isTrue();
            assertThat(duplicateCheckStore.tryInsert(ROW_B))
                    .isTrue();
            assertThat(duplicateCheckStore.tryInsert(ROW_B))
                    .isFalse();
            assertThat(duplicateCheckStore.tryInsert(ROW_C))
                    .isTrue();
            assertThat(duplicateCheckStore.tryInsert(ROW_A))
                    .isFalse();
            assertThat(duplicateCheckStore.tryInsert(ROW_C))
                    .isFalse();

            // Will block until all the above are loaded in
            duplicateCheckStore.flush();

            final DuplicateCheckRows rows = duplicateCheckStore.fetchData(new FindDuplicateCheckCriteria(
                    PageRequest.unlimited(),
                    null,
                    null,
                    null));

            final List<DuplicateCheckRow> values = rows.getResultPage().getValues();
            assertThat(values)
                    .containsExactlyInAnyOrder(
                            ROW_A,
                            ROW_B,
                            ROW_C);

            duplicateCheckStore.close();
        }
    }

    @Test
    void testLargeValue(@TempDir final Path tempDir) {
        final LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(tempDir, true);
        Mockito.when(mockDuplicateCheckDirs.getDir(UUID))
                .thenReturn(lmdbEnvDir);

        final DuplicateCheckStoreConfig duplicateCheckStoreConfig = new DuplicateCheckStoreConfig();
        final DuplicateCheckRowSerde serde = new DuplicateCheckRowSerde(byteBufferFactory);

        try (final ExecutorService executorService = Executors.newSingleThreadExecutor()) {
            final DuplicateCheckStore duplicateCheckStore = new DuplicateCheckStore(
                    mockDuplicateCheckDirs,
                    byteBufferFactory,
                    byteBuffers,
                    duplicateCheckStoreConfig,
                    serde,
                    () -> executorService,
                    UUID);
            duplicateCheckStore.writeColumnNames(List.of("col1", "col2", "col3"));

            // Make sure we can insert large values
            final int valLen = 1_000;
            final DuplicateCheckRow ROW_A = new DuplicateCheckRow(List.of(
                    makeValue(valLen),
                    makeValue(valLen * 10),
                    makeValue(valLen * 100)));

            assertThat(duplicateCheckStore.tryInsert(ROW_A))
                    .isTrue();

            // Will block until all the above are loaded in
            duplicateCheckStore.flush();

            final DuplicateCheckRows rows = duplicateCheckStore.fetchData(new FindDuplicateCheckCriteria(
                    PageRequest.unlimited(),
                    null,
                    null,
                    null));

            final List<DuplicateCheckRow> values = rows.getResultPage().getValues();
            assertThat(values)
                    .containsExactlyInAnyOrder(ROW_A);

            duplicateCheckStore.close();
        }
    }

    @Test
    void testDeletes(@TempDir final Path tempDir) {
        final LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(tempDir, true);
        Mockito.when(mockDuplicateCheckDirs.getDir(UUID))
                .thenReturn(lmdbEnvDir);

        final DuplicateCheckStoreConfig duplicateCheckStoreConfig = new DuplicateCheckStoreConfig();
        final DuplicateCheckRowSerde serde = new DuplicateCheckRowSerde(byteBufferFactory);

        try (final ExecutorService executorService = Executors.newSingleThreadExecutor()) {
            final DuplicateCheckStore duplicateCheckStore = new DuplicateCheckStore(
                    mockDuplicateCheckDirs,
                    byteBufferFactory,
                    byteBuffers,
                    duplicateCheckStoreConfig,
                    serde,
                    () -> executorService,
                    UUID);
            duplicateCheckStore.writeColumnNames(List.of("col1", "col2", "col3"));

            assertThat(duplicateCheckStore.tryInsert(ROW_A))
                    .isTrue();
            assertThat(duplicateCheckStore.tryInsert(ROW_B))
                    .isTrue();
            assertThat(duplicateCheckStore.tryInsert(ROW_B))
                    .isFalse();
            assertThat(duplicateCheckStore.tryInsert(ROW_C))
                    .isTrue();
            assertThat(duplicateCheckStore.tryInsert(ROW_A))
                    .isFalse();
            assertThat(duplicateCheckStore.tryInsert(ROW_C))
                    .isFalse();

            // Will block until all the above are loaded in
            duplicateCheckStore.flush();

            assertThat(delete(duplicateCheckStore, List.of(ROW_A)))
                    .isTrue();
            assertThat(delete(duplicateCheckStore, List.of(ROW_A)))
                    .isTrue();
            assertThat(duplicateCheckStore.tryInsert(ROW_A))
                    .isTrue();

            assertThat(delete(duplicateCheckStore, List.of(ROW_B)))
                    .isTrue();
            assertThat(delete(duplicateCheckStore, List.of(ROW_B)))
                    .isTrue();
            assertThat(duplicateCheckStore.tryInsert(ROW_B))
                    .isTrue();

            duplicateCheckStore.flush();

            duplicateCheckStore.close();
        } catch (final Exception e) {
            LOGGER.error("Error", e);
        }
    }

    @Test
    void testHashClash(@TempDir final Path tempDir) {
        final LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(tempDir, true);
        Mockito.when(mockDuplicateCheckDirs.getDir(UUID))
                .thenReturn(lmdbEnvDir);

        final DuplicateCheckStoreConfig duplicateCheckStoreConfig = new DuplicateCheckStoreConfig();
        // Every value gets the same hash, so 100% hash clashes
        final DuplicateCheckRowSerde serde = new HashClashDupCheckRowSerde(byteBufferFactory);

        try (final ExecutorService executorService = Executors.newSingleThreadExecutor()) {
            final DuplicateCheckStore duplicateCheckStore = new DuplicateCheckStore(
                    mockDuplicateCheckDirs,
                    byteBufferFactory,
                    byteBuffers,
                    duplicateCheckStoreConfig,
                    serde,
                    () -> executorService,
                    UUID);
            duplicateCheckStore.writeColumnNames(List.of("col1", "col2", "col3"));

            assertThat(duplicateCheckStore.tryInsert(ROW_A))
                    .isTrue();
            assertThat(duplicateCheckStore.tryInsert(ROW_B))
                    .isTrue();
            assertThat(duplicateCheckStore.tryInsert(ROW_B))
                    .isFalse();
            assertThat(duplicateCheckStore.tryInsert(ROW_C))
                    .isTrue();
            assertThat(duplicateCheckStore.tryInsert(ROW_A))
                    .isFalse();
            assertThat(duplicateCheckStore.tryInsert(ROW_C))
                    .isFalse();

            // Will block until all the above are loaded in
            duplicateCheckStore.flush();

            final DuplicateCheckRows rows = duplicateCheckStore.fetchData(new FindDuplicateCheckCriteria(
                    PageRequest.unlimited(),
                    null,
                    null,
                    null));

            final List<DuplicateCheckRow> values = rows.getResultPage().getValues();
            assertThat(values)
                    .containsExactlyInAnyOrder(
                            ROW_A,
                            ROW_B,
                            ROW_C);

            duplicateCheckStore.close();
        }
    }

    @Test
    void testDeletesWithHashClash(@TempDir final Path tempDir) {
        final LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(tempDir, true);
        Mockito.when(mockDuplicateCheckDirs.getDir(UUID))
                .thenReturn(lmdbEnvDir);

        final DuplicateCheckStoreConfig duplicateCheckStoreConfig = new DuplicateCheckStoreConfig();
        // Every value gets the same hash, so 100% hash clashes
        final DuplicateCheckRowSerde serde = new HashClashDupCheckRowSerde(byteBufferFactory);

        try (final ExecutorService executorService = Executors.newSingleThreadExecutor()) {
            final DuplicateCheckStore duplicateCheckStore = new DuplicateCheckStore(
                    mockDuplicateCheckDirs,
                    byteBufferFactory,
                    byteBuffers,
                    duplicateCheckStoreConfig,
                    serde,
                    () -> executorService,
                    UUID);
            try {
                duplicateCheckStore.writeColumnNames(List.of("col1", "col2", "col3"));

                assertThat(duplicateCheckStore.tryInsert(ROW_A))
                        .isTrue();
                assertThat(duplicateCheckStore.tryInsert(ROW_B))
                        .isTrue();
                assertThat(duplicateCheckStore.tryInsert(ROW_B))
                        .isFalse();
                assertThat(duplicateCheckStore.tryInsert(ROW_C))
                        .isTrue();
                assertThat(duplicateCheckStore.tryInsert(ROW_A))
                        .isFalse();
                assertThat(duplicateCheckStore.tryInsert(ROW_C))
                        .isFalse();

                // Will block until all the above are loaded in
                duplicateCheckStore.flush();

                assertThat(delete(duplicateCheckStore, List.of(ROW_A)))
                        .isTrue();
                assertThat(delete(duplicateCheckStore, List.of(ROW_A)))
                        .isTrue();
                assertThat(duplicateCheckStore.tryInsert(ROW_A))
                        .isTrue();

                assertThat(delete(duplicateCheckStore, List.of(ROW_B)))
                        .isTrue();
                assertThat(delete(duplicateCheckStore, List.of(ROW_B)))
                        .isTrue();
                assertThat(duplicateCheckStore.tryInsert(ROW_B))
                        .isTrue();

                duplicateCheckStore.flush();

            } finally {
                duplicateCheckStore.close();
            }
        }
    }

    @Test
    void testColumnChange(@TempDir final Path tempDir) {
        final LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(tempDir, true);
        Mockito.when(mockDuplicateCheckDirs.getDir(UUID))
                .thenReturn(lmdbEnvDir);

        final DuplicateCheckStoreConfig duplicateCheckStoreConfig = new DuplicateCheckStoreConfig();
        final DuplicateCheckRowSerde serde = new DuplicateCheckRowSerde(byteBufferFactory);
        final ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("duplicate-check-transfer-%d")
                .build();

        try (final ExecutorService executorService = Executors.newSingleThreadExecutor(threadFactory)) {
            DuplicateCheckStore duplicateCheckStore = null;
            try {
                duplicateCheckStore = new DuplicateCheckStore(
                        mockDuplicateCheckDirs,
                        byteBufferFactory,
                        byteBuffers,
                        duplicateCheckStoreConfig,
                        serde,
                        () -> executorService,
                        UUID);

                duplicateCheckStore.writeColumnNames(List.of("favouriteAnimal", "favouriteThing"));

                assertThat(duplicateCheckStore.tryInsert(DuplicateCheckRow.of("lamb", "rolex")))
                        .isTrue();
                assertThat(duplicateCheckStore.tryInsert(DuplicateCheckRow.of("cat", "bed")))
                        .isTrue();
                duplicateCheckStore.flush();

                assertThat(duplicateCheckStore.size())
                        .isEqualTo(2);

                assertThat(duplicateCheckStore.tryInsert(DuplicateCheckRow.of("lamb", "rolex")))
                        .isFalse();
                assertThat(duplicateCheckStore.tryInsert(DuplicateCheckRow.of("cat", "bed")))
                        .isFalse();
                duplicateCheckStore.flush();

                assertThat(duplicateCheckStore.size())
                        .isEqualTo(2);

                duplicateCheckStore.writeColumnNames(List.of("favouriteFood", "favouriteThing"));
                duplicateCheckStore.flush();

                // Column change clears out all the data
                assertThat(duplicateCheckStore.size())
                        .isEqualTo(0);

                assertThat(duplicateCheckStore.tryInsert(DuplicateCheckRow.of("lamb", "rolex")))
                        .isTrue();
                duplicateCheckStore.flush();

                assertThat(duplicateCheckStore.size())
                        .isEqualTo(1);

                // Will block until all the above are loaded in
                duplicateCheckStore.flush();

                final DuplicateCheckRows rows = duplicateCheckStore.fetchData(new FindDuplicateCheckCriteria(
                        PageRequest.unlimited(),
                        null,
                        null,
                        null));

                final List<DuplicateCheckRow> values = rows.getResultPage().getValues();

                LOGGER.debug("values:\n{}", values.stream()
                        .map(duplicateCheckRow -> String.join(", ", duplicateCheckRow.getValues()))
                        .collect(Collectors.joining("\n")));

                duplicateCheckStore.close();
            } catch (final Throwable e) {
                NullSafe.consume(duplicateCheckStore, dupCheckStore -> {
                    dupCheckStore.flush();
                    dupCheckStore.close();
                });
                throw e;
            }
        }
    }

    @Test
    void testOldVersion(@TempDir final Path tempDir) {
        final LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(tempDir, true);
        Mockito.when(mockDuplicateCheckDirs.getDir(UUID))
                .thenReturn(lmdbEnvDir);

        final DuplicateCheckStoreConfig duplicateCheckStoreConfig = new DuplicateCheckStoreConfig();
        final DuplicateCheckRowSerde serde = new DuplicateCheckRowSerde(byteBufferFactory);

        try (final ExecutorService executorService = Executors.newSingleThreadExecutor()) {
            DuplicateCheckStore duplicateCheckStore = null;
            try {
                // This will create the env
                duplicateCheckStore = new DuplicateCheckStore(
                        mockDuplicateCheckDirs,
                        byteBufferFactory,
                        byteBuffers,
                        duplicateCheckStoreConfig,
                        serde,
                        () -> executorService,
                        UUID);
                duplicateCheckStore.writeColumnNames(List.of("foo", "bar"));
                duplicateCheckStore.flush();
                assertThat(duplicateCheckStore.fetchData(new FindDuplicateCheckCriteria())
                        .getColumnNames())
                        .containsExactly("foo", "bar");
                duplicateCheckStore.close();

                // Remove the version entry from the info db
                LmdbEnv lmdbEnv = createEnv(duplicateCheckStoreConfig.getLmdbConfig(), lmdbEnvDir);
                final LmdbDb infoDb = lmdbEnv.openDb(DuplicateCheckStore.INFO_DB_NAME);
                lmdbEnv.write(writeTxn -> {
                    infoDb.delete(writeTxn, InfoKey.SCHEMA_VERSION.getByteBuffer());
                    writeTxn.commit();
                });
                lmdbEnv.close();

                // Now re-create the store which should delete and re-create the env
                duplicateCheckStore = new DuplicateCheckStore(
                        mockDuplicateCheckDirs,
                        byteBufferFactory,
                        byteBuffers,
                        duplicateCheckStoreConfig,
                        serde,
                        () -> executorService,
                        UUID);

                // New env so no cols
                assertThat(duplicateCheckStore.fetchData(new FindDuplicateCheckCriteria())
                        .getColumnNames())
                        .isEmpty();

                lmdbEnv = createEnv(duplicateCheckStoreConfig.getLmdbConfig(), lmdbEnvDir);
                final LmdbDb infoDb2 = lmdbEnv.openDb(DuplicateCheckStore.INFO_DB_NAME);
                lmdbEnv.read(readTxn -> {
                    final ByteBuffer byteBuffer = infoDb2.get(readTxn, InfoKey.SCHEMA_VERSION.getByteBuffer());
                    final int version = byteBuffer.getInt();
                    assertThat(version)
                            .isEqualTo(DuplicateCheckStore.CURRENT_SCHEMA_VERSION);
                });
                lmdbEnv.close();
                duplicateCheckStore.flush();
                duplicateCheckStore.close();
            } catch (final Throwable e) {
                NullSafe.consume(duplicateCheckStore, dupCheckStore -> {
                    dupCheckStore.flush();
                    dupCheckStore.close();
                });
                throw e;
            }
        }
    }

    @Test
    void testValidVersion(@TempDir final Path tempDir) {
        final LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(tempDir, true);
        Mockito.when(mockDuplicateCheckDirs.getDir(UUID))
                .thenReturn(lmdbEnvDir);

        final DuplicateCheckStoreConfig duplicateCheckStoreConfig = new DuplicateCheckStoreConfig();
        final DuplicateCheckRowSerde serde = new DuplicateCheckRowSerde(byteBufferFactory);

        try (final ExecutorService executorService = Executors.newSingleThreadExecutor()) {
            DuplicateCheckStore duplicateCheckStore = null;
            try {
                // This will create the env with the right schma version
                duplicateCheckStore = new DuplicateCheckStore(
                        mockDuplicateCheckDirs,
                        byteBufferFactory,
                        byteBuffers,
                        duplicateCheckStoreConfig,
                        serde,
                        () -> executorService,
                        UUID);
                // Add some cols
                duplicateCheckStore.writeColumnNames(List.of("foo", "bar"));
                duplicateCheckStore.flush();
                assertThat(duplicateCheckStore.fetchData(new FindDuplicateCheckCriteria())
                        .getColumnNames())
                        .containsExactly("foo", "bar");
                duplicateCheckStore.close();

                // Now re-open the store
                duplicateCheckStore = new DuplicateCheckStore(
                        mockDuplicateCheckDirs,
                        byteBufferFactory,
                        byteBuffers,
                        duplicateCheckStoreConfig,
                        serde,
                        () -> executorService,
                        UUID);

                // Cols still there
                assertThat(duplicateCheckStore.fetchData(new FindDuplicateCheckCriteria())
                        .getColumnNames())
                        .containsExactly("foo", "bar");

                duplicateCheckStore.flush();
                duplicateCheckStore.close();
            } catch (final Throwable e) {
                NullSafe.consume(duplicateCheckStore, dupCheckStore -> {
                    dupCheckStore.flush();
                    dupCheckStore.close();
                });
                throw e;
            }
        }
    }

    private LmdbEnv createEnv(final LmdbConfig lmdbConfig, final LmdbEnvDir lmdbEnvDir) {
        try {
            final LmdbEnv lmdbEnv = LmdbEnv
                    .builder()
                    .config(lmdbConfig)
                    .lmdbEnvDir(lmdbEnvDir)
                    .maxDbs(DuplicateCheckStore.MAX_DBS)
                    .maxReaders(DuplicateCheckStore.MAX_READERS)
                    .addEnvFlag(DuplicateCheckStore.ENV_FLAGS)
                    .build();
            LOGGER.debug("Created LmdbEnv in {} with config {}", lmdbEnvDir, lmdbConfig);
            return lmdbEnv;
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message("Error creating/opening LMDB Env in {} with config {} - ",
                    lmdbEnvDir, lmdbConfig, LogUtil.exceptionMessage(e)), e);
        }
    }


    private boolean delete(final DuplicateCheckStore duplicateCheckStore,
                           final List<DuplicateCheckRow> rows) {
        final DeleteDuplicateCheckRequest request = new DeleteDuplicateCheckRequest(null, rows);
        return duplicateCheckStore.delete(request);
    }

    private String makeValue(final int len) {
        return StringUtil.createRandomCode(secureRandom, len);
    }


    // --------------------------------------------------------------------------------


    private static class HashClashDupCheckRowSerde extends DuplicateCheckRowSerde {

        private static final long HASH = 123L;

        public HashClashDupCheckRowSerde(final ByteBufferFactory byteBufferFactory) {
            super(byteBufferFactory);
        }

        @Override
        protected long createHash(final byte[] bytes) {
            // Always the same hash no matter the value
            return HASH;
        }
    }
}
