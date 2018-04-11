package stroom.persist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.lifecycle.StroomShutdown;
import stroom.util.lifecycle.StroomStartup;

import javax.inject.Inject;

public class PersistLifecycle {
    private static final Logger LOGGER = LoggerFactory.getLogger(PersistLifecycle.class);
    private final PersistService persistService;

    @Inject
    PersistLifecycle(final PersistService persistService) {
        this.persistService = persistService;
    }

    @StroomStartup(priority = 1000)
    public void startPersistence() {
        LOGGER.info("Starting persistence");
        persistService.start();
    }

    @StroomShutdown
    public void stopPersistence() {
        LOGGER.info("Stopping persistence");
        persistService.stop();
    }
}
