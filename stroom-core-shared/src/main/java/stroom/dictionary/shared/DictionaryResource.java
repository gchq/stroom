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

import stroom.docref.DocRef;
import stroom.importexport.shared.Base64EncodedDocumentData;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
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

@Api(tags = "Dictionaries (v1)")
@Path("/dictionary" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface DictionaryResource extends RestResource, DirectRestService {

    // TODO @AT No idea why we have this and NewUiDictionaryResource2 if this one has react endpoints
    //   in it

    ///////////////////////
    // GWT UI end points //
    ///////////////////////

    @POST
    @Path("/read")
    @ApiOperation("Get a dictionary doc")
    DictionaryDoc read(DocRef docRef);

    @PUT
    @Path("/update")
    @ApiOperation("Update a dictionary doc")
    DictionaryDoc update(DictionaryDoc xslt);

    @POST
    @Path("/download")
    @ApiOperation("Download a dictionary doc")
    ResourceGeneration download(DocRef dictionaryRef);


    ////////////////////////
    // React UI endpoints //
    ////////////////////////

    @GET
    @Path("/list")
    @ApiOperation("Submit a request for a list of doc refs held by this service")
    Set<DocRef> listDocuments();

    @POST
    @Path("/import")
    @ApiOperation("Submit an import request")
    DocRef importDocument(@ApiParam("DocumentData") final Base64EncodedDocumentData encodedDocumentData);

    @POST
    @Path("/export")
    @ApiOperation("Submit an export request")
    Base64EncodedDocumentData exportDocument(@ApiParam("DocRef") final DocRef docRef);

    @GET
    @Path("/{dictionaryUuid}")
    @ApiOperation("Fetch a dictionary by its UUID")
    DictionaryDTO fetch(@PathParam("dictionaryUuid") final String dictionaryUuid);

    @POST
    @Path("/{dictionaryUuid}")
    @ApiOperation("Save the supplied dictionary")
    void save(@PathParam("dictionaryUuid") final String dictionaryUuid,
              final DictionaryDTO updates);
}
