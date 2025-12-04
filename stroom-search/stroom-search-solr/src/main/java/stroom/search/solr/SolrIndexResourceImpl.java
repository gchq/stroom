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

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.search.solr.shared.SolrConnectionTestResponse;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.search.solr.shared.SolrIndexResource;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.FetchWithUuid;
import stroom.util.shared.ModelStringUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.schema.SchemaRequest.FieldTypes;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.client.solrj.response.schema.SchemaResponse.FieldTypesResponse;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@AutoLogged
class SolrIndexResourceImpl implements SolrIndexResource, FetchWithUuid<SolrIndexDoc> {

    private final Provider<SolrIndexStore> solrIndexStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;

    @Inject
    SolrIndexResourceImpl(final Provider<SolrIndexStore> solrIndexStoreProvider,
                          final Provider<DocumentResourceHelper> documentResourceHelperProvider) {
        this.solrIndexStoreProvider = solrIndexStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
    }

    @Override
    public SolrIndexDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(solrIndexStoreProvider.get(), getDocRef(uuid));
    }

    @Override
    public SolrIndexDoc update(final String uuid, final SolrIndexDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(solrIndexStoreProvider.get(), doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(SolrIndexDoc.TYPE)
                .build();
    }

    @Override
    public List<String> fetchSolrTypes(final SolrIndexDoc solrIndexDoc) {
        try {
            final SolrClient solrClient = new SolrClientFactory().create(
                    solrIndexDoc.getSolrConnectionConfig());
            final FieldTypesResponse response = new FieldTypes().process(
                    solrClient, solrIndexDoc.getCollection());
            return response.getFieldTypes()
                    .stream()
                    .map(fieldTypeRepresentation ->
                            Optional.ofNullable(fieldTypeRepresentation.getAttributes()))
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
    @AutoLogged(value = OperationType.PROCESS, verb = "Testing Solr Connection")
    public SolrConnectionTestResponse solrConnectionTest(final SolrIndexDoc solrIndexDoc) {
        try {
            final SolrClient solrClient = new SolrClientFactory().create(
                    solrIndexDoc.getSolrConnectionConfig());
//            final SolrPingResponse response = new SolrPing()
//            .process(solrClient, action.getSolrIndex().getCollection());
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

            return new SolrConnectionTestResponse(true, sb.toString());

        } catch (final IOException | SolrServerException | RuntimeException e) {
            return new SolrConnectionTestResponse(false, e.getMessage());
        }
    }
}
