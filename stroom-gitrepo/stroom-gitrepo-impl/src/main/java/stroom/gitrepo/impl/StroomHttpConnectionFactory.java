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

import stroom.util.http.HttpClientConfiguration;
import stroom.util.jersey.HttpClientProvider;
import stroom.util.jersey.HttpClientProviderCache;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.HttpConnectionFactory2;

import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Supplies JGit with HTTP connections that run on Stroom's shared, configured Apache client.
 * <p>
 * One of these is created per Git transport, so its lifetime is the lifetime of a single clone, fetch or
 * push. That matters: the client provider is reference counted, and the count has to come back down exactly
 * once. JGit's {@link HttpConnectionFactory2.GitSession} is the only hook that reliably fires at the end of
 * a transport ({@code TransportHttp.close()} closes the session), so the release happens there rather than
 * per connection - a single transport opens several connections, and several of those are never read to
 * completion when the server responds with an authentication challenge.
 * </p>
 */
class StroomHttpConnectionFactory implements HttpConnectionFactory2 {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomHttpConnectionFactory.class);

    private final HttpClientProviderCache httpClientProviderCache;
    private final HttpClientConfiguration httpClientConfiguration;

    private final List<StroomHttpConnection> connections = new ArrayList<>();
    private HttpClientProvider httpClientProvider;

    StroomHttpConnectionFactory(final HttpClientProviderCache httpClientProviderCache,
                                final HttpClientConfiguration httpClientConfiguration) {
        this.httpClientProviderCache = httpClientProviderCache;
        this.httpClientConfiguration = httpClientConfiguration;
    }

    @Override
    public HttpConnection create(final URL url) {
        return create(url, null);
    }

    @Override
    public HttpConnection create(final URL url, final Proxy proxy) {
        // Proxying comes from the document's HTTP configuration, so JGit's notion of a proxy is ignored for
        // the same reason its timeouts are - see StroomHttpConnection.
        if (proxy != null && !Proxy.NO_PROXY.equals(proxy)) {
            LOGGER.debug("Ignoring JGit proxy {} in favour of the configured client", proxy);
        }

        final StroomHttpConnection connection = new StroomHttpConnection(url, provider().get());
        synchronized (connections) {
            connections.add(connection);
        }
        return connection;
    }

    @Override
    public GitSession newSession() {
        return new GitSession() {
            @Override
            public HttpConnection configure(final HttpConnection connection, final boolean sslVerify) {
                if (!sslVerify) {
                    // http.sslVerify=false in a git config would normally switch off certificate checking.
                    // Say so rather than appearing to honour it: what the connection actually does is
                    // whatever the document's TLS configuration says.
                    LOGGER.warn("Ignoring http.sslVerify=false; TLS is governed by the Git repository's " +
                                "HTTP client configuration");
                }
                return connection;
            }

            @Override
            public void close() {
                closeAll();
            }
        };
    }

    private synchronized HttpClientProvider provider() {
        if (httpClientProvider == null) {
            httpClientProvider = httpClientProviderCache.get(httpClientConfiguration);
        }
        return httpClientProvider;
    }

    private void closeAll() {
        final List<StroomHttpConnection> toClose;
        synchronized (connections) {
            toClose = List.copyOf(connections);
            connections.clear();
        }
        for (final StroomHttpConnection connection : toClose) {
            try {
                connection.close();
            } catch (final RuntimeException e) {
                LOGGER.debug(e::getMessage, e);
            }
        }

        synchronized (this) {
            if (httpClientProvider != null) {
                // Hands our reference back. The underlying pooled client stays alive for other repositories
                // sharing this configuration and is only closed when the last holder lets go.
                httpClientProvider.close();
                httpClientProvider = null;
            }
        }
    }
}
