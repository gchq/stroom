/*
 * Copyright 2026 Crown Copyright
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

package stroom.sqlstore.shared;

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TemporalEntry;
import stroom.util.shared.TemporalEntryId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;

/**
 * REST resource for the SQL-backed updatable temporal store.
 *
 * <p>A <em>temporal store</em> holds versioned key-value entries where each
 * version is identified by an <em>effective time</em> (milliseconds since the
 * Unix epoch).  For any given {@code (map, key)} pair the store keeps the full
 * history of versions, allowing callers to retrieve the value that was in
 * effect at any arbitrary point in time.</p>
 *
 * <h3>Data model</h3>
 * <pre>
 *   (map_name, key, effective_time_ms) → value
 * </pre>
 * <ul>
 *   <li>{@code map_name} — logical namespace; typically the name of the
 *       {@link SqlTemporalStoreDoc} document that owns the data.</li>
 *   <li>{@code key} — identifier for the object within the map
 *       (e.g. a floor-map object ID).</li>
 *   <li>{@code effective_time_ms} — the point in time from which this version
 *       of the value is considered current.</li>
 *   <li>{@code value} — the payload, typically a JSON string.</li>
 * </ul>
 *
 * <h3>Temporal deduplication</h3>
 * <p>When querying by time (e.g. {@link #find} with an {@code EffectiveTime ≤ T}
 * filter, or {@link #fetchAtTime}), the store returns the single most-recent
 * version for each key whose effective time is at or before the requested
 * instant — effectively giving a consistent snapshot of the map at time
 * {@code T}.</p>
 */
@Tag(name = "SqlTemporalStore")
@Path(SqlTemporalStoreResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface SqlTemporalStoreResource extends RestResource, DirectRestService {

    String BASE_PATH = "/sqltemporalstore" + ResourcePaths.V1;

    /**
     * Deletes <strong>all</strong> entries belonging to the store identified by
     * the supplied {@link DocRef}.
     *
     * <p>Requires {@code EDIT} permission on the document.</p>
     *
     * @param docRef reference to the {@link SqlTemporalStoreDoc} whose data
     *               should be cleared; must not be {@code null}
     * @return {@code true} if the operation succeeded
     */
    @DELETE
    @Path("/clear")
    @Operation(
            summary = "Clear a store",
            operationId = "clearSqlTemporalStore",
            description = "Deletes all entries for the store identified by the supplied DocRef. "
                    + "Requires EDIT permission on the document.")
    Boolean clear(@Parameter(description = "docRef", required = true) DocRef docRef);

    /**
     * Returns the total number of entries (across all keys and all effective
     * times) in the store identified by the supplied {@link DocRef}.
     *
     * <p>Requires {@code VIEW} permission on the document.</p>
     *
     * @param docRef reference to the {@link SqlTemporalStoreDoc} to count;
     *               must not be {@code null}
     * @return the total row count, or {@code 0} if the store is empty
     */
    @POST
    @Path("/count")
    @Operation(
            summary = "Count store entries",
            operationId = "countSqlTemporalStoreEntries",
            description = "Returns the total number of entries (all keys, all effective times) "
                    + "in the store identified by the supplied DocRef.")
    Long count(@Parameter(description = "docRef", required = true) DocRef docRef);

    /**
     * Creates a new temporal entry in the store.
     *
     * <p>If an entry already exists for the same {@code (map, key,
     * effective_time)} triple, its {@code value} is overwritten
     * (upsert semantics).</p>
     *
     * <p>Requires {@code EDIT} permission on the map.</p>
     *
     * @param entry the entry to create; {@code map}, {@code key},
     *              {@code effectiveTimeMs}, and {@code value} must all be set
     * @return the entry as persisted
     */
    @POST
    @Path("/entry")
    @Operation(
            summary = "Create an entry",
            operationId = "createSqlTemporalStoreEntry",
            description = "Creates a new temporal entry. If an entry already exists for the same "
                    + "(map, key, effectiveTimeMs) triple the value is overwritten (upsert).")
    TemporalEntry create(@Parameter(description = "entry", required = true) TemporalEntry entry);

    /**
     * Updates an existing temporal entry.
     *
     * <p>Equivalent to {@link #create} — both operations use upsert semantics,
     * so they are interchangeable. This method is provided as a semantically
     * distinct endpoint for callers that wish to signal intent.</p>
     *
     * @param entry the entry to update; {@code map}, {@code key},
     *              and {@code effectiveTimeMs} identify the row to update
     * @return the entry as persisted
     */
    @PUT
    @Path("/entry")
    @Operation(
            summary = "Update an entry",
            operationId = "updateSqlTemporalStoreEntry",
            description = "Updates an existing temporal entry. Uses upsert semantics — "
                    + "equivalent to create if the entry does not already exist.")
    TemporalEntry update(@Parameter(description = "entry", required = true) TemporalEntry entry);


    /**
     * Deletes the entry identified by its composite primary key
     * {@code (map, key, effectiveTimeMs)}.
     *
     * <p>Requires {@code DELETE} permission on the map.</p>
     *
     * @param id composite key identifying the specific version to delete
     * @return {@code true} if a row was deleted; {@code false} if not found
     */
    @POST
    @Path("/entry/delete")
    @Operation(
            summary = "Delete a specific entry",
            operationId = "deleteSqlTemporalStoreEntry",
            description = "Deletes the entry identified by (map, key, effectiveTimeMs). "
                    + "Returns true if a row was deleted.")
    Boolean delete(@Parameter(description = "id", required = true) TemporalEntryId id);

    /**
     * Searches for entries matching the supplied {@link ExpressionCriteria}.
     *
     * <p>When the criteria contain an {@code EffectiveTime ≤ T} term the store
     * performs a <em>temporal deduplication</em> query: for each
     * {@code (map, key)} pair it returns only the entry with the greatest
     * {@code effective_time} that is still {@code ≤ T}, giving a consistent
     * snapshot of the store at time {@code T}.</p>
     *
     * <p>When no time term is present all matching rows across all historical
     * versions are returned.</p>
     *
     * <p>Results are filtered by {@code VIEW} permission on each map before
     * being returned.</p>
     *
     * @param criteria filter expression and optional pagination; must include a
     *                 {@code Map = <name>} term to identify the store
     * @return page of matching entries
     */
    @POST
    @Path("/find")
    @Operation(
            summary = "Find entries",
            operationId = "findSqlTemporalStoreEntries",
            description = "Returns entries matching the supplied criteria. When an EffectiveTime <= T "
                    + "term is present, only the latest version of each key at or before T is returned "
                    + "(temporal deduplication). Without a time term, all historical versions are returned.")
    ResultPage<TemporalEntry> find(
            @Parameter(description = "criteria", required = true) ExpressionCriteria criteria);

    /**
     * Returns the latest-at-time version of every key in the specified map.
     *
     * <p>For each key in {@code mapName} the server returns the single entry
     * whose {@code effective_time} is the greatest value that is still
     * {@code ≤ request.timeTo}, giving a consistent snapshot of the map as
     * it appeared at that instant.</p>
     *
     * <p>Pass the timeline slider position <strong>exactly</strong> as
     * {@code timeTo} — adding any buffer would silently return data from the
     * future relative to the user's chosen instant. For an unconstrained
     * "show all" query use {@link #fetchAll(String)} instead.</p>
     *
     * <p>Requires {@code VIEW} permission on the map.</p>
     *
     * @param request specifies {@code mapName} and {@code timeTo}; see
     *                {@link FetchAtTimeRequest} for full semantics
     * @return deduplicated list of entries — one per key — sorted by key
     *         ascending; never {@code null}, may be empty
     */
    @POST
    @Path("/fetchAtTime")
    @Operation(
            summary = "Fetch entry per key at a point in time",
            operationId = "fetchSqlTemporalStoreEntriesAtTime",
            description = "For each key in the specified map, returns the single entry whose "
                    + "effective_time is the greatest value at or before request.timeTo. "
                    + "Results are sorted by key ascending. "
                    + "Pass the timeline slider position exactly as timeTo.")
    List<TemporalEntry> fetchAtTime(
            @Parameter(description = "request", required = true) FetchAtTimeRequest request);

    /**
     * Returns the absolute latest version of every key in the specified map,
     * with no upper-time constraint.
     *
     * <p>No time bound is applied at all, which is the point of the endpoint —
     * hence the simple {@code String} parameter. A future-dated entry is
     * returned like any other, because it is that key's latest version.</p>
     *
     * <p>Use this to back the "Show all" toggle in the Fact List rather than
     * calling {@link #fetchAtTime} with an inflated {@code timeTo}.</p>
     *
     * <p>Requires {@code VIEW} permission on the map.</p>
     *
     * @param mapName name of the map to query; must not be {@code null} or blank
     * @return deduplicated list of entries — one per key — sorted by key
     *         ascending; never {@code null}, may be empty
     */
    @POST
    @Path("/fetchAll")
    @Operation(
            summary = "Fetch absolute latest entry per key (no time constraint)",
            operationId = "fetchAllSqlTemporalStoreEntries",
            description = "Returns the most recent version of every key in the map with no upper-time "
                    + "bound, including entries whose effective time is in the future. "
                    + "Use for the 'Show all' Fact List toggle.")
    List<TemporalEntry> fetchAll(
            @Parameter(description = "mapName", required = true) String mapName);

    /**
     * Returns the minimum and maximum {@code effective_time} values present in
     * the store for the given map, for use in initialising the timeline slider.
     *
     * <p>Executes a single SQL aggregation ({@code MIN} + {@code MAX}) against
     * the store for the specified map. No deduplication is performed.</p>
     *
     * <p>Requires {@code VIEW} permission on the map.</p>
     *
     * @param mapName name of the map to query; must not be {@code null} or blank
     * @return the effective-time range; both fields are {@code null} if the
     *         store contains no entries for the map
     */
    @POST
    @Path("/timeRange")
    @Operation(
            summary = "Get effective-time range for a map",
            operationId = "getSqlTemporalStoreTimeRange",
            description = "Returns MIN(effective_time) and MAX(effective_time) for all entries in the "
                    + "specified map. Used to initialise the timeline slider range in the Floor Map Editor. "
                    + "Both fields are null if the store is empty.")
    TemporalStoreTimeRange getTimeRange(
            @Parameter(description = "mapName", required = true) String mapName);

    /**
     * Applies a list of staged changes atomically in the order they were performed.
     *
     * <p>All operations are executed within a single database transaction.
     * If any operation fails, the transaction is rolled back and a result with
     * {@code success = false} is returned — no partial changes are persisted.</p>
     *
     * <p>On failure the client should display the {@code errorMessage}, clear
     * its pending-changes buffer, and reload all panels from the server.</p>
     *
     * <p>Requires {@code EDIT} permission on the map.</p>
     *
     * @param request ordered list of operations to apply; must not be
     *                {@code null}
     * @return the result indicating success or failure; never {@code null}
     */
    @POST
    @Path("/applyChanges")
    @Operation(
            summary = "Apply staged changes atomically",
            operationId = "applySqlTemporalStoreChanges",
            description = "Applies an ordered list of upsert and delete operations within a single "
                    + "database transaction. A failure in any operation rolls back all changes. "
                    + "Returns success=false (not HTTP 500) on failure so the client can display "
                    + "the errorMessage and reload from the server.")
    ApplyChangesResult applyChanges(
            @Parameter(description = "request", required = true) ApplyChangesRequest request);
}
