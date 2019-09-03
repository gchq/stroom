package stroom.search.solr;

import org.apache.http.client.HttpClient;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import stroom.search.solr.shared.SolrConnectionConfig;
import stroom.search.solr.shared.SolrConnectionConfig.InstanceType;

import java.util.Optional;

public class SolrClientFactory {
    private final HttpClient httpClient;

    public SolrClientFactory() {
        httpClient = null;
    }

    public SolrClientFactory(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public SolrClient create(final SolrConnectionConfig config) {
        if (InstanceType.SINGLE_NOOE.equals(config.getInstanceType())) {
            if (config.getSolrUrls() == null) {
                throw new SolrIndexException("No Solr URLs have been provided");
            } else if (config.getSolrUrls().size() != 1) {
                throw new SolrIndexException("Expected a single Solr URL but found " + config.getSolrUrls().size());
            }
            HttpSolrClient.Builder builder = new HttpSolrClient.Builder(config.getSolrUrls().get(0));
            if (httpClient != null) {
                builder = builder.withHttpClient(httpClient);
            }
            return builder.build();

        } else if (config.isUseZk()) {
            if (config.getZkHosts() == null || config.getZkHosts().size() == 0) {
                throw new SolrIndexException("No ZK hosts have been provided");
            }
            CloudSolrClient.Builder builder = new CloudSolrClient.Builder(config.getZkHosts(), Optional.ofNullable(config.getZkPath()))
                    .withSocketTimeout(30000)
                    .withConnectionTimeout(15000);
            if (httpClient != null) {
                builder = builder.withHttpClient(httpClient);
            }
            final CloudSolrClient client = builder.build();
            client.connect();
            return client;

        } else {
            if (config.getSolrUrls() == null || config.getSolrUrls().size() == 0) {
                throw new SolrIndexException("No Solr URLs have been provided");
            }
            CloudSolrClient.Builder builder = new CloudSolrClient.Builder(config.getSolrUrls())
                    .withSocketTimeout(30000)
                    .withConnectionTimeout(15000);
            if (httpClient != null) {
                builder = builder.withHttpClient(httpClient);
            }
            return builder.build();
        }
    }
}
