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
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.pipeline.scope.PipelineScopeModule;
import stroom.refdata.store.offheapstore.databases.AbstractLmdbDbTest;
import stroom.util.ByteSizeUnit;

import javax.inject.Inject;
import java.io.IOException;

public abstract class AbstractRefDataOffHeapStoreTest extends AbstractLmdbDbTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRefDataOffHeapStoreTest.class);
    private static final long DB_MAX_SIZE = ByteSizeUnit.MEBIBYTE.longBytes(5);
    protected RefDataStore refDataStore;
    protected Injector injector;
    @Inject
    private RefDataStoreFactory refDataStoreFactory;
    private RefDataStoreConfig refDataStoreConfig = new RefDataStoreConfig();

    @Override
    protected long getMaxSizeBytes() {
        return DB_MAX_SIZE;
    }

    @BeforeEach
    public void setup() throws IOException {
        super.setup();

        LOGGER.debug("Creating LMDB environment in dbDir {}", getDbDir().toAbsolutePath().toString());

        refDataStoreConfig.setLocalDir(getDbDir().toAbsolutePath().toString());

        setDbMaxSizeProperty();

        injector = Guice.createInjector(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(RefDataStoreConfig.class).toInstance(refDataStoreConfig);
                        install(new RefDataStoreModule());
                        install(new PipelineScopeModule());
                    }
                });
        injector.injectMembers(this);
        refDataStore = refDataStoreFactory.getOffHeapStore();
    }

    protected RefDataStoreConfig getRefDataStoreConfig() {
        return refDataStoreConfig;
    }

    protected void setDbMaxSizeProperty(final long sizeInBytes) {
        refDataStoreConfig.setMaxStoreSize(Long.toString(sizeInBytes));
    }

    protected void setPurgeAgeProperty(final String purgeAge) {
        refDataStoreConfig.setPurgeAge(purgeAge);
    }

    protected void setDbMaxSizeProperty() {
        setDbMaxSizeProperty(DB_MAX_SIZE);
    }
}
