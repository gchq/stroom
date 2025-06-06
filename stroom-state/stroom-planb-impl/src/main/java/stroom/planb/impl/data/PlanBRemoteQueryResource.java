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

package stroom.planb.impl.data;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Used to remotely query Plan B
 */
@Tag(name = "Plan B Query")
@Path(PlanBRemoteQueryResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface PlanBRemoteQueryResource extends RestResource {

    String BASE_PATH = "/planb" + ResourcePaths.V1;
    String GET_VALUE_PATH = "/getValue";
    String GET_STORE_INFO = "/getStoreInfo";

    @POST
    @Path(GET_VALUE_PATH)
    @Operation(
            summary = "Gets a value from a remote Plan B store.",
            operationId = "planBQueryGetValue")
    PlanBValue getValue(GetRequest request);


    @POST
    @Path(GET_STORE_INFO)
    @Operation(
            summary = "Gets Plan B store info.",
            operationId = "getPlanBStoreInfo")
    PlanBShardInfoResponse getStoreInfo(PlanBShardInfoRequest request);
}
