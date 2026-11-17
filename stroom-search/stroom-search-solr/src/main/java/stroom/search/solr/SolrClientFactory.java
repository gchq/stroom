/*
 * Copyright 2019 Crown Copyright
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
import stroom.util.shared.NullSafe;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.jetty.CloudJettySolrClient;
import org.apache.solr.client.solrj.jetty.CloudJettySolrClient.Builder;
import org.apache.solr.client.solrj.jetty.HttpJettySolrClient;

import java.util.concurrent.TimeUnit;

public class SolrClientFactory {

    public SolrClient create(final SolrConnectionConfig config) {
        if (InstanceType.SINGLE_NOOE.equals(config.getInstanceType())) {
            if (!NullSafe.hasOneItem(config.getSolrUrls())) {
                throw new SolrIndexException("Expected a single Solr URL but found "
                                             + NullSafe.size(config.getSolrUrls()));
            }
            return new HttpJettySolrClient.Builder(config.getSolrUrls().getFirst())
                    .build();

        } else if (config.isUseZk()) {
            final Builder builder = createWithZKHosts(config);
            builder.withHttpClient(createHttpClient());
            return builder.build();

        } else {
            if (NullSafe.isEmptyCollection(config.getSolrUrls())) {
                throw new SolrIndexException("No Solr URLs have been provided");
            }
            // Without ZooKeeper the cluster state is read from the Solr URLs
            final CloudJettySolrClient.Builder builder = new CloudJettySolrClient.Builder(config.getSolrUrls());
            builder.withHttpClient(createHttpClient());
            return builder.build();
        }
    }

    private static CloudJettySolrClient.Builder createWithZKHosts(final SolrConnectionConfig config) {
        if (NullSafe.isEmptyCollection(config.getZkHosts())) {
            throw new SolrIndexException("No ZK hosts have been provided");
        }
        final CloudSolrClient.CloudSolrClientConnection connection =
                new CloudSolrClient.CloudSolrClientConnection(
                        true,
                        config.getZkHosts(),
                        config.getZkPath());
        return new Builder(connection);
    }

    private static HttpJettySolrClient createHttpClient() {
        return new HttpJettySolrClient.Builder()
                .withConnectionTimeout(15, TimeUnit.SECONDS)
                .withIdleTimeout(30, TimeUnit.SECONDS)
                .build();
    }
}
