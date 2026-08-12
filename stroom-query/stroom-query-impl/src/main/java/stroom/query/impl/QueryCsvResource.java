/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.query.impl;

import stroom.query.shared.QueryResource;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * The CSV search endpoint. This deliberately does not live on {@link QueryResource}, which extends
 * DirectRestService, so GWT generates a client proxy for every method on it and therefore has to be able
 * to compile every signature. {@link Response} has no GWT emulation, so a method returning one cannot go
 * there. Nothing in the UI calls this endpoint anyway, it being intended for scripted use, so it lives
 * here in the impl module where GWT cannot see it.
 * <p>
 * It shares {@link QueryResource#BASE_PATH} so that the endpoint keeps its original URL. RestResources
 * flags two resource classes declaring the same class level path, so this path is listed there as one
 * that may be shared. See gh-5688.
 */
@Tag(name = "Queries")
@Path(QueryResource.BASE_PATH)
@Produces(MediaType.TEXT_PLAIN)
public interface QueryCsvResource extends RestResource {

    String CSV_SEARCH_PATH_PART = "/csv/search";

    /**
     * Tells the caller whether the search ran incrementally, i.e. whether the results can be partial.
     */
    String CSV_INCREMENTAL_HEADER = "X-Stroom-Search-Incremental";
    /**
     * Tells the caller whether the search finished. False means the CSV holds only what had been found
     * when the search returned, so it is a subset of the matching data.
     */
    String CSV_COMPLETE_HEADER = "X-Stroom-Search-Complete";

    @GET
    @Path(CSV_SEARCH_PATH_PART)
    @Operation(
            summary = "Perform a csv query",
            description = "Returns the results as CSV. Sets the '" + CSV_INCREMENTAL_HEADER + "' header to say " +
                          "whether the search ran incrementally, and the '" + CSV_COMPLETE_HEADER + "' header to " +
                          "say whether it finished. An incremental search returns whatever results it has when " +
                          "the timeout expires, so those may be partial; a non incremental search waits for the " +
                          "search to finish, which risks the request itself timing out.",
            operationId = "queryCsv",
            responses = {
                    @ApiResponse(description = "Returns the matching rows as CSV, which may be a subset of the " +
                                               "matching data if the search had not finished")
            })
    @Produces(MediaType.TEXT_PLAIN)
    Response csvSearch(@QueryParam("query") String query,
                       @QueryParam("offset") int offset,
                       @DefaultValue("100") @QueryParam("length") int length,
                       @DefaultValue("true") @QueryParam("incremental") boolean incremental,
                       @QueryParam("timeout") Long timeout);
}
