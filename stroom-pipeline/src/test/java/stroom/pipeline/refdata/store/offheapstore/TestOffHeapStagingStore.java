package stroom.pipeline.refdata.store.offheapstore;

import stroom.pipeline.refdata.ReferenceDataConfig;
import stroom.pipeline.refdata.ReferenceDataLmdbConfig;
import stroom.pipeline.refdata.store.RefDataStoreModule;
import stroom.task.mock.MockTaskModule;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.FileUtil;
import stroom.util.io.HomeDirProvider;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.pipeline.scope.PipelineScopeModule;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Inject;

class TestOffHeapStagingStore extends StroomUnitTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestOffHeapStagingStore.class);

    @Inject
    private OffHeapStagingStoreFactory offHeapStagingStoreFactory;

    private ReferenceDataConfig referenceDataConfig = new ReferenceDataConfig();
    private Injector injector;
    private Path dbDir = null;
    private OffHeapStagingStore offHeapStagingStore;


    @BeforeEach
    void setup() throws IOException {
        dbDir = Files.createTempDirectory("stroom");
//        dbDir = Paths.get("/home/dev/tmp/ref_test");
        Files.createDirectories(dbDir);
        FileUtil.deleteContents(dbDir);

        LOGGER.info("Creating LMDB environment in dbDir {}", dbDir.toAbsolutePath().toString());

        // This should ensure batching is exercised, including partial batches
//        final int batchSize = Math.max(1, ENTRIES_PER_MAP_DEF / 2) - 1;
        final int batchSize = 10;
        LOGGER.debug("Using batchSize {}", batchSize);
        referenceDataConfig = new ReferenceDataConfig()
                .withLmdbConfig(new ReferenceDataLmdbConfig()
                        .withLocalDir(dbDir.toAbsolutePath().toString())
                        .withReaderBlockedByWriter(false))
                .withMaxPutsBeforeCommit(batchSize)
                .withMaxPurgeDeletesBeforeCommit(batchSize);

        injector = Guice.createInjector(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ReferenceDataConfig.class).toProvider(() -> getReferenceDataConfig());
                        bind(HomeDirProvider.class).toInstance(() -> getCurrentTestDir());
                        bind(TempDirProvider.class).toInstance(() -> getCurrentTestDir());
                        bind(PathCreator.class).to(SimplePathCreator.class);
                        install(new RefDataStoreModule());
                        install(new MockTaskModule());
                        install(new PipelineScopeModule());
                    }
                });

        injector.injectMembers(this);
//        offHeapStagingStore = offHeapStagingStoreFactory.create()
    }

    @Test
    void put() {
    }

    @Test
    void testPut() {
    }

    @Test
    void forEachKeyValueEntry() {
    }

    @Test
    void forEachRangeValueEntry() {
    }

    @Test
    void getMapNames() {
    }

    @Test
    void getMapDefinition() {
    }

    protected ReferenceDataConfig getReferenceDataConfig() {
        return referenceDataConfig;
    }
}
