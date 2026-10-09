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

package stroom.sqlstore.api;

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.SearchProvider;
import stroom.sqlstore.shared.ApplyChangesRequest;
import stroom.sqlstore.shared.ApplyChangesResult;
import stroom.sqlstore.shared.FetchAtTimeRequest;
import stroom.sqlstore.shared.TemporalStoreTimeRange;
import stroom.util.shared.HasCrud;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TemporalEntry;
import stroom.util.shared.TemporalEntryId;

import java.util.List;

/**
 * Service interface for a SQL-backed updatable temporal store.
 *
 * <p>A temporal store holds versioned key-value entries where each version is
 * identified by an <em>effective time</em> (milliseconds since the Unix epoch).
 * For any given {@code (map, key)} pair the store keeps the full history of
 * versions, allowing callers to retrieve the value that was in effect at any
 * arbitrary point in time.</p>
 *
 * <h3>Data model</h3>
 * <pre>
 *   (map_name, key, effective_time_ms) → value
 * </pre>
 * <ul>
 *   <li>{@code map_name} — logical namespace, typically the name of the
 *       document that owns the data (e.g. a floor-map name).</li>
 *   <li>{@code key} — identifier for the object within the map.</li>
 *   <li>{@code effective_time_ms} — the point in time from which this version
 *       is considered current. Multiple versions for the same key are stored
 *       independently and are not automatically overwritten.</li>
 *   <li>{@code value} — the payload; typically a JSON string.</li>
 * </ul>
 *
 * <h3>Temporal deduplication</h3>
 * <p>When querying by time (via {@link #find} with an
 * {@code EffectiveTime ≤ T} term, the store
 * returns only the single most-recent version for each key whose effective time
 * is at or before the requested instant, giving a consistent point-in-time
 * snapshot of the map.</p>
 *
 * <h3>Inheritance</h3>
 * <p>This interface extends {@link HasCrud} which provides the basic
 * {@link HasCrud#create create}, {@link HasCrud#fetch fetch},
 * {@link HasCrud#update update}, and {@link HasCrud#delete delete} operations
 * on individual {@link TemporalEntry} instances identified by
 * {@link TemporalEntryId}. It also extends {@link SearchProvider} so that the
 * store can participate in Stroom's generic search / dashboard infrastructure.</p>
 *
 * <h3>Field constants</h3>
 * <p>{@link #MAP_FIELD}, {@link #KEY_FIELD}, {@link #TIME_FIELD}, and
 * {@link #VALUE_FIELD} are the {@link QueryField} descriptors used when
 * building {@link ExpressionCriteria} filters against this store. Use these
 * constants rather than raw strings to guard against field-name typos.</p>
 */
public interface UpdatableTemporalStore extends HasCrud<TemporalEntry, TemporalEntryId>, SearchProvider {

    /**
     * The map-name field — a text field that scopes a query to a specific
     * logical namespace within the store. Must be included in every
     * {@link ExpressionCriteria} passed to {@link #find}.
     */
    QueryField MAP_FIELD = QueryField.createText("Map");

    /**
     * The key field — a text field that identifies a specific object within
     * a map. Can be used in an {@link ExpressionCriteria} to restrict results
     * to a single object.
     */
    QueryField KEY_FIELD = QueryField.createText("Key");

    /**
     * The effective-time field — a date field representing the instant from
     * which a version of an object is considered current, in milliseconds
     * since the Unix epoch.
     *
     * <p>When an {@code EffectiveTime ≤ T} term is included in an
     * {@link ExpressionCriteria} passed to {@link #find}, the store activates
     * its temporal-deduplication path and returns only the latest version of
     * each key at or before {@code T}.</p>
     */
    QueryField TIME_FIELD = QueryField.createDate("EffectiveTime");

    /**
     * The value field — a text field containing the payload of a temporal
     * entry; typically a JSON string whose schema is defined by the
     * application writing to the store.
     */
    QueryField VALUE_FIELD = QueryField.createText("Value");

    /**
     * Ordered list of all queryable fields exposed by this store.
     * Used by Stroom's field-info infrastructure to enumerate available
     * columns for dashboard and search configuration.
     */
    List<QueryField> FIELDS = List.of(
            MAP_FIELD,
            KEY_FIELD,
            TIME_FIELD,
            VALUE_FIELD
    );

    /**
     * Searches the store for entries matching the supplied criteria.
     *
     * <p>The behaviour depends on whether an {@code EffectiveTime} term is
     * present in the expression:</p>
     * <ul>
     *   <li><b>With an {@code EffectiveTime ≤ T} term</b> — temporal
     *       deduplication is applied. For each {@code (map, key)} pair the
     *       store returns only the entry with the greatest
     *       {@code effective_time} that is still {@code ≤ T}, giving a
     *       consistent snapshot of the store at time {@code T}.</li>
     *   <li><b>Without a time term</b> — all matching rows across every
     *       historical version are returned.</li>
     * </ul>
     *
     * <p>A {@code Map = <name>} term must always be present in the criteria
     * to scope the query to a specific store.</p>
     *
     * @param criteria filter expression and optional pagination settings;
     *                 must include a {@link #MAP_FIELD} equality term
     * @return page of matching entries; never {@code null}
     */
    ResultPage<TemporalEntry> find(ExpressionCriteria criteria);

    /**
     * Resolves a store name to the document that bears it, considering only documents the caller
     * may already see.
     *
     * <p>For callers that look up the same store repeatedly - an XSLT {@code lookup()} runs once
     * per event - so the name can be resolved once and the resulting {@link DocRef} passed to
     * {@link #find(DocRef, ExpressionCriteria)} thereafter. Resolution is not free: it lists the
     * document store, which is a database query with no cache behind it.</p>
     *
     * @param mapName the store name; must not be {@code null} or blank
     * @return the single matching store document; never {@code null}
     * @throws stroom.util.shared.PermissionException if no store of that name is available to the
     *                                                caller
     * @throws IllegalStateException                  if more than one visible store bears the name
     */
    DocRef resolveStoreByName(String mapName);

    /**
     * As {@link #find(ExpressionCriteria)} but scoped by an already-resolved store document,
     * skipping the name resolution and its document-store listing.
     *
     * <p>Permission is still checked on every call - a cached {@link DocRef} is a cached identity,
     * not cached authorisation.</p>
     *
     * @param storeDocRef the store to query; must not be {@code null}
     * @param criteria    filter expression and optional pagination settings
     * @return page of matching entries; never {@code null}
     */
    ResultPage<TemporalEntry> find(DocRef storeDocRef, ExpressionCriteria criteria);

    /**
     * Returns the latest-at-time version of every key in the map specified by
     * {@link FetchAtTimeRequest#getMapName()}.
     *
     * <p>For each key the entry whose {@code effective_time} is the greatest
     * value at or before {@link FetchAtTimeRequest#getTimeTo()} is
     * selected, giving a consistent snapshot of the store at that instant.</p>
     *
     * <p>This is the preferred endpoint for populating list-style UI panels
     * because the server handles all deduplication and sorting, keeping the
     * client simple. Prefer this over calling {@link #find} and deduplicating
     * on the client.</p>
     *
     * @param request specifies the map name and required upper time bound;
     *                must not be {@code null}; see {@link FetchAtTimeRequest}
     *                for full semantics
     * @return deduplicated list of entries — one per key — sorted by key
     *         ascending; never {@code null}, may be empty
     */
    List<TemporalEntry> fetchAtTime(FetchAtTimeRequest request);

    /**
     * Returns the absolute latest version of every key in the specified map,
     * with no upper-time constraint.
     *
     * <p>No time value is supplied and none is applied: an entry dated in the
     * future is still that key's latest version, and is returned. Use
     * {@link #fetchAtTime} when the answer should be bounded to an instant.</p>
     *
     * <p>Used to back the "Show all" toggle in the Floor Map Editor Fact List.</p>
     *
     * <p>Requires {@code VIEW} permission on the map.</p>
     *
     * @param mapName name of the map to query; must not be {@code null} or blank
     * @return deduplicated list — one entry per key — sorted by key ascending;
     *         never {@code null}, may be empty
     */
    List<TemporalEntry> fetchAll(String mapName);

    /**
     * Returns the minimum and maximum {@code effective_time} values present in
     * the store for the given map, used to initialise the timeline slider range
     * in the Floor Map Editor.
     *
     * <p>Executed as a single SQL aggregation ({@code MIN} + {@code MAX}) with
     * no row-level deduplication.</p>
     *
     * <p>Requires {@code VIEW} permission on the map.</p>
     *
     * @param mapName name of the map to query; must not be {@code null} or blank
     * @return the time range; both fields {@code null} if the store is empty
     */
    TemporalStoreTimeRange getTimeRange(String mapName);

    /**
     * Applies a list of staged changes atomically in the order the user
     * performed them.
     *
     * <p>All operations in {@link ApplyChangesRequest#getOperations()} are
     * executed within a single database transaction. If any operation fails
     * the transaction is rolled back and an {@link ApplyChangesResult} with
     * {@code success = false} is returned — no partial changes are persisted.</p>
     *
     * <p>The caller should check {@link ApplyChangesResult#isSuccess()} and,
     * on failure, display {@link ApplyChangesResult#getErrorMessage()} and
     * reload all UI panels from the server.</p>
     *
     * <p>Requires {@code EDIT} permission on the map.</p>
     *
     * @param request the ordered list of operations to apply; must not be
     *                {@code null}
     * @return the result indicating success or failure; never {@code null}
     */
    ApplyChangesResult applyChanges(ApplyChangesRequest request);

    /**
     * Deletes all entries for the specified map, across all keys and all
     * effective times.
     *
     * @param docRef reference to the store document to clear; must not be
     *               {@code null}
     */
    void clear(DocRef docRef);

    /**
     * Returns the total number of entries in the store document identified by
     * the supplied {@link DocRef}, counting all keys and all effective-time
     * versions.
     *
     * @param docRef reference to the store document whose rows should be
     *               counted; must not be {@code null}
     * @return total row count; {@code 0} if the store is empty
     */
    long count(DocRef docRef);
}
