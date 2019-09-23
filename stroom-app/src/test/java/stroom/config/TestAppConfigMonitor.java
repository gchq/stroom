package stroom.config;

import org.junit.Ignore;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.app.AppConfig;
import stroom.config.app.AppConfigMonitor;
import stroom.config.app.YamlUtil;
import stroom.test.AbstractCoreIntegrationTest;

import java.nio.file.Path;
import java.nio.file.Paths;

class TestAppConfigMonitor extends AbstractCoreIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestAppConfigMonitor.class);

    @Ignore
    @Test
    void test() throws Exception {

        Path configFile = Paths.get("/home/dev/tmp/dev.yml");

        AppConfig appConfig = YamlUtil.readAppConfig(configFile);
        AppConfigMonitor appConfigMonitor = new AppConfigMonitor(appConfig, configFile);

        appConfigMonitor.start();

        Thread.sleep(999999999999L);

    }
}