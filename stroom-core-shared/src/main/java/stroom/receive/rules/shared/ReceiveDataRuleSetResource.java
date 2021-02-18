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
import org.fusesource.restygwt.client.DirectRestService;

import java.util.Set;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Tag(name = "Rule Set")
@Path(ReceiveDataRuleSetResource.BASE_RESOURCE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ReceiveDataRuleSetResource extends RestResource, DirectRestService, FetchWithUuid<ReceiveDataRules> {

    String BASE_RESOURCE_PATH = "/ruleset" + ResourcePaths.V2;

    @GET
    @Path("/{uuid}")
    @Operation(summary = "Fetch a rules doc by its UUID")
    ReceiveDataRules fetch(@PathParam("uuid") String uuid);

    @PUT
    @Path("/{uuid}")
    @Operation(summary = "Update a rules doc")
    ReceiveDataRules update(@PathParam("uuid") String uuid,
                            @Parameter(description = "doc", required = true) ReceiveDataRules doc);

    @GET
    @Path("/list")
    @Operation(summary = "Submit a request for a list of doc refs held by this service")
    Set<DocRef> listDocuments();

    @POST
    @Path("/import")
    @Operation(summary = "Submit an import request")
    DocRef importDocument(
            @Parameter(description = "DocumentData", required = true) Base64EncodedDocumentData documentData);

    @POST
    @Path("/export")
    @Operation(summary = "Submit an export request")
    Base64EncodedDocumentData exportDocument(@Parameter(description = "DocRef", required = true) DocRef docRef);
}
