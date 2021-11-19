package stroom.config;

import stroom.config.app.AppConfig;
import stroom.config.app.ConfigHolder;
import stroom.config.app.YamlUtil;
import stroom.config.global.impl.AppConfigMonitor;
import stroom.config.global.impl.GlobalConfigService;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.config.AbstractFileChangeMonitor;
import stroom.util.config.AppConfigValidator;
import stroom.util.config.ConfigLocation;
import stroom.util.io.FileUtil;
import stroom.util.logging.LogUtil;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.validation.Validator;

class TestAppConfigMonitor extends AbstractCoreIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestAppConfigMonitor.class);

    @Inject
    private Validator validator;
    @Inject
    private GlobalConfigService globalConfigService;
    @Inject
    private ConfigHolder configHolder;

    @Test
    void test() throws Exception {
        final Path yamlFile = configHolder.getConfigFile();
        final AppConfig appConfig = configHolder.getAppConfig();
//        final Path yamlFile = Paths.get(System.getProperty("user.dir"))
//                .getParent()
//                .resolve("stroom-app")
//                .resolve("dev.yml");

        LOGGER.info("Testing with config file {}", yamlFile.toAbsolutePath().normalize());

        // AppConfigTestModule creates an appConfig instance but doesn't create the file.
        YamlUtil.writeConfig(configHolder.getAppConfig(), configHolder.getConfigFile());

        // Remove all the dropwiz stuff from the file to avoid (de)ser issues.
        final List<String> outputLines = Files.lines(yamlFile)
                .takeWhile(line -> !line.startsWith("server:"))
                .collect(Collectors.toList());
        Files.write(yamlFile, outputLines, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);

        Assertions.assertThat(yamlFile)
                .isRegularFile();

        final Path devYamlCopyPath = getCurrentTestDir().resolve(yamlFile.getFileName());

        LOGGER.info("devYamlCopyPath {}", devYamlCopyPath.toAbsolutePath().normalize());

        // Make a copy of dev.yml so we can hack about with it
        Files.copy(yamlFile, devYamlCopyPath);

        // We need to craft our own instances of these classes rather than use guice
        // so that we can use our own config file
//        final AppConfig appConfig = YamlUtil.readAppConfig(devYamlCopyPath);

        // Create the dirs so validation doesn't fail
        final Path tempDir = Path.of(FileUtil.replaceHome(appConfig.getPathConfig().getTemp()));
        LOGGER.info("Ensuring temp directory {}", tempDir.toAbsolutePath().normalize());
        Files.createDirectories(tempDir);

        final Path homeDir = Path.of(FileUtil.replaceHome(appConfig.getPathConfig().getHome()));
        LOGGER.info("Ensuring home directory {}", homeDir.toAbsolutePath().normalize());
        Files.createDirectories(homeDir);

        final ConfigLocation configLocation = new ConfigLocation(devYamlCopyPath);
        final AppConfigValidator appConfigValidator = new AppConfigValidator(validator);

        final AbstractFileChangeMonitor appConfigMonitor = new AppConfigMonitor(
                appConfig, configLocation, globalConfigService, appConfigValidator);

        // start watching our copied file for changes, the start is async
        appConfigMonitor.start();

        // wait for the monitoring to start
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

        final Path newPath = Files.createTempDirectory("test")
                .toAbsolutePath()
                .normalize();
        final String newPathStr = newPath.toString();

        Assertions.assertThat(newPath)
                .isDirectory();

        LOGGER.info("---------------------------------------------------------------");
        LOGGER.info("Updating value in file to {}", newPathStr);

        Assertions.assertThat(appConfig.getPathConfig().getTemp())
                .isNotEqualTo(newPathStr);

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
        Assertions.assertThat(optMatchResult)
                .withFailMessage(LogUtil.message(
                        "Can't find pattern {} in file {}",
                        pattern.toString(),
                        devYamlCopyPath.toAbsolutePath().normalize()))
                .isPresent();

        // We need to sleep here to give the config monitor time to start as it monitors asynchronously.
        Thread.sleep(3000);

        // Update the config file with our new value
        final String updatedDevYamlStr = pattern.matcher(devYamlStr)
                .replaceAll("temp: \"" + newPathStr + "\"");

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
        while (!appConfig.getPathConfig().getTemp().equals(newPathStr)
                && Instant.now().isBefore(timeOutTime)) {

            LOGGER.debug("value {}", appConfig.getPathConfig().getTemp());
            Thread.sleep(200);
        }

        Assertions.assertThat(appConfig.getPathConfig().getTemp())
                .isEqualTo(newPathStr);

        Files.deleteIfExists(Path.of(newPathStr));
    }
}
