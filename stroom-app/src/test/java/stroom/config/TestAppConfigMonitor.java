package stroom.config;

import stroom.config.app.AppConfig;
import stroom.config.app.ConfigHolder;
import stroom.config.global.impl.AppConfigMonitor;
import stroom.config.global.impl.ConfigMapper;
import stroom.config.global.impl.ConfigProvidersModule;
import stroom.test.BootstrapTestModule;
import stroom.ui.config.shared.UiConfig;
import stroom.util.config.AbstractFileChangeMonitor;
import stroom.util.config.AppConfigValidator;
import stroom.util.config.ConfigLocation;
import stroom.util.logging.LogUtil;
import stroom.util.yaml.YamlUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Random;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.validation.Validator;

class TestAppConfigMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestAppConfigMonitor.class);

    private static final String YAML_KEY = "htmlTitle";

    @Inject
    private Validator validator;
    @Inject
    private ConfigMapper configMapper;
    @Inject
    private ConfigHolder configHolder;
    @Inject
    private Provider<UiConfig> uiConfigProvider;

    @BeforeEach
    void before() {
        final Injector injector = Guice.createInjector(
                new BootstrapTestModule(),
                new ConfigProvidersModule()
        );
        injector.injectMembers(this);
    }

    @Test
    void test() throws Exception {
        final Path yamlFile = configHolder.getConfigFile();
        final AppConfig appConfig = configHolder.getBootStrapConfig();

        LOGGER.info("Testing with config file {}", yamlFile.toAbsolutePath().normalize());

        // Wrap the app config so it looks like a serialised Config
        // We don't want any of the dropwiz stuff in the file to avoid (de)ser issues.
        final DummyConfig dummyConfig = new DummyConfig(configHolder.getBootStrapConfig());
        final ObjectMapper yamlObjectMapper = YamlUtil.createYamlObjectMapper();
        yamlObjectMapper.writeValue(configHolder.getConfigFile().toFile(), dummyConfig);

        Assertions.assertThat(yamlFile)
                .isRegularFile();

        Assertions.assertThat(yamlFile)
                .isNotEmptyFile();

        final ConfigLocation configLocation = new ConfigLocation(yamlFile);
        final AppConfigValidator appConfigValidator = new AppConfigValidator(validator);

        final AbstractFileChangeMonitor appConfigMonitor = new AppConfigMonitor(
                configLocation, configMapper, appConfigValidator);

        // start watching our copied file for changes, the start is async
        appConfigMonitor.start();

        // wait for the monitoring to start
        while (!appConfigMonitor.isRunning()) {
            Thread.sleep(100);
        }
        // isRunning is set to true just before watchService.take() is called so add an extra little sleep
        // to be on the safe side.
        Thread.sleep(100);

        doFileUpdateTest(yamlFile, appConfig);

        // Have a quick snooze then test another file change to be sure it can cope with more than
        // one change.
        Thread.sleep(200);

        doFileUpdateTest(yamlFile, appConfig);

        appConfigMonitor.stop();
    }

    private void doFileUpdateTest(final Path devYamlCopyPath,
                                  final AppConfig appConfig) throws IOException, InterruptedException {

        final String newValue = Integer.toString(new Random().nextInt(10000000));

        LOGGER.info("---------------------------------------------------------------");
        LOGGER.info("Updating value of {} in file to {}", YAML_KEY, newValue);

        Assertions.assertThat(uiConfigProvider.get().getHtmlTitle())
                .isNotEqualTo(newValue);

        final Pattern pattern = Pattern.compile(YAML_KEY + ":\\s*\"[^\"]+\"");
        final String devYamlStr = Files.readString(devYamlCopyPath);
        final Optional<MatchResult> optMatchResult = pattern.matcher(devYamlStr)
                .results()
                .findFirst();

        final Runnable grepFile = () -> {
            try {
                String str = pattern.matcher(Files.readString(devYamlCopyPath))
                        .results()
                        .findFirst()
                        .orElseThrow()
                        .group(0);
                LOGGER.debug("Found str [{}] in file {}", str, devYamlCopyPath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        // Make sure the temp prop is in the file
        Assertions.assertThat(optMatchResult)
                .withFailMessage(LogUtil.message(
                        "Can't find pattern {} in file {}",
                        pattern.toString(),
                        devYamlCopyPath.toAbsolutePath().normalize()))
                .isPresent();

        // We need to sleep here to give the config monitor time to start as it monitors asynchronously.
        Thread.sleep(3000);

        // Update the config file with our new value for a key (key must be uniquely named)
        final String updatedDevYamlStr = pattern.matcher(devYamlStr)
                .replaceAll(YAML_KEY + ": \"" + newValue + "\"");

        // Ensure the replace worked by compare old file content to new
        Assertions.assertThat(updatedDevYamlStr).isNotEqualTo(devYamlStr);

        // Now modify the file which the watcher should detect
        Files.writeString(devYamlCopyPath, updatedDevYamlStr);
        LOGGER.debug("Modified file {}", devYamlCopyPath.toAbsolutePath());

        // Synchronous check of what is in the file for debugging
        grepFile.run();

        // Now keep checking if the appConfig has been updated, or we timeout
        Instant startTime = Instant.now();
        Instant timeOutTime = startTime.plusSeconds(10);
        while (!uiConfigProvider.get().getHtmlTitle().equals(newValue)
                && Instant.now().isBefore(timeOutTime)) {

            LOGGER.debug("value {}", uiConfigProvider.get().getHtmlTitle());
            Thread.sleep(200);
        }

        Assertions.assertThat(uiConfigProvider.get().getHtmlTitle())
                .isEqualTo(newValue);
    }

    private static class DummyConfig {

        @JsonProperty("appConfig")
        private final AppConfig appConfig;

        @JsonCreator
        public DummyConfig(@JsonProperty("appConfig") final AppConfig appConfig) {
            this.appConfig = appConfig;
        }

        public AppConfig getAppConfig() {
            return appConfig;
        }
    }
}
