package stroom.persist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class PersistLifecycle {
    private static final Logger LOGGER = LoggerFactory.getLogger(PersistLifecycle.class);
    private final Provider<PersistService> persistServiceProvider;
    private final AtomicBoolean running = new AtomicBoolean();

    @Inject
    PersistLifecycle(final Provider<PersistService> persistServiceProvider) {
        this.persistServiceProvider = persistServiceProvider;
    }

    public void startPersistence() {
        if (running.compareAndSet(false, true)) {
            LOGGER.info("Starting persistence");
            persistServiceProvider.get().start();
        }
    }

    void stopPersistence() {
        if (running.compareAndSet(true, false)) {
            LOGGER.info("Stopping persistence");
            persistServiceProvider.get().stop();
        }
    }
}
