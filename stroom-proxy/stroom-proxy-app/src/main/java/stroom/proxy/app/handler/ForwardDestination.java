package stroom.proxy.app.handler;

import stroom.util.NullSafe;

import java.nio.file.Path;

public interface ForwardDestination {

    /**
     * Add sourceDir to this {@link ForwardDestination}.
     * If successful, sourceDir will be moved/deleted so should not be used by the caller
     * after calling this method.
     */
    void add(Path sourceDir);

    /**
     * @return The name of the destination
     */
    String getName();

    /**
     * @return Any details of the destination, e.g. url, path, etc.
     */
    String getDestinationDescription();

    /**
     * @return True if this destination is configured with a check for its liveness.
     */
    default boolean hasLivenessCheck() {
        return false;
    }

    /**
     * @return True if the liveness check indicates that the destination is live and ready
     * to have data forwarded to it. If the check fails, an exception will be thrown and the
     * message will provide details of why the liveness check is failing.
     * If hasLivenessCheck() returns false, performLivenessCheck() will always return true.
     */
    default boolean performLivenessCheck() throws Exception {
        return true;
    }

    default String asString() {
        String str = this.getClass().getSimpleName() + " " + getName();
        final String desc = getDestinationDescription();
        if (NullSafe.isNonBlankString(desc)) {
            str += " - " + desc;
        }
        return str;
    }
}
