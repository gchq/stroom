package stroom.search.solr;

import stroom.search.solr.shared.SolrConnectionConfig;
import stroom.search.solr.shared.SolrConnectionConfig.InstanceType;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.CloudHttp2SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.Http2SolrClient;

import java.util.Optional;

public class SolrClientFactory {

    public SolrClient create(final SolrConnectionConfig config) {
        if (InstanceType.SINGLE_NOOE.equals(config.getInstanceType())) {
            if (config.getSolrUrls() == null) {
                throw new SolrIndexException("No Solr URLs have been provided");
            } else if (config.getSolrUrls().size() != 1) {
                throw new SolrIndexException("Expected a single Solr URL but found " + config.getSolrUrls().size());
            }
            Http2SolrClient.Builder builder = new Http2SolrClient.Builder(config.getSolrUrls().getFirst());
            return builder.build();

        } else if (config.isUseZk()) {
            if (config.getZkHosts() == null || config.getZkHosts().isEmpty()) {
                throw new SolrIndexException("No ZK hosts have been provided");
            }
            CloudHttp2SolrClient.Builder builder = new CloudSolrClient.Builder(config.getZkHosts(),
                    Optional.ofNullable(config.getZkPath()))
                    .withHttpClient(
                            new Http2SolrClient.Builder()
                                    .connectionTimeout(15000)
                                    .idleTimeout(30000)
                                    .build());
            final CloudSolrClient client = builder.build();
            client.connect();
            return client;

        } else {
            if (config.getSolrUrls() == null || config.getSolrUrls().isEmpty()) {
                throw new SolrIndexException("No Solr URLs have been provided");
            }
            CloudHttp2SolrClient.Builder builder = new CloudSolrClient.Builder(config.getZkHosts(),
                    Optional.ofNullable(config.getZkPath()))
                    .withHttpClient(
                            new Http2SolrClient.Builder()
                                    .connectionTimeout(15000)
                                    .idleTimeout(30000)
                                    .build());
            return builder.build();
        }
    }
}
