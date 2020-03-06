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

package stroom.dictionary.impl;

import com.codahale.metrics.annotation.Timed;
import com.codahale.metrics.health.HealthCheck.Result;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.importexport.api.DocumentData;
import stroom.importexport.shared.Base64EncodedDocumentData;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.security.api.SecurityContext;
import stroom.util.HasHealthCheck;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Api(value = "dictionary - /v1")
@Path(NewUiDictionaryResource.BASE_RESOURCE_PATH)
@Produces(MediaType.APPLICATION_JSON)
public class NewUiDictionaryResource implements RestResource, HasHealthCheck {
    public static final String BASE_RESOURCE_PATH = "/dictionary" + ResourcePaths.V1;

    private final DictionaryStore dictionaryStore;
    private final SecurityContext securityContext;

    @JsonInclude(Include.NON_NULL)
    private static class DictionaryDTO extends DocRef {
        @JsonProperty
        private String description;
        @JsonProperty
        private String data;
        @JsonProperty
        private List<DocRef> imports;

        public DictionaryDTO() {
        }

        public DictionaryDTO(final DictionaryDoc doc) {
            super(DictionaryDoc.ENTITY_TYPE, doc.getUuid(), doc.getName());
            this.description = doc.getDescription();
            this.data = doc.getData();
            this.imports = doc.getImports();
        }

        @JsonCreator
        public DictionaryDTO(@JsonProperty("type") final String type,
                             @JsonProperty("uuid") final String uuid,
                             @JsonProperty("name") final String name,
                             @JsonProperty("description") final String description,
                             @JsonProperty("data") final String data,
                             @JsonProperty("imports") final List<DocRef> imports) {
            super(type, uuid, name);
            this.description = description;
            this.data = data;
            this.imports = imports;
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
    NewUiDictionaryResource(final DictionaryStore dictionaryStore,
                            final SecurityContext securityContext) {
        this.dictionaryStore = dictionaryStore;
        this.securityContext = securityContext;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/list")
    @Timed
    @ApiOperation(
            value = "Submit a request for a list of doc refs held by this service",
            response = Set.class)
    public Set<DocRef> listDocuments() {
        return dictionaryStore.listDocuments();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/import")
    @Timed
    @ApiOperation(
            value = "Submit an import request",
            response = DocRef.class)
    public DocRef importDocument(@ApiParam("DocumentData") final Base64EncodedDocumentData encodedDocumentData) {
        final DocumentData documentData = DocumentData.fromBase64EncodedDocumentData(encodedDocumentData);
        final ImportState importState = new ImportState(documentData.getDocRef(), documentData.getDocRef().getName());
        return dictionaryStore.importDocument(documentData.getDocRef(), documentData.getDataMap(), importState, ImportMode.IGNORE_CONFIRMATION);
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
        final Map<String, byte[]> map = dictionaryStore.exportDocument(docRef, true, new ArrayList<>());
        return DocumentData.toBase64EncodedDocumentData(new DocumentData(docRef, map));
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
        // A user should be allowed to read pipelines that they are inheriting from as long as they have 'use' permission on them.
        return securityContext.useAsReadResult(() -> fetchInScope(dictionaryUuid));
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
        securityContext.useAsRead(() -> {
            final DictionaryDoc doc = dictionaryStore.readDocument(getDocRef(dictionaryUuid));

            if (doc != null) {
                doc.setDescription(updates.getDescription());
                doc.setData(updates.getData());
                doc.setImports(updates.getImports());
                dictionaryStore.writeDocument(doc);
            }
        });

        return Response.noContent().build();
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}