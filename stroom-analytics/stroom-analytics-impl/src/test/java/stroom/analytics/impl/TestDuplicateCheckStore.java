package stroom.analytics.impl;

import stroom.analytics.shared.DeleteDuplicateCheckRequest;
import stroom.analytics.shared.DuplicateCheckRow;
import stroom.analytics.shared.DuplicateCheckRows;
import stroom.analytics.shared.FindDuplicateCheckCriteria;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.lmdb2.LmdbEnvDir;
import stroom.query.common.v2.DuplicateCheckStoreConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PageRequest;
import stroom.util.string.StringUtil;

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
    void test(@TempDir Path tempDir) {
        LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(tempDir, true);
        Mockito.when(mockDuplicateCheckDirs.getDir(UUID))
                .thenReturn(lmdbEnvDir);

        final DuplicateCheckStoreConfig duplicateCheckStoreConfig = new DuplicateCheckStoreConfig();
        final DuplicateCheckRowSerde serde = new DuplicateCheckRowSerde(byteBufferFactory);

        try (ExecutorService executorService = Executors.newSingleThreadExecutor()) {
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
    void testLargeValue(@TempDir Path tempDir) {
        LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(tempDir, true);
        Mockito.when(mockDuplicateCheckDirs.getDir(UUID))
                .thenReturn(lmdbEnvDir);

        final DuplicateCheckStoreConfig duplicateCheckStoreConfig = new DuplicateCheckStoreConfig();
        final DuplicateCheckRowSerde serde = new DuplicateCheckRowSerde(byteBufferFactory);

        try (ExecutorService executorService = Executors.newSingleThreadExecutor()) {
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
    void testDeletes(@TempDir Path tempDir) {
        LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(tempDir, true);
        Mockito.when(mockDuplicateCheckDirs.getDir(UUID))
                .thenReturn(lmdbEnvDir);

        final DuplicateCheckStoreConfig duplicateCheckStoreConfig = new DuplicateCheckStoreConfig();
        final DuplicateCheckRowSerde serde = new DuplicateCheckRowSerde(byteBufferFactory);

        try (ExecutorService executorService = Executors.newSingleThreadExecutor()) {
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
        } catch (Exception e) {
            LOGGER.error("Error", e);
        }
    }

    @Test
    void testHashClash(@TempDir Path tempDir) {
        LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(tempDir, true);
        Mockito.when(mockDuplicateCheckDirs.getDir(UUID))
                .thenReturn(lmdbEnvDir);

        final DuplicateCheckStoreConfig duplicateCheckStoreConfig = new DuplicateCheckStoreConfig();
        // Every value gets the same hash, so 100% hash clashes
        final DuplicateCheckRowSerde serde = new HashClashDupCheckRowSerde(byteBufferFactory);

        try (ExecutorService executorService = Executors.newSingleThreadExecutor()) {
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
    void testDeletesWithHashClash(@TempDir Path tempDir) {
        LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(tempDir, true);
        Mockito.when(mockDuplicateCheckDirs.getDir(UUID))
                .thenReturn(lmdbEnvDir);

        final DuplicateCheckStoreConfig duplicateCheckStoreConfig = new DuplicateCheckStoreConfig();
        // Every value gets the same hash, so 100% hash clashes
        final DuplicateCheckRowSerde serde = new HashClashDupCheckRowSerde(byteBufferFactory);

        try (ExecutorService executorService = Executors.newSingleThreadExecutor()) {
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
        }
    }

    private boolean delete(final DuplicateCheckStore duplicateCheckStore,
                           final List<DuplicateCheckRow> rows) {
        final DeleteDuplicateCheckRequest request = new DeleteDuplicateCheckRequest(null, rows);
        return duplicateCheckStore.delete(request);
    }

    private String makeValue(int len) {
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
