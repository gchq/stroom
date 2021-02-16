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

import stroom.search.elastic.shared.FetchElasticFieldsAction;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.SharedList;
import stroom.util.shared.SharedString;
import stroom.util.spring.StroomScope;

import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetFieldMappingsRequest;
import org.elasticsearch.client.indices.GetFieldMappingsResponse;
import org.elasticsearch.client.indices.GetFieldMappingsResponse.FieldMappingMetadata;
import org.springframework.context.annotation.Scope;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@TaskHandlerBean(task = FetchElasticFieldsAction.class)
@Scope(StroomScope.TASK)
public class FetchElasticFieldsHandler extends AbstractTaskHandler<FetchElasticFieldsAction, SharedList<SharedString>> {
    @Override
    public SharedList<SharedString> exec(final FetchElasticFieldsAction action) {
        try {
            final RestHighLevelClient elasticClient = new ElasticClientFactory().create(action.getElasticIndex().getConnectionConfig());
            final String indexName = action.getElasticIndex().getIndexName();

            final GetFieldMappingsRequest request = new GetFieldMappingsRequest();
            request.indices(indexName);
            request.fields();

            final GetFieldMappingsResponse response = elasticClient.indices().getFieldMapping(request, RequestOptions.DEFAULT);
            final Map<String, Map<String, FieldMappingMetadata>> allMappings = response.mappings();
            final Map<String, FieldMappingMetadata> mappings = allMappings.get(indexName);

            // Get the field names from the index mappings
            List<String> fieldNames = mappings.values()
                    .stream()
                    .map(field -> field.fullName())
                    .collect(Collectors.toList());

            return SharedList.convert(fieldNames);
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
