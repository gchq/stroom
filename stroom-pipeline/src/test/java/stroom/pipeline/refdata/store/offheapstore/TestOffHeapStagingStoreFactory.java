/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.pipeline.refdata.store.offheapstore;

import stroom.bytebuffer.PooledByteBufferOutputStream;
import stroom.lmdb.LmdbEnvFactory;
import stroom.pipeline.refdata.ReferenceDataConfig;
import stroom.pipeline.refdata.ReferenceDataLmdbConfig;
import stroom.pipeline.refdata.ReferenceDataStagingLmdbConfig;
import stroom.pipeline.refdata.store.RefDataStore;
import stroom.pipeline.refdata.store.RefDataStoreFactory;
import stroom.pipeline.refdata.store.RefDataStoreTestModule;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.offheapstore.databases.KeyValueStagingDb;
import stroom.pipeline.refdata.store.offheapstore.databases.RangeValueStagingDb;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.ByteSize;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A staging store owns an LMDB env, and the loader only ever closes the store that create()
 * returned. If create() opens the env and then fails, nothing holds a reference to that env, so
 * nothing could ever close it or delete its dir.
 */
class TestOffHeapStagingStoreFactory extends StroomUnitTest {

    @Inject
    private LmdbEnvFactory lmdbEnvFactory;
    @Inject
    private RangeValueStagingDb.Factory rangeValueStagingDbFactory;
    @Inject
    private KeyValueStagingDb.Factory keyValueStagingDbFactory;
    @Inject
    private PooledByteBufferOutputStream.Factory pooledByteBufferOutputStreamFactory;
    @Inject
    private PathCreator pathCreator;
    @Inject
    private RefDataStoreFactory refDataStoreFactory;
    @Inject
    private MapDefinitionUIDStore.Factory mapDefinitionUidStoreFactory;

    private ReferenceDataConfig referenceDataConfig;
    private RefDataLmdbEnv refDataLmdbEnv;
    private Path dbDir;

    private final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            RefDataStoreTestModule.REF_STREAM_1_ID);

    @BeforeEach
    void setup() throws IOException {
        dbDir = Files.createTempDirectory("stroom");
        FileUtil.deleteContents(dbDir);

        referenceDataConfig = new ReferenceDataConfig()
                .withLmdbConfig(new ReferenceDataLmdbConfig()
                        .withLocalDir(dbDir.toAbsolutePath().toString())
                        .withMaxStoreSize(ByteSize.ofMebibytes(50)))
                .withStagingLmdbConfig(new ReferenceDataStagingLmdbConfig()
                        .withMaxStoreSize(ByteSize.ofMebibytes(50)));

        final Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                install(new RefDataStoreTestModule(
                        () -> referenceDataConfig,
                        () -> getCurrentTestDir(),
                        () -> getCurrentTestDir()));
            }
        });
        injector.injectMembers(this);

        final RefDataStore refDataStore = refDataStoreFactory.getOffHeapStore(refStreamDefinition);
        refDataLmdbEnv = ((RefDataOffHeapStore) refDataStore).getLmdbEnvironment();
    }

    @AfterEach
    void tearDown() {
        // The dir must not be deleted under an open env, so only delete it once the close worked.
        if (refDataLmdbEnv != null) {
            refDataLmdbEnv.close();
            if (dbDir != null) {
                FileUtil.deleteDir(dbDir);
            }
        }
    }

    @Test
    void failureCreatingTheStoreClosesAndDeletesTheEnv() {
        final OffHeapStagingStoreFactory factory = createFactory(env -> {
            throw new RuntimeException("Failed to open the staging db");
        });

        assertThatThrownBy(() -> factory.create(refStreamDefinition, mapDefinitionUidStore()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to open the staging db");

        // Closed AND deleted, rather than left open with its files lingering for the life of the
        // process. Nothing else has a reference to it, so nothing else could ever clean it up.
        assertThat(stagingEnvDirs()).isEmpty();
    }

    /**
     * Guarded with a flag rather than catch(Exception), so an Error unwinds the same way. Opening
     * LMDB DBIs goes through JNI, which can raise one.
     */
    @Test
    void anErrorCreatingTheStoreAlsoClosesAndDeletesTheEnv() {
        final OffHeapStagingStoreFactory factory = createFactory(env -> {
            throw new OutOfMemoryError("Direct buffer memory");
        });

        assertThatThrownBy(() -> factory.create(refStreamDefinition, mapDefinitionUidStore()))
                .isInstanceOf(OutOfMemoryError.class);

        assertThat(stagingEnvDirs()).isEmpty();
    }

    @Test
    void successfullyCreatedStoreKeepsItsEnv() throws Exception {
        final OffHeapStagingStoreFactory factory = createFactory(keyValueStagingDbFactory);

        try (final OffHeapStagingStore store = factory.create(refStreamDefinition, mapDefinitionUidStore())) {
            assertThat(store).isNotNull();
            assertThat(stagingEnvDirs()).hasSize(1);
        }

        // close() deletes the staging env, so a normal load leaves nothing behind either.
        assertThat(stagingEnvDirs()).isEmpty();
    }

    private MapDefinitionUIDStore mapDefinitionUidStore() {
        return mapDefinitionUidStoreFactory.create(refDataLmdbEnv);
    }

    private OffHeapStagingStoreFactory createFactory(final KeyValueStagingDb.Factory keyValueDbFactory) {
        return new OffHeapStagingStoreFactory(
                lmdbEnvFactory,
                keyValueDbFactory,
                rangeValueStagingDbFactory,
                pooledByteBufferOutputStreamFactory,
                () -> referenceDataConfig.getStagingLmdbConfig(),
                pathCreator);
    }

    private List<Path> stagingEnvDirs() {
        final Path baseDir = pathCreator.toAppPath(referenceDataConfig.getStagingLmdbConfig().getLocalDir());
        if (!Files.isDirectory(baseDir)) {
            return List.of();
        }
        try (final Stream<Path> stream = Files.list(baseDir)) {
            return stream.filter(Files::isDirectory).toList();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
