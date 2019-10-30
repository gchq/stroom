package stroom.util.shared;

import java.util.Set;

public interface IsServlet {

    /**
     * @return The part of the path that will be in addition to any base path,
     * e.g. "/datafeed".
     */
    Set<String> getPathSpecs();
}
