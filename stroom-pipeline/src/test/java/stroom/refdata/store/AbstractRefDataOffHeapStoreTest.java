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

package stroom.refdata.store;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.guice.PipelineScopeModule;
import stroom.properties.api.PropertyService;
import stroom.properties.impl.mock.MockPropertyService;
import stroom.refdata.store.offheapstore.RefDataOffHeapStore;
import stroom.refdata.store.offheapstore.databases.AbstractLmdbDbTest;
import stroom.util.ByteSizeUnit;

import javax.inject.Inject;
import java.nio.file.Path;

public abstract class AbstractRefDataOffHeapStoreTest extends AbstractLmdbDbTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRefDataOffHeapStoreTest.class);
    private static final long DB_MAX_SIZE = ByteSizeUnit.MEBIBYTE.longBytes(5);

    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder();
    private final MockPropertyService mockPropertyService = new MockPropertyService();

//    @Inject
//    protected RefDataStoreHolder refDataStoreHolder;

    @Inject
    private RefDataStoreProvider refDataStoreProvider;

    protected RefDataStore refDataStore;
    protected Injector injector;

    @Override
    protected long getMaxSizeBytes() {
        return DB_MAX_SIZE;
    }

    @Before
    public void setup() {

        Path dbDir = tmpDir.getRoot().toPath();
        LOGGER.debug("Creating LMDB environment in dbDir {}", dbDir.toAbsolutePath().toString());

        mockPropertyService.setProperty(RefDataStoreProvider.OFF_HEAP_STORE_DIR_PROP_KEY,
                dbDir.toAbsolutePath().toString());

        setDbMaxSizeProperty();

        injector = Guice.createInjector(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(PropertyService.class).toInstance(mockPropertyService);
                        install(new RefDataStoreModule());
                        install(new PipelineScopeModule());
                    }
                });
        injector.injectMembers(this);
        refDataStore = refDataStoreProvider.getOffHeapStore();
    }

    protected void setProperty(String name, String value) {
        mockPropertyService.setProperty(name, value);
    }

    protected void setDbMaxSizeProperty(final long sizeInBytes) {
        mockPropertyService.setProperty(RefDataStoreProvider.MAX_STORE_SIZE_BYTES_PROP_KEY,
                Long.toString(sizeInBytes));
    }

    protected void setPurgeAgeProperty(final String purgeAge) {
        mockPropertyService.setProperty(RefDataOffHeapStore.DATA_RETENTION_AGE_PROP_KEY, purgeAge);
    }

    protected void setDbMaxSizeProperty() {
        setDbMaxSizeProperty(DB_MAX_SIZE);
    }
}
