package stroom.receive.common;

import stroom.util.NullSafe;
import stroom.util.io.SimplePathCreator;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
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
            && path.getFileName().endsWith(".json");

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
    void onInitialisation() {
        processAllFiles();
    }

    @Override
    void onEntryModify(final Path path) {
        LOGGER.debug("onEntryModify - path: {}", path);
        if (path != null) {
            processFile(path);
        }
    }

    @Override
    void onEntryCreate(final Path path) {
        LOGGER.debug("onEntryCreate - path: {}", path);
        if (path != null) {
            processFile(path);
        }
    }

    @Override
    void onEntryDelete(final Path path) {
        LOGGER.debug("onEntryDelete - path: {}", path);
        if (path != null) {
            dataFeedKeyServiceProvider.get().removeKeysForFile(path);
        }
    }

    @Override
    void onOverflow() {
        LOGGER.debug("onOverflow");
        processAllFiles();
    }

    private void processAllFiles() {
        // Re-scan the whole directory. The addDataFeedKeys method is idempotent
        LOGGER.info("Reading all data feed key files in {}", dirToWatch);
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(dirToWatch)) {
            AtomicInteger counter = new AtomicInteger();
            dirStream.forEach(path -> {
                if (fileIncludeFilter == null || fileIncludeFilter.test(path)) {
                    processFile(path);
                    counter.incrementAndGet();
                }
            });
            LOGGER.info("Completed reading {} data feed key files in {}", counter, dirToWatch);
        } catch (IOException e) {
            LOGGER.error("Error reading contents of " + dirToWatch, e);
        }
    }

    private void processFile(final Path path) {
        if (path != null && Files.isRegularFile(path)) {
            final ObjectMapper mapper = JsonUtil.getMapper();
            try (InputStream fileStream = new FileInputStream(path.toFile())) {
                try {
                    final DataFeedKeys dataFeedKeys = mapper.readValue(fileStream, DataFeedKeys.class);
                    dataFeedKeyServiceProvider.get().addDataFeedKeys(dataFeedKeys, path);
                } catch (IOException e) {
                    LOGGER.error("Error parsing file {}: {}", path, e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            } catch (IOException e) {
                LOGGER.error("Error reading file {}: {}", path, e.getMessage(), e);
            }
        }
    }
}
