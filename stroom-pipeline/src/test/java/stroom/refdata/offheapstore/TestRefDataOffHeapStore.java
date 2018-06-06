/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.refdata.offheapstore;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.properties.MockStroomPropertyService;
import stroom.properties.StroomPropertyService;
import stroom.refdata.RefDataModule;
import stroom.refdata.offheapstore.databases.AbstractLmdbDbTest;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class TestRefDataOffHeapStore extends AbstractLmdbDbTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestRefDataOffHeapStore.class);

    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder();

    @Inject
    private RefDataStore refDataStore;

    private final MockStroomPropertyService mockStroomPropertyService = new MockStroomPropertyService();

    @Before
    public void setup() {

        Path dbDir = tmpDir.getRoot().toPath();
        LOGGER.debug("Creating LMDB environment in dbDir {}", dbDir.toAbsolutePath().toString());

        mockStroomPropertyService.setProperty(RefDataStoreProvider.OFF_HEAP_STORE_DIR_PROP_KEY,
                dbDir.toAbsolutePath().toString());

        final Injector injector = Guice.createInjector(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(StroomPropertyService.class).toInstance(mockStroomPropertyService);
                        install(new RefDataModule());
                    }
                });
        injector.injectMembers(this);
    }

    @Test
    public void getProcessingInfo() {
    }

    @Test
    public void isDataLoaded_true() {
    }

    @Test
    public void isDataLoaded_false() {
        byte version = 0;
        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                UUID.randomUUID().toString(),
                version,
                123456L,
                1);

        boolean isLoaded = refDataStore.isDataLoaded(refStreamDefinition);

        assertThat(isLoaded).isFalse();
    }

    @Test
    public void getValueProxy() {
    }

    @Test
    public void loader() {
    }
}