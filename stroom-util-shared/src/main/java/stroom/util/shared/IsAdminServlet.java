package stroom.util.shared;

import java.util.Set;

/**
 * A servlet that is registered on the admin port.
 * All admin servlets are un-authenticated on the basis that access to the admin port
 * will be carefully restricted.
 * Any servlets requiring authentication should instead implement {@link IsServlet}.
 */
public interface IsAdminServlet {

    /**
     * @return The part of the path that will be in addition to any base path,
     * e.g. "/datafeed".
     */
    Set<String> getPathSpecs();
}
