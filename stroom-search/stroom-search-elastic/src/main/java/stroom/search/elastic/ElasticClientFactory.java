/*
 * Copyright 2021 Crown Copyright
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

package stroom.search.elastic;

import stroom.util.http.HttpAsyncClientUtil;
import stroom.util.http.HttpClientConfiguration;
import stroom.util.http.HttpProxyConfiguration;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.net.SsrfGuard;
import stroom.util.shared.NullSafe;
import stroom.util.time.StroomDuration;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.Jackson3JsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import jakarta.inject.Inject;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ElasticClientFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticClientFactory.class);
    private static final Pattern URL_PATTERN = Pattern.compile("^([a-zA-Z]+)://(.+?)(?::([0-9]+)/?)?$");

    @Inject
    public ElasticClientFactory() {
    }

    /// Creates a new Elasticsearch client. The caller is responsible for closing it.
    ///
    /// @param connection          The connection to the cluster, with all credentials resolved.
    /// @param elasticClientConfig Stroom's global Elasticsearch client settings, i.e. the connection pool sizes.
    /// @return A new client.
    public ElasticsearchClient create(final ResolvedElasticConnection connection,
                                      final ElasticClientConfig elasticClientConfig) {
        final List<HttpHost> httpHosts = new ArrayList<>();

        // Parse list of connection URLs into their component parts (scheme, host and port)
        for (final String url : connection.connectionUrls()) {
            final HttpHost host = hostFromUrl(url);

            if (host != null) {
                // Reject cloud-metadata/wildcard targets to prevent SSRF. Private and loopback hosts are
                // allowed, as an Elasticsearch cluster legitimately lives on an internal or (in dev) a
                // loopback address.
                SsrfGuard.rejectMetadataAndWildcard(url);
                httpHosts.add(host);
            } else {
                LOGGER.error("Invalid Elasticsearch URL format: '{}'", url);
            }
        }

        final HttpClientConfiguration httpClientConfiguration = connection.httpClientConfiguration();
        final Rest5ClientBuilder restClientBuilder = Rest5Client.builder(httpHosts.toArray(new HttpHost[0]));

        restClientBuilder.setRequestConfigCallback(requestConfig -> {
            setTimeout(httpClientConfiguration.getConnectionRequestTimeout(),
                    requestConfig::setConnectionRequestTimeout);
            setTimeout(httpClientConfiguration.getTimeout(), requestConfig::setResponseTimeout);
        });

        restClientBuilder.setConnectionConfigCallback(connectionConfig -> {
            setTimeout(httpClientConfiguration.getConnectionTimeout(), connectionConfig::setConnectTimeout);
            if (isPositive(httpClientConfiguration.getTimeToLive())) {
                connectionConfig.setTimeToLive(
                        TimeValue.ofMilliseconds(httpClientConfiguration.getTimeToLive().toMillis()));
            }
            if (isPositive(httpClientConfiguration.getValidateAfterInactivityPeriod())) {
                connectionConfig.setValidateAfterInactivity(TimeValue.ofMilliseconds(
                        httpClientConfiguration.getValidateAfterInactivityPeriod().toMillis()));
            }
        });

        restClientBuilder.setConnectionManagerCallback(connectionManager -> {
            connectionManager
                    .setMaxConnPerRoute(elasticClientConfig.getMaxConnectionsPerRoute())
                    .setMaxConnTotal(elasticClientConfig.getMaxConnections());
            // Always replace the client's default TLS strategy, as that does not verify hostnames
            connectionManager.setTlsStrategy(httpClientConfiguration.getTlsConfiguration() != null
                    ? HttpAsyncClientUtil.createTlsStrategy(httpClientConfiguration.getTlsConfiguration())
                    : HttpAsyncClientUtil.createDefaultTlsStrategy());
        });

        final HttpProxyConfiguration proxyConfiguration = httpClientConfiguration.getProxyConfiguration();
        final HttpRoutePlanner routePlanner = HttpAsyncClientUtil.createRoutePlanner(proxyConfiguration);
        final CredentialsProvider proxyCredentialsProvider =
                HttpAsyncClientUtil.createProxyCredentialsProvider(proxyConfiguration);
        restClientBuilder.setHttpClientConfigCallback(httpClientBuilder -> {
            // The Apache client's default retry strategy resends a request (including a non-idempotent bulk
            // POST) on a 429 or 503 response. Callers such as the indexing filter apply their own retry and
            // back-off logic, so client retries would only multiply requests and risk duplicate documents.
            httpClientBuilder.disableAutomaticRetries();
            if (routePlanner != null) {
                httpClientBuilder.setRoutePlanner(routePlanner);
            }
            if (proxyCredentialsProvider != null) {
                httpClientBuilder.setDefaultCredentialsProvider(proxyCredentialsProvider);
            }
            if (NullSafe.isNonBlankString(httpClientConfiguration.getUserAgent())) {
                httpClientBuilder.setUserAgent(httpClientConfiguration.getUserAgent());
            }
        });

        // The API key is the base64 encoding of "<key id>:<secret>", as returned by Elasticsearch in the
        // `encoded` property when the key is created.
        if (NullSafe.isNonBlankString(connection.apiKey())) {
            restClientBuilder.setDefaultHeaders(new Header[]{
                    new BasicHeader(HttpHeaders.AUTHORIZATION, "ApiKey " + connection.apiKey())
            });
        }

        // Use Jackson 3 not 2
        final ElasticsearchTransport transport = new Rest5ClientTransport(restClientBuilder.build(),
                new Jackson3JsonpMapper());

        return new ElasticsearchClient(transport);
    }

    private static void setTimeout(final StroomDuration duration,
                                   final Consumer<Timeout> setter) {
        if (duration != null) {
            setter.accept(Timeout.ofMilliseconds(duration.toMillis()));
        }
    }

    private static boolean isPositive(final StroomDuration duration) {
        return duration != null && duration.toMillis() > 0;
    }

    /**
     * Parse an Elasticsearch connection URL into its components parts
     */
    public static HttpHost hostFromUrl(final String url) {
        final Matcher matches = URL_PATTERN.matcher(url);

        try {
            if (matches.find()) {
                // Extract the host, port and scheme
                final String scheme = matches.group(1);
                final String host = matches.group(2);
                Integer port = matches.group(3) != null
                        ? Integer.parseInt(matches.group(3))
                        : null;

                // If no port specified, try and infer it from the scheme
                if (port == null) {
                    if (scheme.equalsIgnoreCase("http")) {
                        port = 80;
                    } else if (scheme.equalsIgnoreCase("https")) {
                        port = 443;
                    } else {
                        throw new IllegalArgumentException("Port number could not be inferred");
                    }
                }

                return new HttpHost(scheme, host, port);
            }
        } catch (final NumberFormatException e) {
            LOGGER.error("Invalid port format in URL: '{}'", url);
        } catch (final RuntimeException e) {
            LOGGER.error(() -> LogUtil.message(
                    "Elasticsearch connection URL could not be parsed: '{}'. {}", url, e.getMessage()));
        }

        return null;
    }
}
