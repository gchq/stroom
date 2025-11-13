package stroom.contentstore.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.IsServlet;
import stroom.util.shared.NullSafe;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Allows the UI to use HTML img tags to access the icons for the content stores.
 * We don't know what these icons will be - or what format they are.
 * Thus, this servlet can be given the content pack's ID and will stream
 * the image from its original URL directly back to the client.
 */
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
                InputStream istr = null;
                OutputStream ostr = null;

                try {
                    final URI uri = new URI(url);
                    final URLConnection connection = uri.toURL().openConnection();

                    final String httpContentType = connection.getContentType();
                    if (httpContentType != null && !httpContentType.isBlank()) {
                        LOGGER.debug("Setting content type to {}", httpContentType);
                        response.setHeader("Content-Type", httpContentType);
                    }
                    istr = connection.getInputStream();
                    ostr = response.getOutputStream();
                    final byte[] buffer = new byte[IO_BUF_SIZE];
                    for (int length; (length = istr.read(buffer)) != -1; ) {
                        ostr.write(buffer, 0, length);
                    }
                } catch (final URISyntaxException e) {
                    LOGGER.error("Cannot parse the icon URL for content pack with ID '{}': {}",
                            id,
                            e.getMessage(),
                            e);
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                } catch (final IOException e) {
                    LOGGER.error("Error downloading icon for content pack with ID '{}' from '{}': {}",
                            id,
                            url,
                            e.getMessage(),
                            e);
                    response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
                } finally {
                    if (istr != null) {
                        istr.close();
                    }
                    if (ostr != null) {
                        ostr.close();
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
