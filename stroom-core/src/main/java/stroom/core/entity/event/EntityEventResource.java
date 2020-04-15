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

package stroom.core.entity.event;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import stroom.util.entityevent.EntityEvent;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api(value = "entityEvent - /v1")
@Path(EntityEventResource.BASE_PATH)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface EntityEventResource extends RestResource {
    String BASE_PATH = "/entityEvent" + ResourcePaths.V1;
    String NODE_NAME_PATH_PARAM = "/{nodeName}";

    @PUT
    @Path(NODE_NAME_PATH_PARAM)
    @ApiOperation(value = "Sends an entity event")
    Boolean fireEvent(@PathParam("nodeName") String nodeName, EntityEvent entityEvent);
}