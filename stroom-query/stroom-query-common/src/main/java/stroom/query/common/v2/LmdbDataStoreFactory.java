package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.lmdb.LmdbConfig;
import stroom.lmdb.LmdbEnvFactory;
import stroom.query.api.v2.TableSettings;
import stroom.util.NullSafe;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton // To ensure the localDir delete is done only once and before store creation
public class LmdbDataStoreFactory implements DataStoreFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbDataStoreFactory.class);

    private final LmdbEnvFactory lmdbEnvFactory;
    private final ResultStoreConfig resultStoreConfig;

    @Inject
    public LmdbDataStoreFactory(final LmdbEnvFactory lmdbEnvFactory,
                                final ResultStoreConfig resultStoreConfig,
                                final PathCreator pathCreator) {
        this.lmdbEnvFactory = lmdbEnvFactory;
        this.resultStoreConfig = resultStoreConfig;
        // As result stores are transient they serve no purpose after shutdown so delete any that
        // may still be there
        cleanStoresDir(pathCreator);
    }

    @Override
    public DataStore create(final String queryKey,
                            final String componentId,
                            final TableSettings tableSettings,
                            final FieldIndex fieldIndex,
                            final Map<String, String> paramMap,
                            final Sizes maxResults,
                            final Sizes storeSize) {

        if (!resultStoreConfig.isOffHeapResults()) {
            return new MapDataStore(
                    tableSettings,
                    fieldIndex,
                    paramMap,
                    maxResults,
                    storeSize);
        } else {
            return new LmdbDataStore(
                    lmdbEnvFactory,
                    resultStoreConfig,
                    queryKey,
                    componentId,
                    tableSettings,
                    fieldIndex,
                    paramMap,
                    maxResults,
                    storeSize);
        }
    }

    private void cleanStoresDir(final PathCreator pathCreator) {
        final String dirFromConfig = NullSafe.get(
                resultStoreConfig,
                ResultStoreConfig::getLmdbConfig,
                LmdbConfig::getLocalDir);

        Objects.requireNonNull(dirFromConfig);

        final String localDirStr = pathCreator.makeAbsolute(
                pathCreator.replaceSystemProperties(dirFromConfig));
        final Path localDir = Paths.get(localDirStr);

        LOGGER.info("Deleting contents of dir {}", localDir);
        // Delete contents.
        if (!FileUtil.deleteContents(localDir)) {
            throw new RuntimeException(LogUtil.message("Error deleting contents of {}", localDir));
        }
    }
}
