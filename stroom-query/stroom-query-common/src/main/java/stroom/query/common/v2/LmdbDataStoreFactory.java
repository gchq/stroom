package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.lmdb.LmdbConfig;
import stroom.lmdb.LmdbEnv;
import stroom.lmdb.LmdbEnvFactory;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.TableSettings;
import stroom.util.NullSafe;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

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
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton // To ensure the localDir delete is done only once and before store creation
public class LmdbDataStoreFactory implements DataStoreFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbDataStoreFactory.class);

    private final LmdbEnvFactory lmdbEnvFactory;
    private final Provider<ResultStoreConfig> resultStoreConfigProvider;
    private final Provider<AnalyticStoreConfig> analyticStoreConfigProvider;
    private final Provider<Executor> executorProvider;
    private final Provider<Serialisers> serialisersProvider;
    private final Path searchResultStoreDir;
    private final Path analyticResultStoreDir;

    @Inject
    public LmdbDataStoreFactory(final LmdbEnvFactory lmdbEnvFactory,
                                final Provider<ResultStoreConfig> resultStoreConfigProvider,
                                final Provider<AnalyticStoreConfig> analyticStoreConfigProvider,
                                final PathCreator pathCreator,
                                final Provider<Executor> executorProvider,
                                final Provider<Serialisers> serialisersProvider) {
        this.lmdbEnvFactory = lmdbEnvFactory;
        this.resultStoreConfigProvider = resultStoreConfigProvider;
        this.analyticStoreConfigProvider = analyticStoreConfigProvider;
        this.executorProvider = executorProvider;
        this.serialisersProvider = serialisersProvider;

        // This config prop requires restart, so we can hold on to it
        this.searchResultStoreDir = getLocalDir(resultStoreConfigProvider.get(), pathCreator);
        this.analyticResultStoreDir = getLocalDir(analyticStoreConfigProvider.get(), pathCreator);

        // As search result stores are transient they serve no purpose after shutdown so delete any that
        // may still be there
        cleanStoresDir(searchResultStoreDir);
    }

    @Override
    public DataStore create(final QueryKey queryKey,
                            final String componentId,
                            final TableSettings tableSettings,
                            final FieldIndex fieldIndex,
                            final Map<String, String> paramMap,
                            final DataStoreSettings dataStoreSettings,
                            final ErrorConsumer errorConsumer) {

        final ResultStoreConfig resultStoreConfig = resultStoreConfigProvider.get();
        if (!resultStoreConfig.isOffHeapResults()) {
            if (dataStoreSettings.isProducePayloads()) {
                throw new RuntimeException("MapDataStore cannot produce payloads");
            }

            return new MapDataStore(
                    serialisersProvider.get(),
                    tableSettings,
                    fieldIndex,
                    paramMap,
                    dataStoreSettings);
        } else {
            final String subDirectory = queryKey + "_" + componentId + "_" + UUID.randomUUID();
            final DataStoreSettings modifiedDataStoreSettings =
                    dataStoreSettings.copy().subDirectory(subDirectory).build();

            return new LmdbDataStore(
                    serialisersProvider.get(),
                    lmdbEnvFactory,
                    resultStoreConfig,
                    queryKey,
                    componentId,
                    tableSettings,
                    fieldIndex,
                    paramMap,
                    modifiedDataStoreSettings,
                    executorProvider,
                    errorConsumer);
        }
    }

    public LmdbDataStore createAnalyticLmdbDataStore(final QueryKey queryKey,
                                                     final String componentId,
                                                     final TableSettings tableSettings,
                                                     final FieldIndex fieldIndex,
                                                     final Map<String, String> paramMap,
                                                     final DataStoreSettings dataStoreSettings,
                                                     final ErrorConsumer errorConsumer) {

        final AnalyticStoreConfig storeConfig = analyticStoreConfigProvider.get();
        return new LmdbDataStore(
                serialisersProvider.get(),
                lmdbEnvFactory,
                storeConfig,
                queryKey,
                componentId,
                tableSettings,
                fieldIndex,
                paramMap,
                dataStoreSettings,
                executorProvider,
                errorConsumer);
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
                            if (LmdbEnv.isLmdbDataFile(file)) {
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
            } catch (IOException | RuntimeException e) {
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
