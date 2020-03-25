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

package stroom.dictionary.shared;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.fusesource.restygwt.client.DirectRestService;
import stroom.docref.DocRef;
import stroom.importexport.shared.Base64EncodedDocumentData;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Set;

@Api(value = "dictionary - /v1")
@Path("/dictionary" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
public interface DictionaryResource extends RestResource, DirectRestService {
    ///////////////////////
    // GWT UI end points //
    ///////////////////////

    @POST
    @Path("/read")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Get a dictionary doc",
            response = DictionaryDoc.class)
    DictionaryDoc read(DocRef docRef);

    @PUT
    @Path("/update")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Update a dictionary doc",
            response = DictionaryDoc.class)
    DictionaryDoc update(DictionaryDoc xslt);

    @POST
    @Path("/download")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Download a dictionary doc",
            response = ResourceGeneration.class)
    ResourceGeneration download(DocRef dictionaryRef);



    ////////////////////////
    // React UI endpoints //
    ////////////////////////

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @javax.ws.rs.Path("/list")
    @ApiOperation(
            value = "Submit a request for a list of doc refs held by this service",
            response = Set.class)
    Set<DocRef> listDocuments();

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @javax.ws.rs.Path("/import")
    @ApiOperation(
            value = "Submit an import request",
            response = DocRef.class)
    DocRef importDocument(@ApiParam("DocumentData") final Base64EncodedDocumentData encodedDocumentData);

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @javax.ws.rs.Path("/export")
    @ApiOperation(
            value = "Submit an export request",
            response = Base64EncodedDocumentData.class)
    Base64EncodedDocumentData exportDocument(@ApiParam("DocRef") final DocRef docRef);

    @GET
    @javax.ws.rs.Path("/{dictionaryUuid}")
    @Produces(MediaType.APPLICATION_JSON)
    DictionaryDTO fetch(@PathParam("dictionaryUuid") final String dictionaryUuid);

    @POST
    @javax.ws.rs.Path("/{dictionaryUuid}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    void save(@PathParam("dictionaryUuid") final String dictionaryUuid,
                         final DictionaryDTO updates);
}