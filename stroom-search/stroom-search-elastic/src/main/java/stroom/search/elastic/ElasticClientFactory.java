package stroom.search.elastic;

import stroom.search.elastic.shared.ElasticConnectionConfig;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ElasticClientFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticClientFactory.class);
    private static final Pattern URL_PATTERN = Pattern.compile("^([a-zA-Z]+):\\/\\/(.+?)(?:(?::([0-9]+)\\/?)?)$");

    public ElasticClientFactory() { }

    public RestHighLevelClient create(final ElasticConnectionConfig config) {
        final ArrayList<HttpHost> httpHosts = new ArrayList<>();

        // Parse list of connection URLs into their component parts (scheme, host and port)
        for (final String url : config.getConnectionUrls()) {
            final HttpHost host = hostFromUrl(url);

            if (host != null) {
                // Extract the host, port and scheme
                httpHosts.add(host);
            }
            else {
                LOGGER.error("Invalid Elasticsearch URL format: '" + url + "'");
            }
        }

        final RestClientBuilder restClientBuilder = RestClient.builder(httpHosts.toArray(new HttpHost[0]));

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

    /**
     * Encode the API key ID and secret in base64 for use in a HTTP request.
     * @return Base-64 encoded string. If no API key or secret are defined, returns `null`.
     */
    private String getEncodedApiKey(ElasticConnectionConfig config) {
        final String apiKeyId = config.getApiKeyId();
        final String apiKeySecret = config.getApiKeySecret();

        if (apiKeyId == null || apiKeyId.isEmpty() || apiKeySecret == null || apiKeySecret.isEmpty())
            return null;

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
        }
        catch (NumberFormatException e) {
            LOGGER.error("Invalid port format in URL: '" + url + "'");
        }
        catch (RuntimeException e) {
            LOGGER.error("Elasticsearch connection URL could not be parsed: '" + url + "'. " + e.getMessage());
        }

        return null;
    }
}
