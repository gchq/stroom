package stroom.search.elastic;

import stroom.search.elastic.shared.ElasticConnectionConfig;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;

public class ElasticClientFactory {
    public ElasticClientFactory() { }

    public RestHighLevelClient create(final ElasticConnectionConfig config) {
        final HttpHost host = new HttpHost(config.getHost(), config.getPort(), config.getScheme());
        return new RestHighLevelClient(RestClient.builder(host));
    }
}
