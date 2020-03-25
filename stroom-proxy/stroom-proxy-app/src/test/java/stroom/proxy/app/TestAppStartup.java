package stroom.proxy.app;

import io.dropwizard.testing.junit5.DropwizardAppExtension;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestAppStartup extends AbstractApplicationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestAppStartup.class);

    private static final DropwizardAppExtension<Config> DROPWIZARD = new DropwizardAppExtension<Config>(
        App.class,
        getConfig());

    @Test
    public void testAppStartup() {
        LOGGER.info("Nothing to test, just making sure proxy spins up without error");
    }

}
