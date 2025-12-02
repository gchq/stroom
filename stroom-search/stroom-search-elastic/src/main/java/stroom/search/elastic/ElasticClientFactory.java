package stroom.search.elastic;

import stroom.search.elastic.shared.ElasticConnectionConfig;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.TransportUtils;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import jakarta.inject.Inject;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;

public class ElasticClientFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticClientFactory.class);
    private static final Pattern URL_PATTERN = Pattern.compile("^([a-zA-Z]+)://(.+?)(?::([0-9]+)/?)?$");

    @Inject
    public ElasticClientFactory() { }

    public ElasticsearchClient create(final ElasticConnectionConfig config,
                                      final ElasticClientConfig elasticClientConfig) {
        final ArrayList<HttpHost> httpHosts = new ArrayList<>();
        boolean useHttps = false;

        // Parse list of connection URLs into their component parts (scheme, host and port)
        for (final String url : config.getConnectionUrls()) {
            final HttpHost host = hostFromUrl(url);

            if (host != null) {
                // Extract the host, port and scheme
                httpHosts.add(host);

                if (host.getSchemeName().equalsIgnoreCase("https")) {
                    useHttps = true;
                }
            } else {
                LOGGER.error("Invalid Elasticsearch URL format: '" + url + "'");
            }
        }

        final Rest5ClientBuilder restClientBuilder = Rest5Client.builder(httpHosts.toArray(new HttpHost[0]));

        restClientBuilder.setRequestConfigCallback(requestConfig -> {
            requestConfig.setConnectionRequestTimeout(Timeout.ofMilliseconds(config.getConnectionTimeoutMillis()));
            requestConfig.setResponseTimeout(Timeout.ofMilliseconds(config.getConnectionTimeoutMillis()));
        });

        // If using HTTPS, set the CA certificate to verify the connection with the Elasticsearch cluster
        if (useHttps) {
            final SSLContext sslContext = getSslContext(config);

            if (sslContext != null) {
                restClientBuilder.setSSLContext(sslContext);
                restClientBuilder.setConnectionManagerCallback(httpClientBuilder -> {
                    httpClientBuilder
                            .setMaxConnPerRoute(elasticClientConfig.getMaxConnectionsPerRoute())
                            .setMaxConnTotal(elasticClientConfig.getMaxConnections());
                });
            }
        }

        // Set API key header if authentication is used. Key is in the format "<key id>:<secret>"
        final String apiKey = getEncodedApiKey(config);
        if (config.getUseAuthentication() && apiKey != null) {
            final Header[] defaultHeaders = new Header[] {
                new BasicHeader("Authorization", "ApiKey " + apiKey)
            };

            restClientBuilder.setDefaultHeaders(defaultHeaders);
        }

        final ElasticsearchTransport transport = new Rest5ClientTransport(restClientBuilder.build(),
                new JacksonJsonpMapper());

        return new ElasticsearchClient(transport);
    }

    private SSLContext getSslContext(final ElasticConnectionConfig config) {
        final String cert = config.getCaCertificate();

        if (cert == null || cert.isEmpty()) {
            return null;
        }

        try {
            final InputStream certInputStream = new ByteArrayInputStream(cert.getBytes(StandardCharsets.UTF_8));
            return TransportUtils.sslContextFromHttpCaCrt(certInputStream);
        } catch (final RuntimeException e) {
            LOGGER.error("Failed to initialise SSL context", e);
        } catch (final Exception e) {
            LOGGER.error("Failed to load CA certificate", e);
        }

        return null;
    }

    /**
     * Encode the API key ID and secret in base64 for use in a HTTP request.
     * @return Base-64 encoded string. If no API key or secret are defined, returns `null`.
     */
    private String getEncodedApiKey(final ElasticConnectionConfig config) {
        final String apiKeyId = config.getApiKeyId();
        final String apiKeySecret = config.getApiKeySecret();

        if (apiKeyId == null || apiKeyId.isEmpty() || apiKeySecret == null || apiKeySecret.isEmpty()) {
            return null;
        }

        final String combinedSecret = apiKeyId + ":" + apiKeySecret;
        return Base64.getEncoder().encodeToString(combinedSecret.getBytes(StandardCharsets.UTF_8));
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
                Integer port = matches.group(3) != null ? Integer.parseInt(matches.group(3)) : null;

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
            LOGGER.error("Invalid port format in URL: '" + url + "'");
        } catch (final RuntimeException e) {
            LOGGER.error("Elasticsearch connection URL could not be parsed: '" + url + "'. " + e.getMessage());
        }

        return null;
    }
}
