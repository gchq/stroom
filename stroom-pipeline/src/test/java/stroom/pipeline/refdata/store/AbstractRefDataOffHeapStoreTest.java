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

package stroom.pipeline.refdata.store;

import stroom.pipeline.refdata.ReferenceDataConfig;
import stroom.pipeline.refdata.store.offheapstore.databases.AbstractStoreDbTest;
import stroom.util.io.ByteSize;
import stroom.util.pipeline.scope.PipelineScopeModule;
import stroom.util.time.StroomDuration;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractRefDataOffHeapStoreTest extends AbstractStoreDbTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRefDataOffHeapStoreTest.class);
    private static final ByteSize DB_MAX_SIZE = ByteSize.ofMebibytes(5);
    protected RefDataStore refDataStore;
    protected Injector injector;
    @Inject
    private RefDataStoreFactory refDataStoreFactory;

    private ReferenceDataConfig referenceDataConfig = new ReferenceDataConfig();

    @Override
    protected ByteSize getMaxSizeBytes() {
        return DB_MAX_SIZE;
    }

    @BeforeEach
    void setup() {
        LOGGER.debug("Creating LMDB environment in dbDir {}", getDbDir().toAbsolutePath().toString());

        referenceDataConfig = new ReferenceDataConfig()
                .withLmdbConfig(referenceDataConfig.getLmdbConfig()
                        .withLocalDir(getDbDir().toAbsolutePath().toString()));

        setDbMaxSizeProperty();

        injector = Guice.createInjector(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ReferenceDataConfig.class).toInstance(referenceDataConfig);
                        install(new RefDataStoreModule());
                        install(new PipelineScopeModule());
                    }
                });
        injector.injectMembers(this);
        refDataStore = refDataStoreFactory.getOffHeapStore();
    }

    protected ReferenceDataConfig getReferenceDataConfig() {
        return referenceDataConfig;
    }

    protected void setDbMaxSizeProperty(final ByteSize sizeInBytes) {
        referenceDataConfig = new ReferenceDataConfig()
                .withLmdbConfig(referenceDataConfig.getLmdbConfig()
                        .withMaxStoreSize(sizeInBytes));
    }

    protected void setPurgeAgeProperty(final StroomDuration purgeAge) {
        referenceDataConfig = referenceDataConfig.withPurgeAge(purgeAge);
    }

    protected void setDbMaxSizeProperty() {
        setDbMaxSizeProperty(DB_MAX_SIZE);
    }
}
