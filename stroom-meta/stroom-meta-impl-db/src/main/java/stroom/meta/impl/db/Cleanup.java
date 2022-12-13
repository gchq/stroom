package stroom.meta.impl.db;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;

import java.util.Set;
import javax.inject.Inject;

public class Cleanup {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Cleanup.class);

    private final Set<Clearable> clearables;

    @Inject
    Cleanup(final Set<Clearable> clearables) {
        this.clearables = clearables;
    }

    public void cleanup() {
        // Clear all caches or files that might have been created by previous tests.
        clearables.forEach(clearable -> {
            LOGGER.debug("Clearing {}", clearable.getClass().getSimpleName());
            clearable.clear();
        });
    }
}
