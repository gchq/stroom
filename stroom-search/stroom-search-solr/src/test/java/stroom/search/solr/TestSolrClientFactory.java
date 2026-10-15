/*
 * Copyright 2026 Crown Copyright
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

import org.apache.solr.client.solrj.impl.CloudHttp2SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSolrClientFactory {

    private static final List<String> SOLR_URLS = List.of("http://solr1:8983/solr", "http://solr2:8983/solr");
    private static final List<String> ZK_HOSTS = List.of("zk1:2181");

    @Test
    void create_cloudWithoutZooKeeper() {
        // Regression test: Solr Cloud without ZooKeeper built its client from the ZooKeeper hosts
        // (which this mode doesn't use) instead of the Solr URLs
        final SolrConnectionConfig config = SolrConnectionConfig.builder()
                .instanceType(InstanceType.SOLR_CLOUD)
                .useZk(false)
                .solrUrls(SOLR_URLS)
                .zkHosts(ZK_HOSTS)
                .build();

        assertThat(cloudBuilderArguments(config))
                .containsExactly(SOLR_URLS);
    }

    @Test
    void create_cloudWithZooKeeper() {
        final SolrConnectionConfig config = SolrConnectionConfig.builder()
                .instanceType(InstanceType.SOLR_CLOUD)
                .useZk(true)
                .solrUrls(SOLR_URLS)
                .zkHosts(ZK_HOSTS)
                .zkPath("/solr")
                .build();

        assertThat(cloudBuilderArguments(config))
                .containsExactly(ZK_HOSTS, Optional.of("/solr"));
    }

    @Test
    void create_cloudWithoutZooKeeper_noUrls() {
        final SolrConnectionConfig config = SolrConnectionConfig.builder()
                .instanceType(InstanceType.SOLR_CLOUD)
                .useZk(false)
                .zkHosts(ZK_HOSTS)
                .build();

        assertThatThrownBy(() -> new SolrClientFactory().create(config))
                .isInstanceOf(SolrIndexException.class)
                .hasMessage("No Solr URLs have been provided");
    }

    // Creates the client for the config, with SolrJ's builders mocked (so no client is made or
    // connected), and returns the arguments the cloud client's builder was made with.
    private static List<Object> cloudBuilderArguments(final SolrConnectionConfig config) {
        final List<List<Object>> arguments = new ArrayList<>();
        try (final MockedConstruction<Http2SolrClient.Builder> ignored = Mockito.mockConstruction(
                Http2SolrClient.Builder.class,
                Mockito.withSettings().defaultAnswer(Answers.RETURNS_SELF));
                final MockedConstruction<CloudSolrClient.Builder> cloudBuilders = Mockito.mockConstruction(
                        CloudSolrClient.Builder.class,
                        Mockito.withSettings().defaultAnswer(Answers.RETURNS_SELF),
                        (mock, context) -> {
                            arguments.add(new ArrayList<>(context.arguments()));
                            Mockito.doReturn(Mockito.mock(CloudHttp2SolrClient.class)).when(mock).build();
                        })) {
            new SolrClientFactory().create(config);
            assertThat(cloudBuilders.constructed())
                    .hasSize(1);
        }
        return arguments.getFirst();
    }
}
