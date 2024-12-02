package stroom.util.net;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.UriBuilder;

public final class UrlUtils {

    private UrlUtils() {
        // Utility class.
    }

    /**
     * Return the complete URL, including scheme, hostname, port and query string (if specified)
     */
    public static String getFullUrl(final HttpServletRequest request) {
        if (request.getQueryString() == null) {
            return request.getRequestURL().toString();
        }
        return request.getRequestURL().toString() + "?" + request.getQueryString();
    }

    /**
     * Return the URI plus the query string (if specified)
     */
    public static String getFullUri(final HttpServletRequest request) {
        if (request.getQueryString() == null) {
            return request.getRequestURI();
        }
        return request.getRequestURI() + "?" + request.getQueryString();
    }

    public static Map<String, String> createParamMap(final String url) {
        final URI uri = UriBuilder.fromUri(url).build();
        final Map<String, String> params = new HashMap<>();
        final String query = uri.getRawQuery();
        if (query != null) {
            final String[] parts = query.split("&");
            for (final String part : parts) {
                final String[] kv = part.split("=");
                if (kv.length == 2) {
                    final String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                    final String value = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                    params.put(key, value);
                }
            }
        }
        return params;
    }

    /**
     * Gets the last parameter assuming that it has been appended to the end of the URL.
     *
     * @param request The request containing the parameters.
     * @param name    The parameter name to get.
     * @return The last value of the parameter if it exists, else null.
     */
    public static String getLastParam(final HttpServletRequest request, final String name) {
        final String[] arr = request.getParameterValues(name);
        if (arr != null && arr.length > 0) {
            return arr[arr.length - 1];
        }
        return null;
    }
}
