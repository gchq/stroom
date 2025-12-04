/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.core.sysinfo;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.sysinfo.HasSystemInfo.ParamInfo;
import stroom.util.sysinfo.SystemInfoResult;
import stroom.util.sysinfo.SystemInfoResultList;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

import java.util.List;

@Tag(name = "System Info")
@Path(SystemInfoResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface SystemInfoResource extends RestResource {

    String BASE_PATH = "/systemInfo" + ResourcePaths.V1;
    String NAMES_PATH_PART = "/names";
    String PARAMS_PATH_PART = "/params";
    String PARAM_NAME_NAME = "name";

    @GET
    @Operation(
            summary = "Get all system info results",
            operationId = "getAllSystemInfo")
    SystemInfoResultList getAll();

    @GET
    @Path(NAMES_PATH_PART)
    @Operation(
            summary = "Get all system info result names",
            operationId = "getSystemInfoNames")
    List<String> getNames();

    @GET
    @Path(PARAMS_PATH_PART + "/{name}")
    @Operation(
            summary = "Gets the parameters for this system info provider",
            operationId = "getSystemInfoParams")
    List<ParamInfo> getParams(@PathParam(PARAM_NAME_NAME) final String name);

    @GET
    @Path("/{name}")
    @Operation(
            summary = "Get a system info result by name",
            operationId = "getSystemInfoByName")
    SystemInfoResult get(@Context final UriInfo uriInfo,
                         @PathParam(PARAM_NAME_NAME) final String providerName);
}
