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

package stroom.data.shared;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.fusesource.restygwt.client.DirectRestService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.RestResource;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api(value = "data")
@Path("/data")
@Produces(MediaType.APPLICATION_JSON)
public interface DataResource extends RestResource, DirectRestService {
    @POST
    @Path("download")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Download matching data",
            response = ResourceGeneration.class)
    ResourceGeneration download(FindMetaCriteria criteria);

    @POST
    @Path("upload")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Upload data",
            response = ResourceGeneration.class)
    ResourceKey upload(UploadDataRequest request);
}