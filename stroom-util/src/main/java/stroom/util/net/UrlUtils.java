/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.net;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.UriBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class UrlUtils {

    private UrlUtils() {
        // Utility class.
    }

    public static String getFullUrl(final HttpServletRequest request) {
        if (request.getQueryString() == null) {
            return request.getRequestURL().toString();
        }
        return request.getRequestURL().toString() + "?" + request.getQueryString();
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

    /**
     * Normalise a URL to a canonical origin string {@code scheme://host:port}, lower-casing the scheme and
     * host and resolving the default port for the scheme so that e.g. {@code https://example.com} and
     * {@code https://example.com:443} compare equal. Two URLs share an origin iff their {@code toOrigin}
     * results are non-null and equal. Returns {@code null} if the value is null, cannot be parsed, or lacks
     * a scheme or host (e.g. the literal {@code "null"} origin sent by sandboxed contexts).
     */
    public static String toOrigin(final String value) {
        if (value == null) {
            return null;
        }
        try {
            return toOrigin(new URI(value));
        } catch (final URISyntaxException e) {
            return null;
        }
    }

    public static String toOrigin(final URI uri) {
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) {
            return null;
        }
        final String scheme = uri.getScheme().toLowerCase();
        int port = uri.getPort();
        if (port == -1) {
            port = "https".equals(scheme)
                    ? 443
                    : 80;
        }
        return scheme + "://" + uri.getHost().toLowerCase() + ":" + port;
    }

    /**
     * Whether {@code candidate} is a safe same-origin redirect target relative to {@code base}. True for a
     * root-relative path (a single leading {@code /}, which resolves against the current origin) or for an
     * absolute URI whose origin equals {@code base}'s origin. False for a null/blank value, a
     * protocol-relative {@code //host} reference, an opaque scheme (e.g. {@code javascript:}) or any
     * off-origin URI. Use this to reject open redirects before returning a browser to a request-supplied
     * location.
     */
    public static boolean isSameOrigin(final String candidate, final URI base) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        final String candidateOrigin = toOrigin(candidate);
        if (candidateOrigin == null) {
            // No scheme/host, so a relative reference. Allow a safe root-relative path, but not a
            // protocol-relative "//host" (off-origin) or anything else without a leading slash.
            return isSafeRootRelativePath(candidate);
        }
        return candidateOrigin.equals(toOrigin(base));
    }

    /**
     * Whether a relative reference is a root-relative path that cannot escape the current origin. It must
     * start with a single {@code /} and contain no backslash or control character: a browser folds a
     * backslash to {@code /} and strips control characters (tab, newline, ...) before parsing, so a value
     * such as {@code "/\evil.com"} or {@code "/<TAB>/evil.com"} would resolve to a protocol-relative
     * {@code "//evil.com"} (a different origin) despite starting with a single {@code /}.
     */
    private static boolean isSafeRootRelativePath(final String candidate) {
        if (!candidate.startsWith("/") || candidate.startsWith("//")) {
            return false;
        }
        for (int i = 0; i < candidate.length(); i++) {
            final char c = candidate.charAt(i);
            if (c == '\\' || c < 0x20 || c == 0x7f) {
                return false;
            }
        }
        return true;
    }
}
