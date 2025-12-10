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

package stroom.app;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;
import com.google.common.io.Resources;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.dropwizard.servlets.assets.ByteRange;
import io.dropwizard.servlets.assets.ResourceURL;
import jakarta.annotation.Nullable;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

public class BrowserRouterAssetServlet extends HttpServlet {

    private static final long serialVersionUID = 6393345594784987908L;
    private static final CharMatcher SLASHES = CharMatcher.is('/');

    private static class CachedAsset {

        private final byte[] resource;
        private final String eTag;
        private final long lastModifiedTime;

        private CachedAsset(final byte[] resource, final long lastModifiedTime) {
            this.resource = resource;
            this.eTag = '"' + Hashing.murmur3_128().hashBytes(resource).toString() + '"';
            this.lastModifiedTime = lastModifiedTime;
        }

        public byte[] getResource() {
            return resource;
        }

        public String getETag() {
            return eTag;
        }

        public long getLastModifiedTime() {
            return lastModifiedTime;
        }
    }

    private static final MediaType DEFAULT_MEDIA_TYPE = MediaType.HTML_UTF_8;

    private final String singlePagePrefix;
    private final String resourcePath;
    private final String uriPath;

    @Nullable
    private final String indexFile;

    @Nullable
    private final Charset defaultCharset;

    public BrowserRouterAssetServlet(final String resourcePath,
                                     final String uriPath,
                                     @Nullable final String indexFile,
                                     @Nullable final Charset defaultCharset,
                                     final String singlePagePrefix) {
        final String trimmedPath = SLASHES.trimFrom(resourcePath);
        this.resourcePath = trimmedPath.isEmpty()
                ? trimmedPath
                : trimmedPath + '/';
        final String trimmedUri = SLASHES.trimTrailingFrom(uriPath);
        this.uriPath = trimmedUri.isEmpty()
                ? "/"
                : trimmedUri;
        this.indexFile = indexFile;
        this.defaultCharset = defaultCharset;
        this.singlePagePrefix = singlePagePrefix;
    }

    @Override
    protected void doGet(final HttpServletRequest req,
                         final HttpServletResponse resp) throws ServletException, IOException {
        try {
            final StringBuilder builder = new StringBuilder(req.getServletPath());
            if (req.getPathInfo() != null) {
                builder.append(req.getPathInfo());
            }
            String url = builder.toString();
            if (url.contains(singlePagePrefix)) {
                url = "/";
            }

            final CachedAsset cachedAsset = loadAsset(url);
            if (cachedAsset == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            if (isCachedClientSide(req, cachedAsset)) {
                resp.sendError(HttpServletResponse.SC_NOT_MODIFIED);
                return;
            }

            final String rangeHeader = req.getHeader(HttpHeaders.RANGE);

            final int resourceLength = cachedAsset.getResource().length;
            ImmutableList<ByteRange> ranges = ImmutableList.of();

            boolean usingRanges = false;
            // Support for HTTP Byte Ranges
            // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
            if (rangeHeader != null) {

                final String ifRange = req.getHeader(HttpHeaders.IF_RANGE);

                if (ifRange == null || cachedAsset.getETag().equals(ifRange)) {

                    try {
                        ranges = parseRangeHeader(rangeHeader, resourceLength);
                    } catch (final NumberFormatException e) {
                        resp.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                        return;
                    }

                    if (ranges.isEmpty()) {
                        resp.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                        return;
                    }

                    resp.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                    usingRanges = true;

                    resp.addHeader(HttpHeaders.CONTENT_RANGE, "bytes "
                            + Joiner.on(",").join(ranges) + "/" + resourceLength);
                }
            }

            resp.setDateHeader(HttpHeaders.LAST_MODIFIED, cachedAsset.getLastModifiedTime());
            resp.setHeader(HttpHeaders.ETAG, cachedAsset.getETag());

            final String mimeTypeOfExtension = req.getServletContext()
                    .getMimeType(req.getRequestURI());
            MediaType mediaType = DEFAULT_MEDIA_TYPE;

            if (mimeTypeOfExtension != null) {
                try {
                    mediaType = MediaType.parse(mimeTypeOfExtension);
                    if (defaultCharset != null && mediaType.is(MediaType.ANY_TEXT_TYPE)) {
                        mediaType = mediaType.withCharset(defaultCharset);
                    }
                } catch (final IllegalArgumentException ignore) {
                    // ignore
                }
            }

            if (mediaType.is(MediaType.ANY_VIDEO_TYPE)
                    || mediaType.is(MediaType.ANY_AUDIO_TYPE) || usingRanges) {
                resp.addHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
            }

            resp.setContentType(mediaType.type() + '/' + mediaType.subtype());

            if (mediaType.charset().isPresent()) {
                resp.setCharacterEncoding(mediaType.charset().get().toString());
            }

            try (final ServletOutputStream output = resp.getOutputStream()) {
                if (usingRanges) {
                    for (final ByteRange range : ranges) {
                        output.write(cachedAsset.getResource(), range.getStart(),
                                range.getEnd() - range.getStart() + 1);
                    }
                } else {
                    output.write(cachedAsset.getResource());
                }
            }
        } catch (final RuntimeException | URISyntaxException ignored) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Nullable
    private CachedAsset loadAsset(final String key) throws URISyntaxException, IOException {
        checkArgument(key.startsWith(uriPath));
        final String requestedResourcePath = SLASHES.trimFrom(key.substring(uriPath.length()));
        final String absoluteRequestedResourcePath = SLASHES.trimFrom(this.resourcePath + requestedResourcePath);

        URL requestedResourceURL = getResourceUrl(absoluteRequestedResourcePath);
        if (ResourceURL.isDirectory(requestedResourceURL)) {
            if (indexFile != null) {
                requestedResourceURL = getResourceUrl(absoluteRequestedResourcePath + '/' + indexFile);
            } else {
                // directory requested but no index file defined
                return null;
            }
        }

        long lastModified = ResourceURL.getLastModified(requestedResourceURL);
        if (lastModified < 1) {
            // Something went wrong trying to get the last modified time: just use the current time
            lastModified = System.currentTimeMillis();
        }

        // zero out the millis since the date we get back from If-Modified-Since will not have them
        lastModified = (lastModified / 1000) * 1000;
        return new CachedAsset(readResource(requestedResourceURL), lastModified);
    }

    protected URL getResourceUrl(final String absoluteRequestedResourcePath) {
        return Resources.getResource(absoluteRequestedResourcePath);
    }

    protected byte[] readResource(final URL requestedResourceURL) throws IOException {
        return Resources.toByteArray(requestedResourceURL);
    }

    private boolean isCachedClientSide(final HttpServletRequest req, final CachedAsset cachedAsset) {
        return cachedAsset.getETag().equals(req.getHeader(HttpHeaders.IF_NONE_MATCH)) ||
                (req.getDateHeader(HttpHeaders.IF_MODIFIED_SINCE) >= cachedAsset.getLastModifiedTime());
    }

    /**
     * Parses a given Range header for one or more byte ranges.
     *
     * @param rangeHeader    Range header to parse
     * @param resourceLength Length of the resource in bytes
     * @return List of parsed ranges
     */
    private ImmutableList<ByteRange> parseRangeHeader(final String rangeHeader,
                                                      final int resourceLength) {
        final ImmutableList.Builder<ByteRange> builder = ImmutableList.builder();
        if (rangeHeader.contains("=")) {
            final String[] parts = rangeHeader.split("=");
            if (parts.length > 1) {
                final List<String> ranges = Splitter.on(",").trimResults().splitToList(parts[1]);
                for (final String range : ranges) {
                    builder.add(ByteRange.parse(range, resourceLength));
                }
            }
        }
        return builder.build();
    }
}
