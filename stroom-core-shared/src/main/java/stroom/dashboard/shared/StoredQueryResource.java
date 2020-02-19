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

package stroom.dashboard.shared;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.fusesource.restygwt.client.DirectRestService;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api(value = "storedQuery")
@Path("/storedQuery")
@Produces(MediaType.APPLICATION_JSON)
public interface StoredQueryResource extends RestResource, DirectRestService {
    @POST
    @Path("/find")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Find stored queries",
            response = ResultPage.class)
    ResultPage<StoredQuery> find(FindStoredQueryCriteria criteria);

    @POST
    @Path("/create")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Create a stored query",
            response = StoredQuery.class)
    StoredQuery create(StoredQuery storedQuery);

    @POST
    @Path("/read")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Get a stored query",
            response = StoredQuery.class)
    StoredQuery read(StoredQuery storedQuery);

    @PUT
    @Path("/update")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Update a stored query",
            response = StoredQuery.class)
    StoredQuery update(StoredQuery storedQuery);

    @DELETE
    @Path("/delete")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Delete a stored query",
            response = StoredQuery.class)
    Boolean delete(StoredQuery storedQuery);

//    @POST
//    @Path("/fetch")
//    @Consumes(MediaType.APPLICATION_JSON)
//    @Produces(MediaType.APPLICATION_JSON)
//    @ApiOperation(
//            value = "Fetch a stored query",
//            response = ResourceGeneration.class)
//    StoredQuery fetchStoredQuery(StoredQuery storedQuery);
}