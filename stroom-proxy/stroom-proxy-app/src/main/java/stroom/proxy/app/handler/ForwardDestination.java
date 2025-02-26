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

    default String asString() {
        String str = this.getClass().getSimpleName() + " " + getName();
        final String desc = getDestinationDescription();
        if (NullSafe.isNonBlankString(desc)) {
            str += " - " + desc;
        }
        return str;
    }
}
