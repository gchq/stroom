/*
 * Copyright 2025 Crown Copyright
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
import stroom.query.common.v2.ResultStoreLmdbConfig;
import stroom.util.io.ByteSize;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PageRequest;
import stroom.util.string.StringUtil;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

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

    private final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
    private final ByteBuffers byteBuffers = new ByteBuffers(byteBufferFactory);
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * As the production default config, but with a map size fit for what these tests write
     * (testLargeValue's ~111KB value is the biggest single item) rather than the production
     * default of 10GiB, which every open env reserves as address space.
     */
    private static DuplicateCheckStoreConfig createConfig() {
        return new DuplicateCheckStoreConfig(ResultStoreLmdbConfig.builder()
                .localDir("lmdb/duplicate_check")
                .maxStoreSize(ByteSize.ofMebibytes(10))
                .build());
    }

    /**
     * For use in a finally block so that a store is always closed and its writer thread can always
     * finish, whatever the test did. If the store did not close then the executor's
     * try-with-resources would await the still-running writer task forever, turning a test failure
     * into a test-JVM hang. Close failures are logged, not rethrown, so they can't mask the
     * assertion failure that caused them.
     */
    private static void closeQuietly(final DuplicateCheckStore store) {
        try {
            store.close();
        } catch (final RuntimeException e) {
            LOGGER.error("Error closing store: {}", e.getMessage(), e);
        }
    }

    @Mock
    private DuplicateCheckDirs mockDuplicateCheckDirs;

    @Test
    void test(@TempDir final Path tempDir) {
        final LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(tempDir, true);
        Mockito.when(mockDuplicateCheckDirs.getDir(UUID))
                .thenReturn(lmdbEnvDir);

        final DuplicateCheckStoreConfig duplicateCheckStoreConfig = createConfig();
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
            } finally {
                closeQuietly(duplicateCheckStore);
            }
        }
    }

    @Test
    void testLargeValue(@TempDir final Path tempDir) {
        final LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(tempDir, true);
        Mockito.when(mockDuplicateCheckDirs.getDir(UUID))
                .thenReturn(lmdbEnvDir);

        final DuplicateCheckStoreConfig duplicateCheckStoreConfig = createConfig();
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
            try {
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
            } finally {
                closeQuietly(duplicateCheckStore);
            }
        }
    }

    @Test
    void testDeletes(@TempDir final Path tempDir) {
        final LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(tempDir, true);
        Mockito.when(mockDuplicateCheckDirs.getDir(UUID))
                .thenReturn(lmdbEnvDir);

        final DuplicateCheckStoreConfig duplicateCheckStoreConfig = createConfig();
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
                // The test body deliberately has no catch (an earlier version swallowed all
                // exceptions, making it pass whatever the store did); closeQuietly only
                // swallows CLOSE failures so they can't mask a body failure.
                closeQuietly(duplicateCheckStore);
            }
        }
    }

    @Test
    void testHashClash(@TempDir final Path tempDir) {
        final LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(tempDir, true);
        Mockito.when(mockDuplicateCheckDirs.getDir(UUID))
                .thenReturn(lmdbEnvDir);

        final DuplicateCheckStoreConfig duplicateCheckStoreConfig = createConfig();
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
            } finally {
                closeQuietly(duplicateCheckStore);
            }
        }
    }

    @Test
    void testDeletesWithHashClash(@TempDir final Path tempDir) {
        final LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(tempDir, true);
        Mockito.when(mockDuplicateCheckDirs.getDir(UUID))
                .thenReturn(lmdbEnvDir);

        final DuplicateCheckStoreConfig duplicateCheckStoreConfig = createConfig();
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
                closeQuietly(duplicateCheckStore);
            }
        }
    }

    @Test
    void testColumnChange(@TempDir final Path tempDir) {
        final LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(tempDir, true);
        Mockito.when(mockDuplicateCheckDirs.getDir(UUID))
                .thenReturn(lmdbEnvDir);

        final DuplicateCheckStoreConfig duplicateCheckStoreConfig = createConfig();
        final DuplicateCheckRowSerde serde = new DuplicateCheckRowSerde(byteBufferFactory);
        final ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("duplicate-check-transfer-%d")
                .build();

        try (final ExecutorService executorService = Executors.newSingleThreadExecutor(threadFactory)) {
            final DuplicateCheckStore duplicateCheckStore = new DuplicateCheckStore(
                    mockDuplicateCheckDirs,
                    byteBufferFactory,
                    byteBuffers,
                    duplicateCheckStoreConfig,
                    serde,
                    () -> executorService,
                    UUID);
            try {
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
            } finally {
                closeQuietly(duplicateCheckStore);
            }
        }
    }

    @Test
    void testOldVersion(@TempDir final Path tempDir) {
        final LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(tempDir, true);
        Mockito.when(mockDuplicateCheckDirs.getDir(UUID))
                .thenReturn(lmdbEnvDir);

        final DuplicateCheckStoreConfig duplicateCheckStoreConfig = createConfig();
        final DuplicateCheckRowSerde serde = new DuplicateCheckRowSerde(byteBufferFactory);

        // Each store gets its own executor, closed (which awaits the store's writer thread) before
        // the same env dir is opened again. LMDB does not support opening the same env twice in one
        // process, so the previous opener must be completely finished with it, writer thread
        // included, before the next open. A single shared executor would also queue the second
        // store's writer task behind the first one, wedging the second store's constructor if the
        // first task did not exit.

        // Create the env, with cols.
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
                duplicateCheckStore.writeColumnNames(List.of("foo", "bar"));
                duplicateCheckStore.flush();
                assertThat(duplicateCheckStore.fetchData(new FindDuplicateCheckCriteria())
                        .getColumnNames())
                        .containsExactly("foo", "bar");
            } finally {
                closeQuietly(duplicateCheckStore);
            }
        }

        // Remove the version entry from the info db
        final LmdbEnv lmdbEnv = createEnv(duplicateCheckStoreConfig.getLmdbConfig(), lmdbEnvDir);
        try {
            final LmdbDb infoDb = lmdbEnv.openDb(DuplicateCheckStore.INFO_DB_NAME);
            lmdbEnv.write(writeTxn -> {
                infoDb.delete(writeTxn, InfoKey.SCHEMA_VERSION.getByteBuffer());
                writeTxn.commit();
            });
        } finally {
            // The env must not outlive this test's @TempDir whatever happens above
            lmdbEnv.close();
        }

        // Now re-create the store which should delete and re-create the env
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
                // New env so no cols
                assertThat(duplicateCheckStore.fetchData(new FindDuplicateCheckCriteria())
                        .getColumnNames())
                        .isEmpty();
            } finally {
                closeQuietly(duplicateCheckStore);
            }
        }

        // The re-created env should have the current schema version in it.
        // The int is extracted inside the txn (the buffer is LMDB owned) but asserted outside,
        // after the close, so an assertion failure can't leave the env open at @TempDir deletion.
        final LmdbEnv lmdbEnv2 = createEnv(duplicateCheckStoreConfig.getLmdbConfig(), lmdbEnvDir);
        final int version;
        try {
            final LmdbDb infoDb2 = lmdbEnv2.openDb(DuplicateCheckStore.INFO_DB_NAME);
            version = lmdbEnv2.readResult(readTxn ->
                    infoDb2.get(readTxn, InfoKey.SCHEMA_VERSION.getByteBuffer()).getInt());
        } finally {
            lmdbEnv2.close();
        }
        assertThat(version)
                .isEqualTo(DuplicateCheckStore.CURRENT_SCHEMA_VERSION);
    }

    @Test
    void testValidVersion(@TempDir final Path tempDir) {
        final LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(tempDir, true);
        Mockito.when(mockDuplicateCheckDirs.getDir(UUID))
                .thenReturn(lmdbEnvDir);

        final DuplicateCheckStoreConfig duplicateCheckStoreConfig = createConfig();
        final DuplicateCheckRowSerde serde = new DuplicateCheckRowSerde(byteBufferFactory);

        // Separate executor per store; see testOldVersion for why.

        // This will create the env with the right schema version, and some cols.
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
                duplicateCheckStore.writeColumnNames(List.of("foo", "bar"));
                duplicateCheckStore.flush();
                assertThat(duplicateCheckStore.fetchData(new FindDuplicateCheckCriteria())
                        .getColumnNames())
                        .containsExactly("foo", "bar");
            } finally {
                closeQuietly(duplicateCheckStore);
            }
        }

        // Now re-open the store
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
                // Cols still there
                assertThat(duplicateCheckStore.fetchData(new FindDuplicateCheckCriteria())
                        .getColumnNames())
                        .containsExactly("foo", "bar");
            } finally {
                closeQuietly(duplicateCheckStore);
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
