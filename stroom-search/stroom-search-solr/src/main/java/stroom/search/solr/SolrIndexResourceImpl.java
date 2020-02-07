/*
 * Copyright 2017 Crown Copyright
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

import com.codahale.metrics.health.HealthCheck.Result;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.schema.SchemaRequest.FieldTypes;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.client.solrj.response.schema.SchemaResponse.FieldTypesResponse;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.search.solr.shared.SolrIndexResource;
import stroom.util.HasHealthCheck;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.RestResource;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

class SolrIndexResourceImpl implements SolrIndexResource, RestResource, HasHealthCheck {
    private final SolrIndexStore solrIndexStore;
    private final DocumentResourceHelper documentResourceHelper;

    @Inject
    SolrIndexResourceImpl(final SolrIndexStore solrIndexStore,
                          final DocumentResourceHelper documentResourceHelper) {
        this.solrIndexStore = solrIndexStore;
        this.documentResourceHelper = documentResourceHelper;
    }

    @Override
    public SolrIndexDoc read(final DocRef docRef) {
        return documentResourceHelper.read(solrIndexStore, docRef);
    }

    @Override
    public SolrIndexDoc update(final SolrIndexDoc doc) {
        return documentResourceHelper.update(solrIndexStore, doc);
    }

    @Override
    public List<String> fetchSolrTypes(final SolrIndexDoc solrIndexDoc) {
        try {
            final SolrClient solrClient = new SolrClientFactory().create(solrIndexDoc.getSolrConnectionConfig());
            final FieldTypesResponse response = new FieldTypes().process(solrClient, solrIndexDoc.getCollection());
            return response.getFieldTypes()
                    .stream()
                    .map(fieldTypeRepresentation -> Optional.ofNullable(fieldTypeRepresentation.getAttributes()))
                    .filter(Optional::isPresent)
                    .map(optional -> Optional.ofNullable(optional.get().get("name")))
                    .filter(Optional::isPresent)
                    .map(optional -> (String) optional.get())
                    .collect(Collectors.toList());
        } catch (final IOException | SolrServerException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public String solrConnectionTest(final SolrIndexDoc solrIndexDoc) {
        try {
            final SolrClient solrClient = new SolrClientFactory().create(solrIndexDoc.getSolrConnectionConfig());
//            final SolrPingResponse response = new SolrPing().process(solrClient, action.getSolrIndex().getCollection());
            final SolrPingResponse response = solrClient.ping();

            final StringBuilder sb = new StringBuilder();
            sb.append("Request URL: ");
            sb.append(response.getRequestUrl());
            sb.append("\nStatus: ");
            sb.append(response.getStatus());
            sb.append("\nElapsed Time: ");
            sb.append(ModelStringUtil.formatDurationString(response.getElapsedTime()));
            sb.append("\nQ Time: ");
            sb.append(ModelStringUtil.formatDurationString((long) response.getQTime()));

            sb.append("\nResponse Header: ");
            sb.append(response.getResponseHeader().toString());
            sb.append("\nResponse: ");
            sb.append(response.getResponse().toString());

            if (response.getException() != null) {
                sb.append("\nError: ");
                sb.append(response.getException().toString());
            }

            return sb.toString();
        } catch (final IOException | SolrServerException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}