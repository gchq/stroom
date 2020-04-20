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

import com.codahale.metrics.annotation.Timed;
import com.codahale.metrics.health.HealthCheck.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import stroom.docref.DocRef;
import stroom.importexport.api.DocumentData;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.importexport.shared.Base64EncodedDocumentData;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.util.HasHealthCheck;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

@Api(
        value = "solr index - /v1",
        description = "Solr Index API")
@Path(NewUiSolrIndexResource.BASE_RESOURCE_PATH)
@Produces(MediaType.APPLICATION_JSON)
public class NewUiSolrIndexResource implements RestResource, HasHealthCheck {
    public static final String BASE_RESOURCE_PATH = "/solr/index" + ResourcePaths.V1;

    private final SolrIndexStore solrIndexStore;

    @Inject
    public NewUiSolrIndexResource(final SolrIndexStore solrIndexStore) {
        this.solrIndexStore = solrIndexStore;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/list")
    @Timed
    @ApiOperation(
            value = "Submit a request for a list of doc refs held by this service",
            response = Set.class)
    public Set<DocRef> listDocuments() {
        return solrIndexStore.listDocuments();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/import")
    @Timed
    @ApiOperation(
            value = "Submit an import request",
            response = DocRef.class)
    public DocRef importDocument(@ApiParam("DocumentData") final Base64EncodedDocumentData base64EncodedDocumentData) {
        final DocumentData documentData = DocumentData.fromBase64EncodedDocumentData(base64EncodedDocumentData);
        final ImportState importState = new ImportState(documentData.getDocRef(), documentData.getDocRef().getName());
        final ImportExportActionHandler.ImpexDetails result = solrIndexStore.importDocument(documentData.getDocRef(), documentData.getDataMap(), importState, ImportMode.IGNORE_CONFIRMATION);
        if (result != null)
            return result.getDocRef();
        else
            return null;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/export")
    @Timed
    @ApiOperation(
            value = "Submit an export request",
            response = Base64EncodedDocumentData.class)
    public Base64EncodedDocumentData exportDocument(@ApiParam("DocRef") final DocRef docRef) {
        final Map<String, byte[]> map = solrIndexStore.exportDocument(docRef, true, new ArrayList<>());
        return DocumentData.toBase64EncodedDocumentData(new DocumentData(docRef, map));
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}