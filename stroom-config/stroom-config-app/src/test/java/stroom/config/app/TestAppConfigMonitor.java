package stroom.config.app;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

class TestAppConfigMonitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestAppConfigMonitor.class);

    @Disabled
    @Test
    void test() throws Exception {

        Path configFile = Paths.get("/home/dev/tmp/dev.yml");

        AppConfig appConfig = YamlUtil.readAppConfig(configFile);
        AppConfigMonitor appConfigMonitor = new AppConfigMonitor(appConfig, configFile);

        appConfigMonitor.start();

        Thread.sleep(999999999999L);

    }
}