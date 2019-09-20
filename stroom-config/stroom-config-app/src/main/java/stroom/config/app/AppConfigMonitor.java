package stroom.config.app;

import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.config.FieldMapper;
import stroom.util.logging.LogUtil;

import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

@Singleton
public class AppConfigMonitor implements Managed {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppConfigMonitor.class);

    private final AppConfig appConfig;
    private final Path configFile;
    private final Path dirToWatch;
    private final ExecutorService executorService;
    private WatchService watchService = null;
    private Future<?> watcherFuture = null;

    public AppConfigMonitor(final AppConfig appConfig, final Path configFile) {
        this.appConfig = appConfig;
        this.configFile = configFile;

        if (!Files.isRegularFile(configFile)) {
            throw new RuntimeException(LogUtil.message("{} is not a regular file", configFile));
        }
        dirToWatch = configFile.getParent();
        if (!Files.isDirectory(dirToWatch)) {
            throw new RuntimeException(LogUtil.message("{} is not a directory", dirToWatch));
        }
        executorService = Executors.newSingleThreadExecutor();
    }


    /**
     * Starts the object. Called <i>before</i> the application becomes available.
     *
     * @throws Exception if something goes wrong; this will halt the application startup.
     */
    @Override
    public void start() throws Exception {
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            throw new RuntimeException(LogUtil.message("Error creating watch new service"), e);
        }

        dirToWatch.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

        // run the watcher in its own thread else it will block app startup
        watcherFuture = executorService.submit(() -> {
            WatchKey watchKey = null;

            LOGGER.info("Starting file modification watcher for {}", configFile.toAbsolutePath());
            while (true) {
                if (Thread.currentThread().isInterrupted()) {
                    LOGGER.warn("Thread interrupted, stopping watching directory {}", dirToWatch.toAbsolutePath());
                    break;
                }

                try {
                    // block until the watch service spots a change
                    watchKey = watchService.take();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    // continue to re-use the if block above
                    continue;
                }

                for (WatchEvent<?> event : watchKey.pollEvents()) {
                    if (event.kind().equals(OVERFLOW)) {
                        break;
                    }

                    final WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    final WatchEvent.Kind<Path> kind = pathEvent.kind();

                    if (kind.equals(StandardWatchEventKinds.ENTRY_MODIFY)) {
                        final Path modifiedFile = dirToWatch.resolve(pathEvent.context());

                        try {
                            // we don't care about changes to other files
                            if (Files.isRegularFile(modifiedFile) && Files.isSameFile(configFile, modifiedFile)) {
                                updateAppConfigFromFile();
                            }
                        } catch (IOException e) {
                            // Swallow error so future changes can be monitored.
                            LOGGER.error("Error comparing paths {} and {}", configFile, modifiedFile, e);
                        }
                    }
                }
                boolean isValid = watchKey.reset();
                if (!isValid) {
                    LOGGER.warn("Watch key is no longer valid, the watch service may have been stopped");
                    break;
                }
            }
        });
    }

    private void updateAppConfigFromFile() {
        final AppConfig newAppConfig;
        try {
            LOGGER.info("Updating app config from file {}", configFile.toAbsolutePath());
            newAppConfig = YamlUtil.readAppConfig(configFile);

            try {
                // Copy changed values from the newly modified appConfig into the guice bound one
                FieldMapper.copy(newAppConfig, this.appConfig);
            } catch (Throwable e) {
                // Swallow error as we don't want to break the app because the new config is bad
                // The admins can fix the problem and let it have another go.
                LOGGER.error("Error updating runtime configuration from file {}", configFile.toAbsolutePath(), e);
            }
        } catch (Throwable e) {
            // Swallow error as we don't want to break the app because the file is bad.
            LOGGER.error("Error parsing configuration from file {}", configFile.toAbsolutePath(), e);
        }
    }


    /**
     * Stops the object. Called <i>after</i> the application is no longer accepting requests.
     *
     * @throws Exception if something goes wrong.
     */
    @Override
    public void stop() throws Exception {
        LOGGER.info("Stopping file modification watcher for {}", configFile.toAbsolutePath());

        if (executorService != null) {
           if (watcherFuture != null) {
               watcherFuture.cancel(true);
           }
           executorService.shutdown();
        }
    }
}
