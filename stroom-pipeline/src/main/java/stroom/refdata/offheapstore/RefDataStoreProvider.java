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

import stroom.properties.StroomPropertyService;
import stroom.util.ByteSizeUnit;
import stroom.util.logging.LambdaLogger;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

@Singleton
public class RefDataStoreProvider implements Provider<RefDataStore> {

    public static final String OFF_HEAP_STORE_DIR_PROP_KEY = "stroom.refloader.offheapstore.localDir";
    public static final String MAX_STORE_SIZE_BYTES_PROP_KEY = "stroom.refloader.offheapstore.maxStoreSize";
    private static final long MAX_STORE_SIZE_BYTES_DEFAULT = ByteSizeUnit.GIBIBYTE.longBytes(10);

    private final StroomPropertyService stroomPropertyService;
    private final RefDataOffHeapStore refDataOffHeapStore;

    @Inject
    RefDataStoreProvider(final StroomPropertyService stroomPropertyService) {
        this.stroomPropertyService = stroomPropertyService;


        final Path storeDir = getStoreDir();

        long maxStoreSizeBytes = stroomPropertyService.getLongProperty(
                MAX_STORE_SIZE_BYTES_PROP_KEY, MAX_STORE_SIZE_BYTES_DEFAULT);

        this.refDataOffHeapStore = new RefDataOffHeapStore(storeDir, maxStoreSizeBytes);
    }

    @Override
    public RefDataStore get() {
        return refDataOffHeapStore;
    }

    private Path getStoreDir() {
        final String storeDirStr = stroomPropertyService.getProperty(OFF_HEAP_STORE_DIR_PROP_KEY);
        Objects.requireNonNull(storeDirStr);
        Path storeDir = Paths.get(storeDirStr);

        try {
            Files.createDirectories(storeDir);
        } catch (IOException e) {
            throw new RuntimeException(LambdaLogger.buildMessage("Error ensuring store directory {} exists", storeDirStr), e);
        }

        return storeDir;
    }
}
