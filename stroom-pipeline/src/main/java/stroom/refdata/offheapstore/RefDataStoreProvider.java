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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.jobsystem.JobTrackedSchedule;
import stroom.properties.StroomPropertyService;
import stroom.util.ByteSizeUnit;
import stroom.util.config.StroomProperties;
import stroom.util.lifecycle.StroomSimpleCronSchedule;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(RefDataStoreProvider.class);

    static final String OFF_HEAP_STORE_DIR_PROP_KEY = "stroom.refloader.offheapstore.localDir";
    static final String MAX_STORE_SIZE_BYTES_PROP_KEY = "stroom.refloader.offheapstore.maxStoreSize";
    private static final String MAX_READERS_PROP_KEY = "stroom.refloader.offheapstore.maxReaders";
    private static final String MAX_PUTS_BEFORE_COMMIT_PROP_KEY = "stroom.refloader.offheapstore.maxPutsBeforeCommit";
    private static final String VALUE_BUFFER_CAPACITY_PROP_KEY = "stroom.refloader.offheapstore.valueBufferCapacity";

    private static final String DEFAULT_STORE_SUB_DIR_NAME = "refDataOffHeapStore";

    private static final int VALUE_BUFFER_CAPACITY_DEFAULT_VALUE = 1_000;
    private static final long MAX_STORE_SIZE_BYTES_DEFAULT = ByteSizeUnit.GIBIBYTE.longBytes(10);
    private static final int MAX_READERS_DEFAULT = 100;
    private static final int MAX_PUTS_BEFORE_COMMIT_DEFAULT = 1000;

    private final StroomPropertyService stroomPropertyService;
    private final RefDataStore refDataStore;

    @Inject
    RefDataStoreProvider(final StroomPropertyService stroomPropertyService,
                         final RefDataOffHeapStore.Factory refDataOffHeapStoreFactory) {
        this.stroomPropertyService = stroomPropertyService;


        final Path storeDir = getStoreDir();

        long maxStoreSizeBytes = stroomPropertyService.getLongProperty(
                MAX_STORE_SIZE_BYTES_PROP_KEY, MAX_STORE_SIZE_BYTES_DEFAULT);

        int maxReaders = stroomPropertyService.getIntProperty(MAX_READERS_PROP_KEY, MAX_READERS_DEFAULT);

        int maxPutsBeforeCommit = stroomPropertyService.getIntProperty(
                MAX_PUTS_BEFORE_COMMIT_PROP_KEY, MAX_PUTS_BEFORE_COMMIT_DEFAULT);

        int valueBufferCapacity = stroomPropertyService.getIntProperty(
                VALUE_BUFFER_CAPACITY_PROP_KEY, VALUE_BUFFER_CAPACITY_DEFAULT_VALUE);

        this.refDataStore = refDataOffHeapStoreFactory.create(
                storeDir,
                maxStoreSizeBytes,
                maxReaders,
                maxPutsBeforeCommit,
                valueBufferCapacity);
    }

    @Override
    public RefDataStore get() {
        return refDataStore;
    }

    @StroomSimpleCronSchedule(cron = "0 2 *") // 02:00 every day
    @JobTrackedSchedule(
            jobName = "Ref Data Store Purge",
            description = "Purge old reference data from the off heap store, as defined by " +
                    RefDataOffHeapStore.DATA_RETENTION_AGE_PROP_KEY)
    public void purgeOldData() {
        this.refDataStore.purgeOldData();
    }

    private Path getStoreDir() {
        String storeDirStr = stroomPropertyService.getProperty(OFF_HEAP_STORE_DIR_PROP_KEY);
        LOGGER.info("Property {} is not set, falling back to {}", OFF_HEAP_STORE_DIR_PROP_KEY, StroomProperties.STROOM_TEMP);
        Path storeDir;
        if (storeDirStr == null) {
            String stroomTempDirStr = stroomPropertyService.getProperty(StroomProperties.STROOM_TEMP);
            Objects.requireNonNull(stroomTempDirStr, () ->
                    LambdaLogger.buildMessage("Property {} is not set", StroomProperties.STROOM_TEMP));
            storeDir = Paths.get(stroomTempDirStr).resolve(DEFAULT_STORE_SUB_DIR_NAME);
        } else {
            storeDir = Paths.get(storeDirStr);
        }

        try {
            LOGGER.debug("Ensuring directory {}", storeDir);
            Files.createDirectories(storeDir);
        } catch (IOException e) {
            throw new RuntimeException(LambdaLogger.buildMessage("Error ensuring store directory {} exists", storeDirStr), e);
        }

        return storeDir;
    }

}
