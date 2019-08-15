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

package stroom.search.solr;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.schema.SchemaRequest.FieldTypes;
import org.apache.solr.client.solrj.response.schema.SchemaResponse.FieldTypesResponse;
import org.springframework.context.annotation.Scope;
import stroom.search.solr.shared.FetchSolrTypesAction;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.SharedList;
import stroom.util.shared.SharedString;
import stroom.util.spring.StroomScope;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@TaskHandlerBean(task = FetchSolrTypesAction.class)
@Scope(StroomScope.TASK)
public class FetchSolrTypesHandler extends AbstractTaskHandler<FetchSolrTypesAction, SharedList<SharedString>> {
    @Override
    public SharedList<SharedString> exec(final FetchSolrTypesAction action) {
        try {
            final SolrClient solrClient = new SolrClientFactory().create(action.getSolrIndex().getSolrConnectionConfig());
            final FieldTypesResponse response = new FieldTypes().process(solrClient, action.getSolrIndex().getCollection());
            final List<String> names = response.getFieldTypes()
                    .stream()
                    .map(fieldTypeRepresentation -> Optional.ofNullable(fieldTypeRepresentation.getAttributes()))
                    .filter(Optional::isPresent)
                    .map(optional -> Optional.ofNullable(optional.get().get("name")))
                    .filter(Optional::isPresent)
                    .map(optional -> (String) optional.get())
                    .collect(Collectors.toList());
            return SharedList.convert(names);
        } catch (final IOException | SolrServerException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
