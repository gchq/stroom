package stroom.analytics.impl;

import stroom.analytics.shared.DuplicateCheckRow;
import stroom.analytics.shared.DuplicateCheckRows;
import stroom.analytics.shared.FindDuplicateCheckCriteria;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.lmdb2.LmdbEnvDir;
import stroom.query.common.v2.DuplicateCheckStoreConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PageRequest;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    @Mock
    private DuplicateCheckDirs mockDuplicateCheckDirs;

    @Test
    void test(@TempDir Path tempDir) {
        LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(tempDir, true);
        Mockito.when(mockDuplicateCheckDirs.getDir(UUID))
                .thenReturn(lmdbEnvDir);

        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        final DuplicateCheckStoreConfig duplicateCheckStoreConfig = new DuplicateCheckStoreConfig();
        final DuplicateCheckRowSerde serde = new DuplicateCheckRowSerde(byteBufferFactory);

        try (ExecutorService executorService = Executors.newSingleThreadExecutor()) {
            final DuplicateCheckStore duplicateCheckStore = new DuplicateCheckStore(
                    mockDuplicateCheckDirs,
                    byteBufferFactory,
                    duplicateCheckStoreConfig,
                    serde,
                    () -> executorService,
                    UUID);
            duplicateCheckStore.writeColumnNames(List.of("col1", "col2", "col3"));

            duplicateCheckStore.tryInsert(ROW_A);
            duplicateCheckStore.tryInsert(ROW_B);
            duplicateCheckStore.tryInsert(ROW_B);
            duplicateCheckStore.tryInsert(ROW_C);
            duplicateCheckStore.tryInsert(ROW_A);
            duplicateCheckStore.tryInsert(ROW_C);

            // Will block until all the above are loaded in
            duplicateCheckStore.flush();

            final DuplicateCheckRows rows = duplicateCheckStore.fetchData(new FindDuplicateCheckCriteria(
                    PageRequest.unlimited(),
                    null,
                    null,
                    null));

            final List<DuplicateCheckRow> values = rows.getResultPage().getValues();
            Assertions.assertThat(values)
                    .containsExactlyInAnyOrder(
                            ROW_A,
                            ROW_B,
                            ROW_C);

            duplicateCheckStore.close();
        }
    }

    @Test
    void testHashClash(@TempDir Path tempDir) {
        LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(tempDir, true);
        Mockito.when(mockDuplicateCheckDirs.getDir(UUID))
                .thenReturn(lmdbEnvDir);

        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        final DuplicateCheckStoreConfig duplicateCheckStoreConfig = new DuplicateCheckStoreConfig();
        // Every value gets the same hash, so 100% hash clashes
        final DuplicateCheckRowSerde serde = new HashClashDupCheckRowSerde(byteBufferFactory);

        try (ExecutorService executorService = Executors.newSingleThreadExecutor()) {
            final DuplicateCheckStore duplicateCheckStore = new DuplicateCheckStore(
                    mockDuplicateCheckDirs,
                    byteBufferFactory,
                    duplicateCheckStoreConfig,
                    serde,
                    () -> executorService,
                    UUID);
            duplicateCheckStore.writeColumnNames(List.of("col1", "col2", "col3"));

            duplicateCheckStore.tryInsert(ROW_A);
            duplicateCheckStore.tryInsert(ROW_B);
            duplicateCheckStore.tryInsert(ROW_B);
            duplicateCheckStore.tryInsert(ROW_C);
            duplicateCheckStore.tryInsert(ROW_A);
            duplicateCheckStore.tryInsert(ROW_C);

            // Will block until all the above are loaded in
            duplicateCheckStore.flush();

            final DuplicateCheckRows rows = duplicateCheckStore.fetchData(new FindDuplicateCheckCriteria(
                    PageRequest.unlimited(),
                    null,
                    null,
                    null));

            final List<DuplicateCheckRow> values = rows.getResultPage().getValues();
            Assertions.assertThat(values)
                    .containsExactlyInAnyOrder(
                            ROW_A,
                            ROW_B,
                            ROW_C);

            duplicateCheckStore.close();
        }
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
