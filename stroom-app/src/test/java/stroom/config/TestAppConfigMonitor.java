package stroom.config;

import stroom.config.app.AppConfig;
import stroom.config.app.ConfigLocation;
import stroom.config.app.YamlUtil;
import stroom.config.global.impl.AppConfigMonitor;
import stroom.config.global.impl.ConfigMapper;
import stroom.config.global.impl.GlobalConfigService;
import stroom.config.global.impl.validation.ConfigValidator;
import stroom.test.AbstractCoreIntegrationTest;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.validation.Validator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;
import java.util.Random;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

class TestAppConfigMonitor extends AbstractCoreIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestAppConfigMonitor.class);

    @Inject
    private Validator validator;
    @Inject
    private GlobalConfigService globalConfigService;

    @Test
    void test() throws Exception {
        final Path devYamlFile = Paths.get(System.getProperty("user.dir"))
                .getParent()
                .resolve("stroom-app")
                .resolve("dev.yml");

        final Path devYamlCopyPath = getCurrentTestDir().resolve(devYamlFile.getFileName());

        // Make a copy of dev.yml so we can hack about with it
        Files.copy(devYamlFile, devYamlCopyPath);

        // We need to craft our own instances of these classes rather than use guice
        // so that we can use our own config file
        final AppConfig appConfig = YamlUtil.readAppConfig(devYamlCopyPath);
        final ConfigMapper configMapper = new ConfigMapper(appConfig);
        final ConfigLocation configLocation = new ConfigLocation(devYamlCopyPath);
        final ConfigValidator configValidator = new ConfigValidator(validator);

        final AppConfigMonitor appConfigMonitor = new AppConfigMonitor(
                appConfig, configLocation, configMapper, configValidator, globalConfigService);

        // start watching our copied file for changes, the start is async
        appConfigMonitor.start();

        // with for the monitoring to start
        while (!appConfigMonitor.isRunning()) {
            Thread.sleep(100);
        }
        // isRunning is set to true just before watchService.take() is called so add an extra little sleep
        // to be on the safe side.
        Thread.sleep(100);

        doFileUpdateTest(devYamlCopyPath, appConfig);

        // Have a quick snooze then test another file change to be sure it can cope with more than
        // one change.
        Thread.sleep(200);

        doFileUpdateTest(devYamlCopyPath, appConfig);

        appConfigMonitor.stop();
    }

    private void doFileUpdateTest(final Path devYamlCopyPath,
                                  final AppConfig appConfig) throws IOException, InterruptedException {

        final String newPathValue = "new_value_" + new Random().nextInt(100000000);
        LOGGER.info("---------------------------------------------------------------");
        LOGGER.info("Updating value in file to {}", newPathValue);

        Assertions.assertThat(appConfig.getPathConfig().getTemp())
                .isNotEqualTo(newPathValue);

        final Pattern pattern = Pattern.compile("temp:\\s*\"[^\"]+\"");
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
        Assertions.assertThat(optMatchResult).isPresent();

        // We need to sleep here to give the config monitor time to start as it monitors asynchronously.
        Thread.sleep(3000);

        // Update the config file with our new value
        final String updatedDevYamlStr = pattern.matcher(devYamlStr)
                .replaceAll("temp: \"" + newPathValue + "\"");

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
        while (!appConfig.getPathConfig().getTemp().equals(newPathValue)
                && Instant.now().isBefore(timeOutTime)) {

            LOGGER.debug("value {}", appConfig.getPathConfig().getTemp());
            Thread.sleep(200);
        }

        Assertions.assertThat(appConfig.getPathConfig().getTemp())
                .isEqualTo(newPathValue);
    }
}