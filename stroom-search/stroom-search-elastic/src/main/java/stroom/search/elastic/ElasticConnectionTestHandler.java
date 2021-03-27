/*
 * Copyright 2016 Crown Copyright
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

package stroom.search.elastic;

import stroom.search.elastic.shared.ElasticCluster;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.search.elastic.shared.ElasticConnectionTestAction;
import stroom.search.elastic.shared.ElasticIndex;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.SharedString;
import stroom.util.spring.StroomScope;

import org.elasticsearch.ResourceNotFoundException;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.MainResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;

import javax.inject.Inject;

@TaskHandlerBean(task = ElasticConnectionTestAction.class)
@Scope(StroomScope.TASK)
public class ElasticConnectionTestHandler extends AbstractTaskHandler<ElasticConnectionTestAction, SharedString> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticConnectionTestHandler.class);

    private final ElasticClusterStore elasticClusterStore;

    @Inject
    public ElasticConnectionTestHandler(
        final ElasticClusterStore elasticClusterStore
    ) {
        this.elasticClusterStore = elasticClusterStore;
    }

    @Override
    public SharedString exec(final ElasticConnectionTestAction action) {
        switch (action.getTestType()) {
            case CLUSTER:
                return testCluster(action.getElasticCluster());
            case INDEX:
                return testIndex(action.getElasticIndex());
            default:
                LOGGER.error("Test type is not defined");
                return null;
        }
    }

    private SharedString testCluster(final ElasticCluster cluster) {
        try {
            final ElasticConnectionConfig connectionConfig = cluster.getConnectionConfig();
            final RestHighLevelClient elasticClient = new ElasticClientFactory().create(connectionConfig);

            MainResponse response = elasticClient.info(RequestOptions.DEFAULT);

            final StringBuilder sb = new StringBuilder()
                    .append("Cluster URLs: ")
                    .append(connectionConfig.getConnectionUrls())
                    .append("\nCluster name: ")
                    .append(response.getClusterName())
                    .append("\nCluster UUID: ")
                    .append(response.getClusterUuid())
                    .append("\nNode name: ")
                    .append(response.getNodeName())
                    .append("\nVersion: ")
                    .append(response.getVersion().getNumber());

            return SharedString.wrap(sb.toString());
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private SharedString testIndex(final ElasticIndex index) {
        try {
            if (index.getClusterRef() == null)
                throw new IllegalArgumentException("Elasticsearch cluster configuration not specified");

            final ElasticCluster elasticCluster = elasticClusterStore.readDocument(index.getClusterRef());
            final ElasticConnectionConfig connectionConfig = elasticCluster.getConnectionConfig();
            final RestHighLevelClient elasticClient = new ElasticClientFactory().create(connectionConfig);

            // Check whether the specified index exists
            final String indexName = index.getIndexName();
            final GetIndexRequest getIndexRequest = new GetIndexRequest(indexName);
            GetIndexResponse getIndexResponse = elasticClient.indices().get(getIndexRequest, RequestOptions.DEFAULT);
            if (getIndexResponse.getIndices().length < 1) {
                throw new ResourceNotFoundException("Index '" + indexName + "' was not found");
            }

            return testCluster(elasticCluster);
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
