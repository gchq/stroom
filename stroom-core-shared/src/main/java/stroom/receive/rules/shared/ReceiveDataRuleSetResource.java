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

package stroom.receive.rules.shared;

import stroom.docref.DocRef;
import stroom.importexport.shared.Base64EncodedDocumentData;
import stroom.util.shared.FetchWithUuid;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.Set;

@Tag(name = "Rule Set")
@Path(ReceiveDataRuleSetResource.BASE_RESOURCE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ReceiveDataRuleSetResource extends RestResource, DirectRestService, FetchWithUuid<ReceiveDataRules> {

    String BASE_RESOURCE_PATH = "/ruleset" + ResourcePaths.V2;

    @POST
    @Path("/")
    @Operation(
            summary = "Create a new rules doc",
            operationId = "createReceiveDataRules")
    ReceiveDataRules create();

    @GET
    @Path("/{uuid}")
    @Operation(
            summary = "Fetch a rules doc by its UUID",
            operationId = "fetchReceiveDataRules")
    ReceiveDataRules fetch(@PathParam("uuid") String uuid);

    @PUT
    @Path("/{uuid}")
    @Operation(
            summary = "Update a rules doc",
            operationId = "updateReceiveDataRules")
    ReceiveDataRules update(@PathParam("uuid") String uuid,
                            @Parameter(description = "doc", required = true) ReceiveDataRules doc);

    @GET
    @Path("/list")
    @Operation(
            summary = "Submit a request for a list of doc refs held by this service",
            operationId = "listReceiveDataRules")
    Set<DocRef> listDocuments();

    @POST
    @Path("/import")
    @Operation(
            summary = "Submit an import request",
            operationId = "importReceiveDataRules")
    DocRef importDocument(
            @Parameter(description = "DocumentData", required = true) Base64EncodedDocumentData documentData);

    @POST
    @Path("/export")
    @Operation(
            summary = "Submit an export request",
            operationId = "exportReceiveDataRules")
    Base64EncodedDocumentData exportDocument(@Parameter(description = "DocRef", required = true) DocRef docRef);
}
