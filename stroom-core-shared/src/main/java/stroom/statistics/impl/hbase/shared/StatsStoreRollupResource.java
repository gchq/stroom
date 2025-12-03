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

package stroom.statistics.impl.hbase.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;

@Tag(name = "Stroom Stats RollUps")
@Path("/statsStore/rollUp" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface StatsStoreRollupResource extends RestResource, DirectRestService {

    @POST
    @Path("/bitMaskPermGeneration")
    @Operation(
            summary = "Create rollup bit mask",
            operationId = "statsBitMaskPermGeneration")
    ResultPage<CustomRollUpMask> bitMaskPermGeneration(
            @Parameter(description = "fieldCount", required = true) Integer fieldCount);

    @POST
    @Path("/bitMaskConversion")
    @Operation(
            summary = "Get rollup bit mask",
            operationId = "statsBitMaskConversion")
    ResultPage<CustomRollUpMaskFields> bitMaskConversion(
            @Parameter(description = "maskValues", required = true) List<Short> maskValues);

    @POST
    @Path("/dataSourceFieldChange")
    @Operation(
            summary = "Change fields",
            operationId = "statsFieldChange")
    StroomStatsStoreEntityData fieldChange(
            @Parameter(description = "request", required = true) StroomStatsStoreFieldChangeRequest request);
}
