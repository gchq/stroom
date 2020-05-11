package stroom.config.global.impl;

import stroom.config.app.AppConfig;
import stroom.config.app.ConfigLocation;
import stroom.config.app.YamlUtil;
import stroom.config.global.impl.validation.ConfigValidator;
import stroom.util.HasHealthCheck;
import stroom.util.config.FieldMapper;
import stroom.util.logging.LogUtil;
import stroom.util.shared.AbstractConfig;

import com.codahale.metrics.health.HealthCheck;
import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

@Singleton
public class AppConfigMonitor implements Managed, HasHealthCheck {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppConfigMonitor.class);

    private final AppConfig appConfig;
    private final ConfigMapper configMapper;
    private final ConfigValidator configValidator;
    private final GlobalConfigService globalConfigService;

    private final Path configFile;
    private final Path dirToWatch;
    private final ExecutorService executorService;
    private WatchService watchService = null;
    private Future<?> watcherFuture = null;
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private final boolean isValidFile;
    private final AtomicBoolean isFileReadScheduled = new AtomicBoolean(false);
    private final List<String> errors = new ArrayList<>();

    private static final long DELAY_BEFORE_FILE_READ_MS = 1_000;

    @Inject
    public AppConfigMonitor(final AppConfig appConfig,
                            final ConfigLocation configLocation,
                            final ConfigMapper configMapper,
                            final ConfigValidator configValidator,
                            final GlobalConfigService globalConfigService) {
        this.appConfig = appConfig;
        this.configFile = configLocation.getConfigFilePath();
        this.configMapper = configMapper;
        this.configValidator = configValidator;
        this.globalConfigService = globalConfigService;

        if (Files.isRegularFile(configFile)) {
            isValidFile = true;

            dirToWatch = configFile.getParent();
            if (!Files.isDirectory(dirToWatch)) {
                throw new RuntimeException(LogUtil.message("{} is not a directory", dirToWatch));
            }
            executorService = Executors.newSingleThreadExecutor();
        } else {
            isValidFile = false;
            dirToWatch = null;
            executorService = null;
        }
    }


    /**
     * Starts the object. Called <i>before</i> the application becomes available.
     */
    @Override
    public void start() {
        if (isValidFile) {
            try {
                startWatcher();
            } catch (Exception e) {
                // Swallow and log as we don't want to stop the app from starting just for this
                errors.add(e.getMessage());
                LOGGER.error("Unable to start config file monitor due to [{}]. Changes to {} will not be monitored.",
                        e.getMessage(), configFile.toAbsolutePath().normalize(), e);
            }
        } else {
            LOGGER.error("Unable to start watcher as {} is not a valid file", configFile.toAbsolutePath().normalize());
        }
    }

    private void startWatcher() throws IOException {
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            throw new RuntimeException(LogUtil.message("Error creating watch new service, {}", e.getMessage()), e);
        }

        dirToWatch.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

        // run the watcher in its own thread else it will block app startup
        watcherFuture = executorService.submit(() -> {
            WatchKey watchKey = null;

            LOGGER.info("Starting config file modification watcher for {}", configFile.toAbsolutePath().normalize());
            while (true) {
                if (Thread.currentThread().isInterrupted()) {
                    LOGGER.debug("Thread interrupted, stopping watching directory {}", dirToWatch.toAbsolutePath().normalize());
                    break;
                }

                try {
                    isRunning.compareAndSet(false, true);
                    // block until the watch service spots a change
                    watchKey = watchService.take();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    // continue to re-use the if block above
                    continue;
                }

                for (WatchEvent<?> event : watchKey.pollEvents()) {
                    if (LOGGER.isDebugEnabled()) {
                        if (event == null) {
                            LOGGER.debug("Event is null");
                        } else {
                            String name = event.kind() != null ? event.kind().name() : "kind==null";
                            String type = event.kind() != null ? event.kind().type().getSimpleName() : "kind==null";
                            LOGGER.debug("Dir watch event {}, {}, {}", name, type, event.context());
                        }
                    }

                    if (event.kind().equals(OVERFLOW)) {
                        LOGGER.warn("{} event detected breaking out. Retry config file change", OVERFLOW.name());
                        break;
                    }
                    if (event.kind() != null && Path.class.isAssignableFrom(event.kind().type())) {
                        handleWatchEvent((WatchEvent<Path>) event);
                    } else {
                        LOGGER.debug("Not an event we care about");
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

    private void handleWatchEvent(final WatchEvent<Path> pathEvent) {
        final WatchEvent.Kind<Path> kind = pathEvent.kind();

        // Only trigger on modify events and when count is one to avoid repeated events
        if (kind.equals(StandardWatchEventKinds.ENTRY_MODIFY)) {
            final Path modifiedFile = dirToWatch.resolve(pathEvent.context());

            try {
                // we don't care about changes to other files
                if (Files.isRegularFile(modifiedFile) && Files.isSameFile(configFile, modifiedFile)) {
                    LOGGER.info("Change detected to config file {}", configFile.toAbsolutePath().normalize());
                    scheduleUpdateIfRequired();
                }
            } catch (IOException e) {
                // Swallow error so future changes can be monitored.
                LOGGER.error("Error comparing paths {} and {}", configFile, modifiedFile, e);
            }
        }
    }

    private synchronized void scheduleUpdateIfRequired() {

        // When a file is changed the filesystem can trigger two changes, one to change the file content
        // and another to change the file access time. To prevent a duplicate read we delay the read
        // a bit so we can have many changes during that delay period but with only one read of the file.
        if (isFileReadScheduled.compareAndSet(false, true)) {
            LOGGER.info("Scheduling update of application config from file in {}ms", DELAY_BEFORE_FILE_READ_MS);
            CompletableFuture.delayedExecutor(DELAY_BEFORE_FILE_READ_MS, TimeUnit.MILLISECONDS)
                .execute(() -> {
                    try {
                        updateAppConfigFromFile();
                    } finally {
                        isFileReadScheduled.set(false);
                    }
                });
        }
    }

    private synchronized void updateAppConfigFromFile() {
        final AppConfig newAppConfig;
        try {
            LOGGER.info("Reading updated config file");
            newAppConfig = YamlUtil.readAppConfig(configFile);

            final ConfigValidator.Result result = validateNewConfig(newAppConfig);

            if (result.hasErrors()) {
                LOGGER.error("Unable to update application config from file {} because it failed validation. " +
                    "Fix the errors and save the file.", configFile.toAbsolutePath().normalize().toString());
            } else {
                try {
                    // Don't have to worry about the DBV config merging that goes on in DataSourceFactoryImpl
                    // as that doesn't mutate the config objects

                    final AtomicInteger updateCount = new AtomicInteger(0);
                    final FieldMapper.UpdateAction updateAction = (destParent, prop, sourcePropValue, destPropValue) -> {
                        final String fullPath = ((AbstractConfig)destParent).getFullPath(prop.getName());
                        LOGGER.info("  Updating config value of {} from [{}] to [{}]",
                            fullPath, destPropValue, sourcePropValue);
                        updateCount.incrementAndGet();
                    };

                    LOGGER.info("Updating application config from file.");
                    // Copy changed values from the newly modified appConfig into the guice bound one
                    FieldMapper.copy(newAppConfig, this.appConfig, updateAction);

                    // Update the config objects using the DB as the removal of a yaml value may trigger
                    // a DB value to be effective
                    LOGGER.info("Completed updating application config from file. Changes: {}", updateCount.get());
                    globalConfigService.updateConfigObjects();

                } catch (Throwable e) {
                    // Swallow error as we don't want to break the app because the new config is bad
                    // The admins can fix the problem and let it have another go.
                    LOGGER.error("Error updating runtime configuration from file {}",
                        configFile.toAbsolutePath().normalize(), e);
                }
            }
        } catch (Throwable e) {
            // Swallow error as we don't want to break the app because the file is bad.
            LOGGER.error("Error parsing configuration from file {}",
                configFile.toAbsolutePath().normalize(), e);
        }
    }

    private ConfigValidator.Result validateNewConfig(final AppConfig newAppConfig) {
        // Initialise a ConfigMapper on the new config tree so it will decorate all the paths,
        // i.e. call setBasePath on each branch in the newAppConfig tree so if we get any violations we
        // can log their locations with full paths.
        new ConfigMapper(newAppConfig);

        LOGGER.info("Validating modified config file");
        final ConfigValidator.Result result = configValidator.validate(newAppConfig);
        result.handleViolations(ConfigValidator::logConstraintViolation);

        LOGGER.info("Completed validation of application configuration, errors: {}, warnings: {}",
            result.getErrorCount(),
            result.getWarningCount());
        return result;
    }


    /**
     * Stops the object. Called <i>after</i> the application is no longer accepting requests.
     *
     * @throws Exception if something goes wrong.
     */
    @Override
    public void stop() throws Exception {
        if (isValidFile) {
            LOGGER.info("Stopping file modification watcher for {}", configFile.toAbsolutePath().normalize());

            if (watchService != null) {
                watchService.close();
            }
            if (executorService != null) {
                watchService.close();
                if (watcherFuture != null && !watcherFuture.isCancelled() && !watcherFuture.isDone()) {
                    watcherFuture.cancel(true);
                }
                executorService.shutdown();
            }
        }
        isRunning.set(false);
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    @Override
    public HealthCheck.Result getHealth() {
        HealthCheck.ResultBuilder resultBuilder = HealthCheck.Result.builder();

        // isRunning will only be true if the file is also present and valid
        if (isRunning.get()) {
            resultBuilder.healthy();
        } else {
            resultBuilder
                    .unhealthy()
                    .withDetail("errors", errors);
        }

        return resultBuilder
                .withDetail("configFilePath", configFile != null
                    ? configFile.toAbsolutePath().normalize().toString()
                    : null)
                .withDetail("isRunning", isRunning)
                .withDetail("isValidFile", isValidFile)
                .build();
    }
}
