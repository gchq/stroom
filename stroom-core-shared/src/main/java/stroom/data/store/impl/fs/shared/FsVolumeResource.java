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

package stroom.data.store.impl.fs.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.fusesource.restygwt.client.DirectRestService;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Api(value = "fsVolume - /v1")
@Path("/fsVolume" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface FsVolumeResource extends RestResource, DirectRestService {

    @POST
    @Path("/find")
    @ApiOperation(
            value = "Finds volumes",
            response = List.class)
    ResultPage<FsVolume> find(@ApiParam("criteria") FindFsVolumeCriteria criteria);

    @POST
    FsVolume create(FsVolume volume);

    @GET
    @Path("/{id}")
    @ApiOperation(
            value = "Get a volume",
            response = FsVolume.class)
    FsVolume read(@PathParam("id") Integer id);

    @PUT
    @Path("/{id}")
    @ApiOperation(
            value = "Update a volume",
            response = FsVolume.class)
    FsVolume update(@PathParam("id") Integer id,
                    @ApiParam("volume") FsVolume volume);

    @DELETE
    @Path("/{id}")
    @ApiOperation(
            value = "Delete a volume",
            response = Boolean.class)
    Boolean delete(@PathParam("id") Integer id);

    @GET
    @Path("/rescan")
    @ApiOperation(
            value = "Rescans volumes",
            response = Boolean.class)
    Boolean rescan();
}
