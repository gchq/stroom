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

package stroom.query.common.v2;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.dictionary.api.WordListProvider;
import stroom.lmdb.LmdbConfig;
import stroom.lmdb2.LmdbEnv;
import stroom.lmdb2.LmdbEnvDir;
import stroom.lmdb2.LmdbEnvDirFactory;
import stroom.query.api.QueryKey;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.TableSettings;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.LongAdder;

@Singleton // To ensure the localDir delete is done only once and before store creation
public class LmdbDataStoreFactory implements DataStoreFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbDataStoreFactory.class);

    private final LmdbEnvDirFactory lmdbEnvDirFactory;
    private final Provider<SearchResultStoreConfig> resultStoreConfigProvider;
    private final Provider<Executor> executorProvider;
    private final Path searchResultStoreDir;
    private final MapDataStoreFactory mapDataStoreFactory;
    private final ByteBufferFactory bufferFactory;
    private final ExpressionPredicateFactory expressionPredicateFactory;
    private final AnnotationMapperFactory annotationMapperFactory;
    final WordListProvider wordListProvider;

    @Inject
    public LmdbDataStoreFactory(final LmdbEnvDirFactory lmdbEnvDirFactory,
                                final Provider<SearchResultStoreConfig> resultStoreConfigProvider,
                                final PathCreator pathCreator,
                                final Provider<Executor> executorProvider,
                                final MapDataStoreFactory mapDataStoreFactory,
                                final ByteBufferFactory bufferFactory,
                                final ExpressionPredicateFactory expressionPredicateFactory,
                                final AnnotationMapperFactory annotationMapperFactory,
                                final WordListProvider wordListProvider) {
        this.lmdbEnvDirFactory = lmdbEnvDirFactory;
        this.resultStoreConfigProvider = resultStoreConfigProvider;
        this.executorProvider = executorProvider;
        this.mapDataStoreFactory = mapDataStoreFactory;
        this.bufferFactory = bufferFactory;
        this.expressionPredicateFactory = expressionPredicateFactory;
        this.annotationMapperFactory = annotationMapperFactory;
        this.wordListProvider = wordListProvider;

        // This config prop requires restart, so we can hold on to it
        this.searchResultStoreDir = getLocalDir(resultStoreConfigProvider.get(), pathCreator);

        // As search result stores are transient they serve no purpose after shutdown so delete any that
        // may still be there
        cleanStoresDir(searchResultStoreDir);
    }

    @Override
    public DataStore create(final ExpressionContext expressionContext,
                            final SearchRequestSource searchRequestSource,
                            final QueryKey queryKey,
                            final String componentId,
                            final TableSettings tableSettings,
                            final FieldIndex fieldIndex,
                            final Map<String, String> paramMap,
                            final DataStoreSettings dataStoreSettings,
                            final ErrorConsumer errorConsumer) {

        final SearchResultStoreConfig resultStoreConfig = resultStoreConfigProvider.get();
        if (!resultStoreConfig.isOffHeapResults()) {
            if (dataStoreSettings.isProducePayloads()) {
                throw new RuntimeException("MapDataStore cannot produce payloads");
            }

            return mapDataStoreFactory.create(
                    expressionContext,
                    searchRequestSource,
                    queryKey, componentId,
                    tableSettings,
                    fieldIndex,
                    paramMap,
                    dataStoreSettings,
                    errorConsumer);

        } else {
            final String subDirectory = queryKey + "_" + componentId + "_" + UUID.randomUUID();
            final LmdbEnvDir lmdbEnvDir = lmdbEnvDirFactory
                    .builder()
                    .config(resultStoreConfig.getLmdbConfig())
                    .subDir(subDirectory)
                    .build();

            final LmdbEnv.Builder lmdbEnvBuilder = LmdbEnv
                    .builder()
                    .config(resultStoreConfig.getLmdbConfig())
                    .lmdbEnvDir(lmdbEnvDir);

            return new LmdbDataStore(
                    searchRequestSource,
                    lmdbEnvBuilder,
                    resultStoreConfig,
                    queryKey,
                    componentId,
                    tableSettings,
                    expressionContext,
                    fieldIndex,
                    paramMap,
                    dataStoreSettings,
                    executorProvider,
                    errorConsumer,
                    bufferFactory,
                    expressionPredicateFactory,
                    annotationMapperFactory, wordListProvider);
        }
    }

    private Path getLocalDir(final AbstractResultStoreConfig resultStoreConfig,
                             final PathCreator pathCreator) {
        final String dirFromConfig = NullSafe.get(
                resultStoreConfig,
                AbstractResultStoreConfig::getLmdbConfig,
                LmdbConfig::getLocalDir);

        Objects.requireNonNull(dirFromConfig, "localDir not set");
        return pathCreator.toAppPath(dirFromConfig);
    }

    private void cleanStoresDir(final Path localDir) {
        LOGGER.info("Deleting redundant search result stores from {}", localDir);
        // Delete contents.
        if (!FileUtil.deleteContents(localDir)) {
            throw new RuntimeException(LogUtil.message("Error deleting contents of {}", localDir));
        }
    }

    /**
     * @return The size of all result stores. Assumes no other files are stored in the configured
     * localDir.
     */
    @Override
    public StoreSizeSummary getTotalSizeOnDisk() {
        final LongAdder totalSizeBytes = new LongAdder();
        final LongAdder storeCount = new LongAdder();

        LOGGER.debug("Getting total size in {}", searchResultStoreDir);

        LOGGER.logDurationIfDebugEnabled(() -> {
            try {
                Files.walkFileTree(searchResultStoreDir, new FileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(final Path dir,
                                                             final BasicFileAttributes attrs) {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(final Path file,
                                                     final BasicFileAttributes attrs) {
                        if (Files.isRegularFile(file)) {
                            totalSizeBytes.add(attrs.size());
                            if (LmdbEnvDir.isLmdbDataFile(file)) {
                                storeCount.increment();
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(final Path file,
                                                           final IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(final Path dir,
                                                              final IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (final IOException | RuntimeException e) {
                LOGGER.error("Error calculating disk usage for path {}",
                        searchResultStoreDir.normalize(), e);
                // Return -1 to indicate a failure
                totalSizeBytes.reset();
                totalSizeBytes.decrement();
                storeCount.reset();
                storeCount.decrement();
            }
        }, "Getting total size");

        LOGGER.debug("total size is {} in {}", totalSizeBytes, searchResultStoreDir);

        return new StoreSizeSummary(totalSizeBytes.longValue(), storeCount.intValue());
    }
}
