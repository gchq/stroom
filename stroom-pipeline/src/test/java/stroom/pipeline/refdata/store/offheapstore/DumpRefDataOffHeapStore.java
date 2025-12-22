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

package stroom.pipeline.refdata.store.offheapstore;

import stroom.pipeline.refdata.ReferenceDataConfig;
import stroom.pipeline.refdata.ReferenceDataLmdbConfig;
import stroom.pipeline.refdata.store.RefDataStore;
import stroom.pipeline.refdata.store.RefDataStoreFactory;
import stroom.pipeline.refdata.store.RefDataStoreModule;
import stroom.util.logging.LogUtil;
import stroom.util.pipeline.scope.PipelineScopeModule;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DumpRefDataOffHeapStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(DumpRefDataOffHeapStore.class);

    private static final Path DEFAULT_STORE_DIR = Paths.get("/tmp/stroom/refDataOffHeapStore");

    /**
     * main() method to dump the contents of all the table in the {@link RefDataOffHeapStore}.
     * This is useful when running pipeline processing int tests or stroom and you want to see what is
     * in the store.
     * <p>
     * Not advisable to use if there are large amounts of data in the store.
     */
    public static void main(final String[] args) {
        final Path storeDir;
        if (args.length > 0) {
            storeDir = Paths.get(args[0]);
        } else {
            storeDir = DEFAULT_STORE_DIR;
        }

        if (!Files.isDirectory(storeDir)) {
            throw new RuntimeException(LogUtil.message("Unable to find store directory {}", storeDir.toString()));
        }

        LOGGER.info("Using storeDir {}", storeDir.toAbsolutePath().normalize());

        final ReferenceDataConfig referenceDataConfig = new ReferenceDataConfig()
                .withLmdbConfig(new ReferenceDataLmdbConfig()
                        .withLocalDir(storeDir.toAbsolutePath().toString()));

        final Injector injector = Guice.createInjector(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ReferenceDataConfig.class).toInstance(referenceDataConfig);
                        install(new RefDataStoreModule());
                        install(new PipelineScopeModule());
                    }
                });

        final RefDataStoreFactory refDataStoreFactory = injector.getInstance(RefDataStoreFactory.class);
        final RefDataStore refDataStore = refDataStoreFactory.getOffHeapStore();

        refDataStore.logAllContents(LOGGER::info);
    }
}
