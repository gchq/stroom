package stroom.search.elastic;

import stroom.search.elastic.shared.ElasticConnectionConfig;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig.Builder;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback;
import org.elasticsearch.client.RestClientBuilder.RequestConfigCallback;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.net.ssl.SSLContext;

public class ElasticClientFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticClientFactory.class);
    private static final Pattern URL_PATTERN = Pattern.compile("^([a-zA-Z]+):\\/\\/(.+?)(?:(?::([0-9]+)\\/?)?)$");

    @Inject
    public ElasticClientFactory() { }

    public RestHighLevelClient create(final ElasticConnectionConfig config) {
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

        final RestClientBuilder restClientBuilder = RestClient.builder(httpHosts.toArray(new HttpHost[0]));

        restClientBuilder.setRequestConfigCallback(new RequestConfigCallback() {
            @Override
            public Builder customizeRequestConfig(final Builder requestConfigBuilder) {
                return requestConfigBuilder.setSocketTimeout(config.getSocketTimeoutMillis());
            }
        });

        // If using HTTPS, set the CA certificate to verify the connection with the Elasticsearch cluster
        if (useHttps) {
            final SSLContext sslContext = getSslContext(config);
            if (sslContext != null) {
                restClientBuilder.setHttpClientConfigCallback(new HttpClientConfigCallback() {
                    @Override
                    public HttpAsyncClientBuilder customizeHttpClient(final HttpAsyncClientBuilder httpClientBuilder) {
                        return httpClientBuilder.setSSLContext(sslContext);
                    }
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

        return new RestHighLevelClient(restClientBuilder);
    }

    private SSLContext getSslContext(final ElasticConnectionConfig config) {
        final String cert = config.getCaCertificate();

        if (cert == null || cert.isEmpty()) {
            return null;
        }

        try {
            final CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            final InputStream certInputStream = new ByteArrayInputStream(cert.getBytes(StandardCharsets.UTF_8));
            final Certificate trustedCa = certFactory.generateCertificate(certInputStream);

            final KeyStore trustStore = KeyStore.getInstance("pkcs12");
            trustStore.load(null, null);
            trustStore.setCertificateEntry("ca", trustedCa);

            final SSLContextBuilder sslContextBuilder = SSLContexts.custom().loadTrustMaterial(trustStore, null);
            return sslContextBuilder.build();
        } catch (CertificateException e) {
            LOGGER.error("Failed to create a certificate factory", e);
        } catch (Exception e) {
            LOGGER.error("Failed to load CA certificate", e);
        }

        return null;
    }

    /**
     * Encode the API key ID and secret in base64 for use in a HTTP request.
     * @return Base-64 encoded string. If no API key or secret are defined, returns `null`.
     */
    private String getEncodedApiKey(ElasticConnectionConfig config) {
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
        Matcher matches = URL_PATTERN.matcher(url);

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
                    }
                    if (scheme.equalsIgnoreCase("https")) {
                        port = 443;
                    } else {
                        throw new IllegalArgumentException("Port number could not be inferred");
                    }
                }

                return new HttpHost(host, port, scheme);
            }
        } catch (NumberFormatException e) {
            LOGGER.error("Invalid port format in URL: '" + url + "'");
        } catch (RuntimeException e) {
            LOGGER.error("Elasticsearch connection URL could not be parsed: '" + url + "'. " + e.getMessage());
        }

        return null;
    }
}
