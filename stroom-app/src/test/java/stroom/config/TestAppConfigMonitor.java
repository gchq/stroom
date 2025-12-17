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

package stroom.config;

import stroom.config.app.AppConfig;
import stroom.config.app.ConfigHolder;
import stroom.config.app.StroomYamlUtil;
import stroom.config.global.impl.AppConfigMonitor;
import stroom.config.global.impl.ConfigMapper;
import stroom.config.global.impl.ConfigProvidersModule;
import stroom.test.BootstrapTestModule;
import stroom.ui.config.shared.UiConfig;
import stroom.util.config.AbstractFileChangeMonitor;
import stroom.util.config.AppConfigValidator;
import stroom.util.config.ConfigLocation;
import stroom.util.logging.LogUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.validation.Validator;
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

class TestAppConfigMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestAppConfigMonitor.class);

    private static final String YAML_KEY = "htmlTitle";
    protected static final Pattern YAML_KEY_PATTERN = Pattern.compile(YAML_KEY + ":\\s*\"[^\"]+\"");

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

        // AppConfigTestModule creates an appConfig instance but doesn't create the file.
        StroomYamlUtil.writeAppConfig(configHolder.getBootStrapConfig(), configHolder.getConfigFile());

        final String yamlAfter = Files.readString(yamlFile);
        LOGGER.info("yamlAfter:\n{}", yamlAfter);

        Assertions.assertThat(yamlFile)
                .isRegularFile();

        Assertions.assertThat(yamlFile)
                .isNotEmptyFile();

        final ConfigLocation configLocation = new ConfigLocation(yamlFile);
        final AppConfigValidator appConfigValidator = new AppConfigValidator(validator);

        final AbstractFileChangeMonitor appConfigMonitor = new AppConfigMonitor(
                configLocation, configMapper, appConfigValidator);

        // start watching our created yaml file for changes, the start is async
        appConfigMonitor.start();

        // wait for the monitoring to start
        while (!appConfigMonitor.isRunning()) {
            Thread.sleep(100);
        }
        // isRunning is set to true just before watchService.take() is called so add an extra little sleep
        // to be on the safe side.
        Thread.sleep(200);

        doFileUpdateTest(yamlFile);

        // Have a quick snooze then test another file change to be sure it can cope with more than
        // one change.
        Thread.sleep(200);

        doFileUpdateTest(yamlFile);

        appConfigMonitor.stop();
    }

    private void doFileUpdateTest(final Path yamlFile) throws IOException, InterruptedException {

        final String newValue = Integer.toString(new Random().nextInt(10000000));

        LOGGER.info("---------------------------------------------------------------");
        LOGGER.info("Updating value of {} in file to {}", YAML_KEY, newValue);

        Assertions.assertThat(uiConfigProvider.get().getHtmlTitle())
                .isNotEqualTo(newValue);

        final String devYamlStr = Files.readString(yamlFile);
        final Optional<MatchResult> optMatchResult = YAML_KEY_PATTERN.matcher(devYamlStr)
                .results()
                .findFirst();

        grepFile(yamlFile);

        // Make sure the prop is in the file
        Assertions.assertThat(optMatchResult)
                .withFailMessage(LogUtil.message(
                        "Can't find pattern {} in file {}",
                        YAML_KEY_PATTERN.toString(),
                        yamlFile.toAbsolutePath().normalize()))
                .isPresent();

        // We need to sleep here to give the config monitor time to start as it monitors asynchronously.
//        Thread.sleep(3000);

        // Update the config file with our new value for a key (key must be uniquely named)
        final String updatedDevYamlStr = YAML_KEY_PATTERN.matcher(devYamlStr)
                .replaceAll(YAML_KEY + ": \"" + newValue + "\"");

        // Ensure the replace worked by compare old file content to new
        Assertions.assertThat(updatedDevYamlStr)
                .isNotEqualTo(devYamlStr);

        // Now modify the file which the watcher should detect
        Files.writeString(yamlFile, updatedDevYamlStr);
        LOGGER.info("Modified file {}, set {} to {}",
                yamlFile.toAbsolutePath(),
                YAML_KEY,
                newValue);

        // Synchronous check of what is in the file for debugging
        grepFile(yamlFile);

        // Now keep checking if the appConfig has been updated, or we timeout
        final Instant startTime = Instant.now();
        final Instant timeOutTime = startTime.plusSeconds(10);
        LOGGER.info("Waiting for config object to be updated by AppConfigMonitor");
        // AppConfigMonitor waits 2s after detecting the change before it actually updates the object
        // so need to allow for that
        while (!uiConfigProvider.get().getHtmlTitle().equals(newValue)) {
            if (Instant.now().isAfter(timeOutTime)) {
                Assertions.fail("Timed out waiting for {} to equal {}",
                        uiConfigProvider.get().getHtmlTitle(),
                        newValue);
            }

            LOGGER.debug("value {}", uiConfigProvider.get().getHtmlTitle());
            Thread.sleep(200);
        }

        Assertions.assertThat(uiConfigProvider.get().getHtmlTitle())
                .isEqualTo(newValue);
    }

    private void grepFile(final Path file) {
        try {
            final String str = YAML_KEY_PATTERN.matcher(Files.readString(file))
                    .results()
                    .findFirst()
                    .orElseThrow()
                    .group(0);
            LOGGER.info("Found str [{}] in file {}", str, file);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
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
