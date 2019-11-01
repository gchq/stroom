package stroom.util;

import java.util.Map;

/**
 * Marker interface for identifying and binding a REST resource
 */
public interface RestResource {

    static Map<String, String> buildErrorResponse(final Throwable throwable) {
        return Map.of(
                "error",
                (throwable == null
                        ? "null"
                        : (throwable.getMessage() == null
                        ? throwable.toString()
                        : throwable.getMessage())));
    }
}
