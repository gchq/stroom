package stroom.search.elastic;

import stroom.search.elastic.shared.ElasticConnectionConfig;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ElasticClientFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticClientFactory.class);
    private final Pattern urlPattern = Pattern.compile("^([a-zA-Z]+):\\/\\/(.+?):([0-9]+)\\/?$");

    public ElasticClientFactory() { }

    public RestHighLevelClient create(final ElasticConnectionConfig config) {
        final ArrayList<HttpHost> httpHosts = new ArrayList<>();

        // Parse list of connection URLs into their component parts (scheme, host and port)
        for (final String url : config.getConnectionUrls()) {
            Matcher matches = urlPattern.matcher(url);

            if (matches.find()) {
                // Extract the host, port and scheme
                httpHosts.add(new HttpHost(matches.group(1), Integer.parseInt(matches.group(2)), matches.group(0)));
            }
            else {
                LOGGER.error("Invalid Elasticsearch URL format: '" + url + "'");
            }
        }

        final RestClientBuilder restClient = RestClient.builder((HttpHost[]) httpHosts.toArray());

        return new RestHighLevelClient(restClient);
    }
}
