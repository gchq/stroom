/*
 * Copyright 2025 Crown Copyright
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

package stroom.contentstore.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.net.SsrfGuard;
import stroom.util.shared.IsServlet;
import stroom.util.shared.NullSafe;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Allows the UI to use HTML img tags to access the icons for the content stores.
 * We don't know what these icons will be - or what format they are.
 * Thus, this servlet can be given the content pack's ID and will stream
 * the image from its original URL directly back to the client.
 */
// The icon URLs come from the content store definitions, which are fetched from remote URLs,
// so they are not trusted. Only http(s) URLs that pass SsrfGuard are fetched, only image
// responses are passed through, and the response is served with headers that stop it being
// sniffed or run as an active document on Stroom's origin.
public class IconPassthroughServlet extends HttpServlet implements IsServlet {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IconPassthroughServlet.class);

    /** Somewhere to cache the ID -> URL mapping */
    private static final Map<String, String> ID_URL_MAP = new ConcurrentHashMap<>();

    /** The size of the buffer used to copy stuff around */
    private static final int IO_BUF_SIZE = 4096;

    /** The URL path to this servlet */
    public static final String PATH_PART = "/iconPassThrough";

    /** Set of paths to access this servlet */
    private static final Set<String> PATH_SPECS = Set.of(PATH_PART);

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private static final String IMAGE_MIMETYPE_PREFIX = "image/";

    // Redirects are followed by hand so each hop can be checked.
    private static final int MAX_REDIRECTS = 5;

    private static final int CONNECT_TIMEOUT_MS = 10_000;

    private static final int READ_TIMEOUT_MS = 10_000;

    static final String CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options";

    static final String CONTENT_TYPE_OPTIONS_NOSNIFF = "nosniff";

    static final String CONTENT_SECURITY_POLICY_HEADER = "Content-Security-Policy";

    // Stops an icon (e.g. an SVG) running script or loading anything if it is opened directly
    // rather than through an img tag. The same policy GitHub uses for raw content.
    static final String CONTENT_SECURITY_POLICY_INERT = "default-src 'none'; style-src 'unsafe-inline'; sandbox";

    /**
     * Call to provide a map from the ID to the URL for the icon.
     * @param id The ID that the client will pass to the servlet.
     * @param url The URL to get the icon from given the ID.
     */
    public static void addIdToUrl(final String id, final String url) {
        ID_URL_MAP.put(id, url);
    }

    /**
     * Called to get an image for the Content Store.
     */
    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
        throws IOException {

        LOGGER.debug("iconPassThrough: {}", request.getQueryString());

        final String query = request.getQueryString();
        if (NullSafe.isNonBlankString(query)) {
            final String id = URLDecoder.decode(query, StandardCharsets.UTF_8);
            final String url = ID_URL_MAP.get(id);
            if (NullSafe.isNonBlankString(url)) {
                HttpURLConnection connection = null;
                try {
                    connection = openConnection(url);

                    final String httpContentType = connection.getContentType();
                    if (!isImageContentType(httpContentType)) {
                        LOGGER.error("Icon for content pack with ID '{}' from '{}' has content type '{}', " +
                                     "which is not an image", id, url, httpContentType);
                        response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
                        return;
                    }

                    LOGGER.debug("Setting content type to {}", httpContentType);
                    response.setHeader("Content-Type", httpContentType);
                    response.setHeader(CONTENT_TYPE_OPTIONS_HEADER, CONTENT_TYPE_OPTIONS_NOSNIFF);
                    response.setHeader(CONTENT_SECURITY_POLICY_HEADER, CONTENT_SECURITY_POLICY_INERT);

                    try (final InputStream istr = connection.getInputStream();
                            final OutputStream ostr = response.getOutputStream()) {
                        final byte[] buffer = new byte[IO_BUF_SIZE];
                        for (int length; (length = istr.read(buffer)) != -1; ) {
                            ostr.write(buffer, 0, length);
                        }
                    }
                } catch (final URISyntaxException | IllegalArgumentException e) {
                    LOGGER.error("Refusing to fetch the icon for content pack with ID '{}' from '{}': {}",
                            id,
                            url,
                            e.getMessage(),
                            e);
                    response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
                } catch (final IOException e) {
                    LOGGER.error("Error downloading icon for content pack with ID '{}' from '{}': {}",
                            id,
                            url,
                            e.getMessage(),
                            e);
                    response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            } else {
                // No URL for this ID
                LOGGER.error("No URL found for ID {}", id);
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
        } else {
            // No query
            LOGGER.error("No query string found in icon passthrough servlet");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    /// Opens a connection to the URL, following any redirects by hand so that every URL
    /// visited is checked.
    ///
    /// @param url The URL to fetch.
    /// @return A connection that has received a non-redirect response.
    /// @throws IllegalArgumentException If a URL is not allowed or there are too many redirects.
    private HttpURLConnection openConnection(final String url) throws IOException, URISyntaxException {
        String currentUrl = url;
        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            final URI uri = checkUrl(currentUrl);
            final HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);

            final int status = connection.getResponseCode();
            final String location = connection.getHeaderField("Location");
            if (isRedirect(status) && location != null) {
                connection.disconnect();
                currentUrl = uri.resolve(location).toString();
                LOGGER.debug("Following redirect from '{}' to '{}'", uri, currentUrl);
            } else {
                return connection;
            }
        }
        throw new IllegalArgumentException("Too many redirects fetching '" + url + "'");
    }

    /// Checks that an icon URL is safe to fetch.
    ///
    /// @param url The URL to check.
    /// @return The parsed URL if it is http(s) and its host is not a disallowed address.
    /// @throws URISyntaxException If the URL cannot be parsed.
    /// @throws IllegalArgumentException If the URL is not allowed.
    static URI checkUrl(final String url) throws URISyntaxException {
        final URI uri = new URI(url);
        final String scheme = NullSafe.get(uri.getScheme(), str -> str.toLowerCase(Locale.ROOT));
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme)) {
            throw new IllegalArgumentException("URL scheme must be one of " + ALLOWED_SCHEMES + ": '" + url + "'");
        }
        SsrfGuard.rejectMetadataAndWildcard(url);
        return uri;
    }

    /// Checks whether a response content type is an image.
    ///
    /// @param contentType The content type, possibly with parameters. May be null.
    /// @return True if the content type is an image type.
    static boolean isImageContentType(final String contentType) {
        return NullSafe.isNonBlankString(contentType)
               && contentType.trim().toLowerCase(Locale.ROOT).startsWith(IMAGE_MIMETYPE_PREFIX);
    }

    private static boolean isRedirect(final int status) {
        return status >= 300 && status < 400 && status != HttpURLConnection.HTTP_NOT_MODIFIED;
    }

    @Override
    public void init() throws ServletException {
        LOGGER.debug("Creating IconPassthroughServlet");
        super.init();
    }

    @Override
    public void destroy() {
        LOGGER.debug("Destroying IconPassthroughServlet");
        super.destroy();
    }

    @Override
    public Set<String> getPathSpecs() {
        LOGGER.debug("PathSpecs: {}", PATH_SPECS);
        return PATH_SPECS;
    }
}
