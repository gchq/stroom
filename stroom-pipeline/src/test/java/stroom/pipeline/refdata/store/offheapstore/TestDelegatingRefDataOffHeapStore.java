package stroom.pipeline.refdata.store.offheapstore;

import stroom.pipeline.refdata.ReferenceDataConfig;
import stroom.pipeline.refdata.ReferenceDataLmdbConfig;
import stroom.pipeline.refdata.store.RefDataStore;
import stroom.pipeline.refdata.store.RefDataStoreFactory;
import stroom.pipeline.refdata.store.RefDataStoreTestModule;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Inject;

class TestDelegatingRefDataOffHeapStore extends StroomUnitTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestDelegatingRefDataOffHeapStore.class);

    private static final int ENTRIES_PER_MAP_DEF = 10;

    @Inject
    private RefDataStoreFactory refDataStoreFactory;

    private ReferenceDataConfig referenceDataConfig = new ReferenceDataConfig();
    private RefDataStoreTestModule refDataStoreTestModule;
    private Path dbDir = null;
    private RefDataStore refDataStore;
    private Injector injector;

    @BeforeEach
    void setup() throws IOException {
        dbDir = Files.createTempDirectory("stroom");
        Files.createDirectories(dbDir);
        FileUtil.deleteContents(dbDir);

        LOGGER.info("Creating LMDB environment in dbDir {}", dbDir.toAbsolutePath().toString());

        // This should ensure batching is exercised, including partial batches
        final int batchSize = Math.max(1, ENTRIES_PER_MAP_DEF / 2) - 1;
        LOGGER.debug("Using batchSize {}", batchSize);
        referenceDataConfig = new ReferenceDataConfig()
                .withLmdbConfig(new ReferenceDataLmdbConfig()
                        .withLocalDir(dbDir.toAbsolutePath().toString())
                        .withReaderBlockedByWriter(false))
                .withMaxPutsBeforeCommit(batchSize)
                .withMaxPurgeDeletesBeforeCommit(batchSize);

        refDataStoreTestModule = new RefDataStoreTestModule(
                this::getReferenceDataConfig,
                this::getCurrentTestDir,
                this::getCurrentTestDir);

        injector = Guice.createInjector(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        install(refDataStoreTestModule);
                    }
                });

        injector.injectMembers(this);
        refDataStore = refDataStoreFactory.getOffHeapStore();
    }

    @Test
    void testMigration() {
        final RefDataOffHeapStore legacyStore = getLegacyStore(true);

        long effectiveTimeMs = System.currentTimeMillis();
        RefDataStoreTestModule.DEFAULT_REF_STREAM_DEFINITIONS.forEach(refStreamDefinition -> {
            legacyStore.doWithLoaderUnlessComplete(refStreamDefinition, effectiveTimeMs, loader -> {
                //

            });
        });

    }

    @Test
    void getMapNames() {
    }

    @Test
    void getLoadState() {
    }

    @Test
    void exists() {
    }

    @Test
    void getValue() {
    }

    @Test
    void getValueProxy() {
    }

    @Test
    void consumeValueBytes() {
    }

    @Test
    void doWithLoaderUnlessComplete() {
    }

    @Test
    void list() {
    }

    @Test
    void testList() {
    }

    @Test
    void consumeEntries() {
    }

    @Test
    void listProcessingInfo() {
    }

    @Test
    void testListProcessingInfo() {
    }

    @Test
    void getKeyValueEntryCount() {
    }

    @Test
    void getRangeValueEntryCount() {
    }

    @Test
    void getProcessingInfoEntryCount() {
    }

    @Test
    void purgeOldData() {
    }

    @Test
    void testPurgeOldData() {
    }

    @Test
    void purge() {
    }

    @Test
    void logAllContents() {
    }

    @Test
    void testLogAllContents() {
    }

    @Test
    void getStorageType() {
    }

    @Test
    void getSizeOnDisk() {
    }

    @Test
    void getSystemInfo() {
    }

    @Test
    void testGetSystemInfo() {
    }

    private RefDataOffHeapStore getLegacyStore(final boolean createIfNotExists) {
        return ((DelegatingRefDataOffHeapStore) refDataStore).getLegacyRefDataStore(createIfNotExists);
    }

    private ReferenceDataConfig getReferenceDataConfig() {
        return referenceDataConfig;
    }
}
