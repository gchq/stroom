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
        final String templateName = paramName + TEMPLATE;
        return uriBuilder.queryParam(paramName, "{" + templateName + "}")
                .resolveTemplate(templateName, value);
    }

    public static WebTarget addParam(final WebTarget webTarget,
                                     final String paramName,
                                     final Object value) {
        final String templateName = paramName + TEMPLATE;
        return webTarget.queryParam(paramName, "{" + templateName + "}")
                .resolveTemplate(templateName, value);
    }
}
