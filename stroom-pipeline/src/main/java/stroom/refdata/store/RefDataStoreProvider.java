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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.pipeline.writer.PathCreator;
import stroom.refdata.store.offheapstore.RefDataOffHeapStore;
import stroom.refdata.store.onheapstore.RefDataOnHeapStore;
import stroom.util.io.FileUtil;
import stroom.util.lifecycle.JobTrackedSchedule;
import stroom.util.lifecycle.StroomSimpleCronSchedule;
import stroom.util.logging.LambdaLogger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

@Singleton
public class RefDataStoreProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(RefDataStoreProvider.class);

    private static final String DEFAULT_STORE_SUB_DIR_NAME = "refDataOffHeapStore";

    private final RefDataStoreConfig refDataStoreConfig;
    private final RefDataStore offHeapRefDataStore;

    @Inject
    RefDataStoreProvider(final RefDataStoreConfig refDataStoreConfig,
                         final RefDataOffHeapStore.Factory refDataOffHeapStoreFactory) {
        this.refDataStoreConfig = refDataStoreConfig;


        final Path storeDir = getStoreDir();

        long maxStoreSizeBytes = refDataStoreConfig.getMaxStoreSizeBytes();

        int maxReaders = refDataStoreConfig.getMaxReaders();

        int maxPutsBeforeCommit = refDataStoreConfig.getMaxPutsBeforeCommit();

        int valueBufferCapacity = refDataStoreConfig.getValueBufferCapacity();

        this.offHeapRefDataStore = refDataOffHeapStoreFactory.create(
                storeDir,
                maxStoreSizeBytes,
                maxReaders,
                maxPutsBeforeCommit,
                valueBufferCapacity);
    }

    public RefDataStore getOffHeapStore() {
        return offHeapRefDataStore;
    }

    public RefDataStore createOnHeapStore() {
        return new RefDataOnHeapStore();
    }


    @StroomSimpleCronSchedule(cron = "0 2 *") // 02:00 every day
    @JobTrackedSchedule(
            jobName = "Ref Data Off-heap Store Purge",
            description = "Purge old reference data from the off heap store as configured")
    public void purgeOldData() {
        this.offHeapRefDataStore.purgeOldData();
        // nothing to purge in the heap stores as they are transient objects
    }

    private Path getStoreDir() {
        String storeDirStr = refDataStoreConfig.getLocalDir();
        Path storeDir;
        if (storeDirStr == null) {
            LOGGER.info("Off heap store dir is not set, falling back to {}", FileUtil.getTempDir());
            storeDir = FileUtil.getTempDir();
            Objects.requireNonNull(storeDir, "Temp dir is not set");
            storeDir = storeDir.resolve(DEFAULT_STORE_SUB_DIR_NAME);
        } else {
            storeDirStr = PathCreator.replaceSystemProperties(storeDirStr);
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

//    public static class OffHeapRefDataStoreProvider implements Provider<RefDataStore> {
//
//        private final RefDataStoreProvider refDataStoreProvider;
//
//        @Inject
//        public OffHeapRefDataStoreProvider(final RefDataStoreProvider refDataStoreProvider) {
//            this.refDataStoreProvider = refDataStoreProvider;
//        }
//
//        @Override
//        public RefDataStore get() {
//            return refDataStoreProvider.getOffHeapStore();
//        }
//    }
//
//    public static class OnHeapRefDataStoreProvider implements Provider<RefDataStore> {
//
//        private final RefDataStoreProvider refDataStoreProvider;
//
//        @Inject
//        public OnHeapRefDataStoreProvider(final RefDataStoreProvider refDataStoreProvider) {
//            this.refDataStoreProvider = refDataStoreProvider;
//        }
//
//        @Override
//        public RefDataStore get() {
//            return refDataStoreProvider.createOnHeapStore();
//        }
//    }

}
