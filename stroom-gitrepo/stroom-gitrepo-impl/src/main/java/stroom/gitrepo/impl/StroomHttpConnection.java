/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.gitrepo.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.util.TemporaryBuffer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

/**
 * Bridges JGit's {@link HttpConnection} onto Stroom's shared Apache HTTP client.
 * <p>
 * JGit's interface is modelled on {@link java.net.HttpURLConnection}: the caller sets a method and headers,
 * optionally writes a body to an output stream, and only then asks for a response code. Apache's client is
 * request/response, so the body is buffered (see {@link TemporaryBufferEntity}) and the request is not
 * actually sent until something needs the response.
 * </p>
 * <p>
 * The point of routing Git through Stroom's client at all is that the client is already built from the
 * {@code GitRepoDoc}'s own HTTP configuration - including a trust store resolved from the secret store. That
 * is what lets a repository on a server with a private CA work without touching JVM-wide trust settings.
 * </p>
 * <p>
 * The client is owned by {@link StroomHttpConnectionFactory} and outlives any one connection, so nothing
 * here closes it.
 * </p>
 */
class StroomHttpConnection implements HttpConnection {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomHttpConnection.class);

    private static final String METHOD_GET = "GET";
    private static final String METHOD_HEAD = "HEAD";
    private static final String METHOD_POST = "POST";
    private static final String METHOD_PUT = "PUT";
    private static final String HDR_CONTENT_TYPE = "Content-Type";

    private final URL url;
    private final HttpHost host;
    private final HttpClient httpClient;

    private HttpUriRequestBase request;
    private String method = METHOD_GET;
    private ClassicHttpResponse response;
    private TemporaryBufferEntity entity;
    private long fixedContentLength = -1;
    private boolean chunked;

    StroomHttpConnection(final URL url, final HttpClient httpClient) {
        this.url = url;
        this.httpClient = httpClient;
        this.host = new HttpHost(url.getProtocol(), url.getHost(), url.getPort());
        this.request = newRequest(METHOD_GET);
    }

    private HttpUriRequestBase newRequest(final String method) {
        try {
            return new HttpUriRequestBase(method, url.toURI());
        } catch (final URISyntaxException e) {
            throw new IllegalArgumentException("Invalid Git URL '" + url + "'", e);
        }
    }

    /**
     * Send the request, once. Everything that needs a response funnels through here, mirroring the way
     * {@link java.net.HttpURLConnection} connects lazily.
     */
    private void execute() throws IOException {
        if (response != null) {
            return;
        }

        if (entity != null) {
            request.setEntity(entity);
        }

        // executeOpen leaves the response body streaming, which is what JGit needs - it reads pack data from
        // getInputStream() long after this returns, and a clone is far too big to buffer. The response is
        // closed by close(), which the session calls when the transport is done.
        response = httpClient.executeOpen(host, request, HttpClientContext.create());
    }

    @Override
    public int getResponseCode() throws IOException {
        execute();
        return response.getCode();
    }

    @Override
    public URL getURL() {
        return url;
    }

    @Override
    public String getResponseMessage() throws IOException {
        execute();
        return response.getReasonPhrase();
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
        final Map<String, List<String>> result = new HashMap<>();
        if (response != null) {
            for (final Header header : response.getHeaders()) {
                result.computeIfAbsent(header.getName(), k -> new ArrayList<>()).add(header.getValue());
            }
        }
        return result;
    }

    @Override
    public String getHeaderField(final String name) {
        final Header header = response == null
                ? null
                : response.getFirstHeader(name);
        return header == null
                ? null
                : header.getValue();
    }

    @Override
    public List<String> getHeaderFields(final String name) {
        if (response == null) {
            return List.of();
        }
        return Arrays.stream(response.getHeaders(name))
                .map(Header::getValue)
                .toList();
    }

    @Override
    public void setRequestProperty(final String name, final String value) {
        request.addHeader(name, value);
    }

    @Override
    public void setRequestMethod(final String method) throws ProtocolException {
        final String upperCase = method.toUpperCase();
        switch (upperCase) {
            case METHOD_GET, METHOD_HEAD, METHOD_POST, METHOD_PUT -> {
                // Rebuilding the request loses any headers already set, so carry them over. JGit sets the
                // method first in practice, but nothing in the interface says it has to.
                final Header[] existing = request.getHeaders();
                this.method = upperCase;
                this.request = newRequest(upperCase);
                for (final Header header : existing) {
                    request.addHeader(header);
                }
            }
            default -> throw new ProtocolException("Unsupported HTTP method: " + method);
        }
    }

    @Override
    public String getRequestMethod() {
        return method;
    }

    @Override
    public void setUseCaches(final boolean useCaches) {
        // Not applicable - the Apache client does not cache.
    }

    @Override
    public void setConnectTimeout(final int timeout) {
        // Deliberately ignored. Timeouts come from the GitRepoDoc's HTTP client configuration, which the
        // shared client was built with; honouring JGit's value here would silently override what an
        // administrator set on the screen. See also setReadTimeout.
        LOGGER.debug("setConnectTimeout({}) ignored in favour of the configured client", timeout);
    }

    @Override
    public void setReadTimeout(final int readTimeout) {
        // Ignored, as setConnectTimeout.
        LOGGER.debug("setReadTimeout({}) ignored in favour of the configured client", readTimeout);
    }

    @Override
    public String getContentType() {
        final HttpEntity responseEntity = response == null
                ? null
                : response.getEntity();
        return responseEntity == null
                ? null
                : responseEntity.getContentType();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        execute();
        final HttpEntity responseEntity = response.getEntity();
        return responseEntity == null
                ? InputStream.nullInputStream()
                : responseEntity.getContent();
    }

    @Override
    public int getContentLength() {
        final HttpEntity responseEntity = response == null
                ? null
                : response.getEntity();
        if (responseEntity == null) {
            return -1;
        }
        final long length = responseEntity.getContentLength();
        return length < 0 || length > Integer.MAX_VALUE
                ? -1
                : (int) length;
    }

    @Override
    public void setInstanceFollowRedirects(final boolean followRedirects) {
        // JGit does its own redirect handling for Git specific rules, and always turns this off. Redirect
        // policy otherwise belongs to the configured client, for the same reason as the timeouts.
        LOGGER.debug("setInstanceFollowRedirects({}) ignored in favour of the configured client",
                followRedirects);
    }

    @Override
    public void setDoOutput(final boolean doOutput) {
        // Whether there is a body is decided by whether anything is written to getOutputStream().
    }

    @Override
    public void setFixedLengthStreamingMode(final int contentLength) {
        this.fixedContentLength = contentLength;
        this.chunked = false;
    }

    @Override
    public void setChunkedStreamingMode(final int chunkLength) {
        this.chunked = true;
        this.fixedContentLength = -1;
    }

    @Override
    public OutputStream getOutputStream() {
        if (entity == null) {
            final Header contentType = request.getFirstHeader(HDR_CONTENT_TYPE);
            entity = new TemporaryBufferEntity(
                    new TemporaryBuffer.LocalFile(null),
                    chunked
                            ? -1
                            : fixedContentLength,
                    contentType == null
                            ? null
                            : contentType.getValue());
        }
        return entity.getBuffer();
    }

    @Override
    public boolean usingProxy() {
        // Proxying is a property of the configured client rather than of this connection, so we cannot say.
        return false;
    }

    @Override
    public void connect() throws IOException {
        execute();
    }

    @Override
    public void configure(final KeyManager[] keyManagers,
                          final TrustManager[] trustManagers,
                          final SecureRandom random) {
        // Intentionally does nothing. TLS is already established by the client this connection borrows,
        // built from the GitRepoDoc's configuration with key and trust stores resolved from the secret
        // store. JGit calls this when it wants to disable certificate checking (http.sslVerify=false);
        // that decision belongs on the document, via 'verify hostname' and 'trust self signed
        // certificates', where it is visible and auditable rather than buried in a git config file.
        LOGGER.debug("configure() ignored - TLS is set by the configured client");
    }

    @Override
    public void setHostnameVerifier(final HostnameVerifier hostnameVerifier) {
        // Ignored, as configure().
        LOGGER.debug("setHostnameVerifier() ignored - TLS is set by the configured client");
    }

    /**
     * Release the response and any buffered request body. Not part of {@link HttpConnection}; the factory's
     * session calls this when the transport is closed.
     */
    void close() {
        try {
            if (response != null) {
                response.close();
            }
        } catch (final IOException e) {
            LOGGER.debug(e::getMessage, e);
        } finally {
            response = null;
            if (entity != null) {
                entity.close();
                entity = null;
            }
        }
    }
}
