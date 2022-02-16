package stroom.util.jersey;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.UriBuilder;

public class UriBuilderUtil {

    private static final String TEMPLATE = "Template";

    private UriBuilderUtil() {
        // Utility class.
    }

    public static UriBuilder addParam(final UriBuilder uriBuilder,
                                      final String paramName,
                                      final Object value) {
        return uriBuilder.queryParam(paramName, "{" + paramName + TEMPLATE + "}")
                .resolveTemplate(paramName + TEMPLATE, value);
    }

    public static WebTarget addParam(final WebTarget webTarget,
                                     final String paramName,
                                     final Object value) {
        return webTarget.queryParam(paramName, "{" + paramName + TEMPLATE + "}")
                .resolveTemplate(paramName + TEMPLATE, value);
    }
}
