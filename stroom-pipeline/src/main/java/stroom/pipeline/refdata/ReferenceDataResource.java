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

package stroom.pipeline.refdata;

import stroom.pipeline.refdata.store.ProcessingInfoResponse;
import stroom.pipeline.refdata.store.RefStoreEntry;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Tag(name = "Reference Data")
@Path(ReferenceDataResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ReferenceDataResource extends RestResource {

    String BASE_PATH = "/refData" + ResourcePaths.V1;

    String ENTRIES_SUB_PATH = "/entries";
    String REF_STREAM_INFO_SUB_PATH = "/refStreamInfo";
    String LOOKUP_SUB_PATH = "/lookup";
    String PURGE_BY_AGE_SUB_PATH = "/purgeByAge";
    String PURGE_BY_FEED_AGE_SUB_PATH = "/purgeByFeedByAge";
    String PURGE_BY_STREAM_SUB_PATH = "/purgeByStream";
    String CLEAR_BUFFER_POOL_PATH = "/clearBufferPool";
    String QUERY_PARAM_NODE_NAME = "nodeName";

    @GET
    @Path(ENTRIES_SUB_PATH)
    @Operation(
            summary = "List entries from the reference data store on the node called.",
            description = "This is primarily intended  for small scale debugging in non-production environments. If " +
                    "no limit is set a default limit is applied else the results will be limited to limit entries.",
            operationId = "getReferenceStoreEntries")
    List<RefStoreEntry> entries(@QueryParam("limit") final Integer limit,
                                @QueryParam("refStreamId") final Long refStreamId,
                                @QueryParam("mapName") final String mapName);

    @GET
    @Path(REF_STREAM_INFO_SUB_PATH)
    @Operation(
            summary = "List processing info entries for all ref streams",
            description = "This is primarily intended  for small scale debugging in non-production environments. If " +
                    "no limit is set a default limit is applied else the results will be limited to limit entries. " +
                    "Performed on this node only.",
            operationId = "getReferenceStreamProcessingInfoEntries")
    List<ProcessingInfoResponse> refStreamInfo(@QueryParam("limit") final Integer limit,
                                               @QueryParam("refStreamId") final Long refStreamId,
                                               @QueryParam("mapName") final String mapName);

    @POST
    @Path(LOOKUP_SUB_PATH)
    @Operation(
            summary = "Perform a reference data lookup using the supplied lookup request. " +
                    "Reference data will be loaded if required using the supplied reference pipeline. " +
                    "Performed on this node only.",
            operationId = "lookupReferenceData")
    String lookup(@Valid @NotNull final RefDataLookupRequest refDataLookupRequest);

    @DELETE
    @Path(PURGE_BY_AGE_SUB_PATH + "/{purgeAge}")
    @Operation(
            summary = "Explicitly delete all entries that are older than purgeAge. Performed on the named node, " +
                    "or all nodes if null.",
            operationId = "purgeReferenceDataByAge")
    boolean purgeByAge(@NotNull @PathParam("purgeAge") final String purgeAge,
                       @Nullable @QueryParam(QUERY_PARAM_NODE_NAME) final String nodeName);

    @DELETE
    @Path(PURGE_BY_FEED_AGE_SUB_PATH + "/{feedName}/{purgeAge}")
    @Operation(
            summary = "Explicitly delete all entries belonging to a feed that are older than purgeAge." +
                    "Performed on the named node, or all nodes if null.",
            operationId = "purgeReferenceDataByAge")
    boolean purgeByFeedByAge(@NotNull @PathParam("feedName") final String feedName,
                             @NotNull @PathParam("purgeAge") final String purgeAge,
                             @Nullable @QueryParam(QUERY_PARAM_NODE_NAME) final String nodeName);

    @DELETE
    @Path(PURGE_BY_STREAM_SUB_PATH + "/{refStreamId}")
    @Operation(
            summary = "Delete all entries for a reference stream. " +
                    "Performed on the named node or all nodes if null.",
            operationId = "purgeReferenceDataByStream")
    boolean purgeByStreamId(@Min(1) @PathParam("refStreamId") final long refStreamId,
                            @Nullable @QueryParam(QUERY_PARAM_NODE_NAME) final String nodeName);

    @DELETE
    @Path(CLEAR_BUFFER_POOL_PATH)
    @Operation(
            summary = "Clear all buffers currently available in the buffer pool to reclaim memory. " +
                    "Performed on the named node or all nodes if null.",
            operationId = "clearBufferPool")
    void clearBufferPool(@Nullable @QueryParam(QUERY_PARAM_NODE_NAME) final String nodeName);
}
