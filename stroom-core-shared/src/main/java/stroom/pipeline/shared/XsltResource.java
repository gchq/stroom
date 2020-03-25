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

package stroom.pipeline.shared;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.fusesource.restygwt.client.DirectRestService;
import stroom.docref.DocRef;
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

@Api(value = "xslt - /v1")
@Path("/xslt" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
public interface XsltResource extends RestResource, DirectRestService {
    @POST
    @Path("/read")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Get an xslt doc",
            response = XsltDoc.class)
    XsltDoc read(DocRef docRef);

    @PUT
    @Path("/update")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Update an xslt doc",
            response = XsltDoc.class)
    XsltDoc update(XsltDoc xslt);

    @GET
    @Path("/{xsltId}")
    XsltDoc fetch(@PathParam("xsltId") final String xsltId);

    @POST
    @Path("/{xsltId}")
    @Consumes(MediaType.APPLICATION_JSON)
    void save(@PathParam("xsltId") final String xsltId,
                         final XsltDTO xsltDto);
}