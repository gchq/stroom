package stroom.util.shared;

import java.util.Set;

public interface IsServlet {

    /**
     * @return The part of the path that will be in addition to any base path,
     * e.g. "/datafeed".
     */
    Set<String> getPathSpecs();

    /**
     * @return The name to use when registering this servlet. If not implemented, returns the simple name
     * of this class.
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}
