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

package stroom.receive.common;

import stroom.util.io.AbstractDirChangeMonitor;
import stroom.util.io.SimplePathCreator;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectReader;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class DataFeedIdentitiesDirWatcher extends AbstractDirChangeMonitor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataFeedIdentitiesDirWatcher.class);

    private final Provider<DataFeedIdentityService> dataFeedIdentityServiceProvider;

    @Inject
    public DataFeedIdentitiesDirWatcher(final Provider<ReceiveDataConfig> receiveDataConfigProvider,
                                        final SimplePathCreator simplePathCreator,
                                        final Provider<DataFeedIdentityService> dataFeedIdentityServiceProvider) {
        super(getDataFeedDir(receiveDataConfigProvider, simplePathCreator),
                DataFeedIdentitiesDirWatcher::isJsonFile,
                EnumSet.allOf(EventType.class));
        this.dataFeedIdentityServiceProvider = dataFeedIdentityServiceProvider;
    }

    private static Path getDataFeedDir(final Provider<ReceiveDataConfig> receiveDataConfigProvider,
                                       final SimplePathCreator simplePathCreator) {
        final ReceiveDataConfig receiveDataConfig = receiveDataConfigProvider.get();
        return NullSafe.get(
                receiveDataConfig.getDataFeedIdentitiesDir(),
                simplePathCreator::toAppPath);
    }

    @Override
    protected void onInitialisation() {
        processAllFiles();
    }

    @Override
    protected void onEntryModify(final Path path) {
        LOGGER.debug("onEntryModify - path: {}", path);
        if (path != null) {
            processFile(path);
        }
    }

    @Override
    protected void onEntryCreate(final Path path) {
        LOGGER.debug("onEntryCreate - path: {}", path);
        if (path != null) {
            processFile(path);
        }
    }

    @Override
    protected void onEntryDelete(final Path path) {
        LOGGER.debug("onEntryDelete - path: {}", path);
        if (path != null) {
            dataFeedIdentityServiceProvider.get().removeKeysForFile(path);
        }
    }

    @Override
    protected void onOverflow() {
        LOGGER.debug("onOverflow");
        processAllFiles();
    }

    private void processAllFiles() {
        // Re-scan the whole directory. The addDataFeedKeys method is idempotent
        LOGGER.info("Reading all data feed identity files in {}", dirToWatch);
        try (final DirectoryStream<Path> dirStream = Files.newDirectoryStream(dirToWatch)) {
            final AtomicInteger counter = new AtomicInteger();
            dirStream.forEach(path -> {
                if (fileIncludeFilter == null || fileIncludeFilter.test(path)) {
                    processFile(path);
                    counter.incrementAndGet();
                } else {
                    LOGGER.info(() -> LogUtil.message("Ignoring file {}", path.toAbsolutePath().normalize()));
                }
            });
            LOGGER.info("Completed reading {} data feed identity files in {}", counter, dirToWatch);
        } catch (final Exception e) {
            LOGGER.error("Error reading contents of directory '{}': {}", dirToWatch, LogUtil.exceptionMessage(e));
        }
    }

    private void processFile(final Path path) {
        if (path != null && Files.isRegularFile(path)) {
            LOGGER.info(() -> LogUtil.message("Reading data feed identity file {}", path.toAbsolutePath().normalize()));
            final ObjectReader reader = JsonUtil.getMapper().reader()
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            try (final InputStream fileStream = new FileInputStream(path.toFile())) {
                try {
                    final DataFeedIdentities dataFeedIdentities = reader.readValue(fileStream,
                            DataFeedIdentities.class);
                    if (dataFeedIdentities != null && !dataFeedIdentities.isEmpty()) {
                        final int addedCount = dataFeedIdentityServiceProvider.get()
                                .addDataFeedKeys(dataFeedIdentities.getDataFeedIdentities(), path);
                        LOGGER.info(() -> LogUtil.message("Loaded {} data feed identities found in {}",
                                addedCount,
                                path.toAbsolutePath().normalize()));
                    } else {
                        LOGGER.info(() -> LogUtil.message("No data feed identities found in {}",
                                path.toAbsolutePath().normalize()));
                    }
                } catch (final IOException e) {
                    LOGGER.debug(() -> LogUtil.message("Error parsing file {}: {}", path, e.getMessage()), e);
                    LOGGER.error("Error parsing file {}: {} (enable DEBUG for stacktrace)", path, e.getMessage());
                }
            } catch (final IOException e) {
                LOGGER.debug(() -> LogUtil.message("Error reading file {}: {}", path, e.getMessage()), e);
                LOGGER.error("Error reading file {}: {} (enable DEBUG for stacktrace)", path, e.getMessage());
            }
        }
    }

    private static boolean isJsonFile(final Path path) {
        final boolean isJsonFile = path != null
                                   && Files.isRegularFile(path)
                                   && Files.isReadable(path)
                                   && path.getFileName()
                                           .toString()
                                           .toLowerCase()
                                           .endsWith(".json");
        LOGGER.debug(() -> LogUtil.message("isJsonFile() - path: {}, isJsonFile: {}",
                NullSafe.get(path, Path::toAbsolutePath, Path::normalize), isJsonFile));
        return isJsonFile;
    }

}
