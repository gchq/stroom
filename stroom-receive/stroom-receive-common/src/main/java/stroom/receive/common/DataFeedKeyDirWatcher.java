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
import java.util.function.Predicate;

@Singleton
public class DataFeedKeyDirWatcher extends AbstractDirChangeMonitor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataFeedKeyDirWatcher.class);

    private static final Predicate<Path> FILE_INCLUDE_FILTER = path ->
            path != null
            && Files.isRegularFile(path)
            && path.getFileName().toString().endsWith(".json");

    private final Provider<DataFeedKeyService> dataFeedKeyServiceProvider;

    @Inject
    public DataFeedKeyDirWatcher(final Provider<ReceiveDataConfig> receiveDataConfigProvider,
                                 final SimplePathCreator simplePathCreator,
                                 final Provider<DataFeedKeyService> dataFeedKeyServiceProvider) {
        super(
                getDataFeedDir(receiveDataConfigProvider, simplePathCreator),
                FILE_INCLUDE_FILTER,
                EnumSet.allOf(EventType.class));
        this.dataFeedKeyServiceProvider = dataFeedKeyServiceProvider;
    }

    private static Path getDataFeedDir(final Provider<ReceiveDataConfig> receiveDataConfigProvider,
                                       final SimplePathCreator simplePathCreator) {
        final ReceiveDataConfig receiveDataConfig = receiveDataConfigProvider.get();
        return NullSafe.get(
                receiveDataConfig.getDataFeedKeysDir(),
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
            dataFeedKeyServiceProvider.get().removeKeysForFile(path);
        }
    }

    @Override
    protected void onOverflow() {
        LOGGER.debug("onOverflow");
        processAllFiles();
    }

    private void processAllFiles() {
        // Re-scan the whole directory. The addDataFeedKeys method is idempotent
        LOGGER.info("Reading all data feed key files in {}", dirToWatch);
        try (final DirectoryStream<Path> dirStream = Files.newDirectoryStream(dirToWatch)) {
            final AtomicInteger counter = new AtomicInteger();
            dirStream.forEach(path -> {
                if (fileIncludeFilter == null || fileIncludeFilter.test(path)) {
                    processFile(path);
                    counter.incrementAndGet();
                } else {
                    LOGGER.info("Ignoring file {}", path.toAbsolutePath().normalize());
                }
            });
            LOGGER.info("Completed reading {} data feed key files in {}", counter, dirToWatch);
        } catch (final Exception e) {
            LOGGER.error("Error reading contents of directory '{}': {}", dirToWatch, LogUtil.exceptionMessage(e));
        }
    }

    private void processFile(final Path path) {
        if (path != null && Files.isRegularFile(path)) {
            LOGGER.info("Reading datafeed key file {}", path.toAbsolutePath().normalize());
            final ObjectReader reader = JsonUtil.getMapper().reader()
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            try (final InputStream fileStream = new FileInputStream(path.toFile())) {
                try {
                    final HashedDataFeedKeys hashedDataFeedKeys = reader.readValue(fileStream,
                            HashedDataFeedKeys.class);
                    if (hashedDataFeedKeys != null && NullSafe.hasItems(hashedDataFeedKeys.getDataFeedKeys())) {
                        final int addedCount = dataFeedKeyServiceProvider.get().addDataFeedKeys(hashedDataFeedKeys,
                                path);
                        LOGGER.info("Loaded {} datafeed keys found in {}",
                                addedCount,
                                path.toAbsolutePath().normalize());
                    } else {
                        LOGGER.info("No datafeed keys found in {}", path.toAbsolutePath().normalize());
                    }
                } catch (final IOException e) {
                    LOGGER.debug("Error parsing file {}: {}", path, e.getMessage(), e);
                    LOGGER.error("Error parsing file {}: {} (enable DEBUG for stacktrace)", path, e.getMessage());
                }
            } catch (final IOException e) {
                LOGGER.debug("Error reading file {}: {}", path, e.getMessage(), e);
                LOGGER.error("Error reading file {}: {} (enable DEBUG for stacktrace)", path, e.getMessage());
            }
        }
    }
}
