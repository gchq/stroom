/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.search.solr;

import stroom.search.solr.shared.SolrConnectionConfig;
import stroom.search.solr.shared.SolrConnectionConfig.InstanceType;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.CloudHttp2SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.Http2SolrClient;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class SolrClientFactory {

    public SolrClient create(final SolrConnectionConfig config) {
        if (InstanceType.SINGLE_NOOE.equals(config.getInstanceType())) {
            if (config.getSolrUrls() == null) {
                throw new SolrIndexException("No Solr URLs have been provided");
            } else if (config.getSolrUrls().size() != 1) {
                throw new SolrIndexException("Expected a single Solr URL but found " + config.getSolrUrls().size());
            }
            final Http2SolrClient.Builder builder = new Http2SolrClient.Builder(config.getSolrUrls().getFirst());
            return builder.build();

        } else if (config.isUseZk()) {
            if (config.getZkHosts() == null || config.getZkHosts().isEmpty()) {
                throw new SolrIndexException("No ZK hosts have been provided");
            }
            final CloudHttp2SolrClient.Builder builder = new CloudSolrClient.Builder(config.getZkHosts(),
                    Optional.ofNullable(config.getZkPath()))
                    .withHttpClient(
                            new Http2SolrClient.Builder()
                                    .withConnectionTimeout(15000, TimeUnit.MILLISECONDS)
                                    .withIdleTimeout(30000, TimeUnit.MILLISECONDS)
                                    .build());
            final CloudSolrClient client = builder.build();
            client.connect();
            return client;

        } else {
            if (config.getSolrUrls() == null || config.getSolrUrls().isEmpty()) {
                throw new SolrIndexException("No Solr URLs have been provided");
            }
            final CloudHttp2SolrClient.Builder builder = new CloudSolrClient.Builder(config.getZkHosts(),
                    Optional.ofNullable(config.getZkPath()))
                    .withHttpClient(
                            new Http2SolrClient.Builder()
                                    .withConnectionTimeout(15000, TimeUnit.MILLISECONDS)
                                    .withIdleTimeout(30000, TimeUnit.MILLISECONDS)
                                    .build());
            return builder.build();
        }
    }
}
