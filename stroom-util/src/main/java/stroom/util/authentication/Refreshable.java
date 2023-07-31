package stroom.util.authentication;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.concurrent.Delayed;

public interface Refreshable extends Delayed {

    LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Refreshable.class);

    boolean isRefreshRequired();

    /**
     * @return True if the refresh happened
     */
    boolean refresh();

    long getExpireTimeEpochMs();

    default boolean refreshIfRequired() {
        final boolean didRefresh;
        if (isRefreshRequired()) {
            synchronized (this) {
                if (isRefreshRequired()) {
                    LOGGER.trace("Refreshing");
                    didRefresh = refresh();
                } else {
                    LOGGER.trace("Refresh not required");
                    didRefresh = false;
                }
            }
        } else {
            LOGGER.trace("Refresh not required");
            didRefresh = false;
        }
        return didRefresh;
    }
}
