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

package stroom.dictionary;

import com.codahale.metrics.annotation.Timed;
import com.codahale.metrics.health.HealthCheck.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docstore.EncodingUtil;
import stroom.docstore.shared.DocRefUtil;
import stroom.importexport.DocRefs;
import stroom.importexport.OldDocumentData;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.docref.DocRef;
import stroom.security.Security;
import stroom.util.HasHealthCheck;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

@Api(
        value = "dictionary - /v1",
        description = "Dictionary API")
@Path("/dictionary/v1")
@Produces(MediaType.APPLICATION_JSON)
public class DictionaryResource implements HasHealthCheck {
    private final DictionaryStore dictionaryStore;
    private final Security security;

    private static class DictionaryDTO {
        private DocRef docRef;
        private String description;
        private String data;
        private List<DocRef> imports;

        public DictionaryDTO() {

        }

        public DictionaryDTO(final DictionaryDoc doc) {
            this.docRef = DocRefUtil.create(doc);
            this.description = doc.getDescription();
            this.data = doc.getData();
            this.imports = doc.getImports();
        }

        public DocRef getDocRef() {
            return docRef;
        }

        public String getDescription() {
            return description;
        }

        public String getData() {
            return data;
        }

        public List<DocRef> getImports() {
            return imports;
        }

        public void setDocRef(DocRef docRef) {
            this.docRef = docRef;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public void setData(String data) {
            this.data = data;
        }

        public void setImports(List<DocRef> imports) {
            this.imports = imports;
        }
    }

    @Inject
    DictionaryResource(final DictionaryStore dictionaryStore,
                       final Security security) {
        this.dictionaryStore = dictionaryStore;
        this.security = security;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/list")
    @Timed
    @ApiOperation(
            value = "Submit a request for a list of doc refs held by this service",
            response = Set.class)
    public DocRefs listDocuments() {
        return new DocRefs(dictionaryStore.listDocuments());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/import")
    @Timed
    @ApiOperation(
            value = "Submit an import request",
            response = DocRef.class)
    public DocRef importDocument(@ApiParam("DocumentData") final OldDocumentData documentData) {
        final ImportState importState = new ImportState(documentData.getDocRef(), documentData.getDocRef().getName());
        if (documentData.getDataMap() == null) {
            return dictionaryStore.importDocument(documentData.getDocRef(), null, importState, ImportMode.IGNORE_CONFIRMATION);
        }
        final Map<String, byte[]> data = documentData.getDataMap().entrySet().stream().collect(Collectors.toMap(Entry::getKey, e -> EncodingUtil.asBytes(e.getValue())));
        return dictionaryStore.importDocument(documentData.getDocRef(), data, importState, ImportMode.IGNORE_CONFIRMATION);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/export")
    @Timed
    @ApiOperation(
            value = "Submit an export request",
            response = OldDocumentData.class)
    public OldDocumentData exportDocument(@ApiParam("DocRef") final DocRef docRef) {
        final Map<String, byte[]> map = dictionaryStore.exportDocument(docRef, true, new ArrayList<>());
        if (map == null) {
            return new OldDocumentData(docRef, null);
        }
        final Map<String, String> data = map.entrySet().stream().collect(Collectors.toMap(Entry::getKey, e -> EncodingUtil.asString(e.getValue())));
        return new OldDocumentData(docRef, data);
    }

    private Response fetchInScope(final String dictionaryUuid) {
        final DictionaryDoc doc = dictionaryStore.readDocument(getDocRef(dictionaryUuid));
        final DictionaryDTO dto = new DictionaryDTO(doc);

        return Response.ok(dto).build();
    }

    @GET
    @Path("/{dictionaryUuid}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response fetch(@PathParam("dictionaryUuid") final String dictionaryUuid) {
        return security.secureResult(() -> {
            // A user should be allowed to read pipelines that they are inheriting from as long as they have 'use' permission on them.
            return security.useAsReadResult(() -> fetchInScope(dictionaryUuid));
        });
    }

    private DocRef getDocRef(final String pipelineId) {
        return new DocRef.Builder()
                .uuid(pipelineId)
                .type(DictionaryDoc.ENTITY_TYPE)
                .build();
    }

    @POST
    @Path("/{dictionaryUuid}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response save(@PathParam("dictionaryUuid") final String dictionaryUuid,
                         final DictionaryDTO updates) {

        // A user should be allowed to read pipelines that they are inheriting from as long as they have 'use' permission on them.
        security.useAsRead(() -> {
            final DictionaryDoc doc = dictionaryStore.readDocument(getDocRef(dictionaryUuid));

            if (doc != null) {
                doc.setDescription(updates.getDescription());
                doc.setData(updates.getData());
                doc.setImports(updates.getImports());
                dictionaryStore.writeDocument(doc);
            }
        });

        return Response.ok().build();
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}