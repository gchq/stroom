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

package stroom.sqlstore.impl;

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.index.shared.IndexFieldImpl;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.Query;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchTaskProgress;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.IndexField;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.CoprocessorsImpl;
import stroom.query.common.v2.DataStoreSettings;
import stroom.query.common.v2.FieldInfoResultPageFactory;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.ResultStoreFactory;
import stroom.query.common.v2.SearchProcess;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.sqlstore.api.TemporalEntryValidator;
import stroom.sqlstore.api.UpdatableTemporalStore;
import stroom.sqlstore.shared.ApplyChangesRequest;
import stroom.sqlstore.shared.ApplyChangesResult;
import stroom.sqlstore.shared.ChangeOperation;
import stroom.sqlstore.shared.FetchAtTimeRequest;
import stroom.sqlstore.shared.SqlTemporalStoreDoc;
import stroom.sqlstore.shared.TemporalStoreTimeRange;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.api.TaskTerminatedException;
import stroom.task.shared.TaskProgress;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TemporalEntry;
import stroom.util.shared.TemporalEntryId;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class UpdatableSqlTemporalStore implements UpdatableTemporalStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UpdatableSqlTemporalStore.class);

    /** Audit-log object type for a single temporal-store entry. */
    private static final String LOGGED_ENTRY_TYPE = "Temporal store entry";

    /** Audit-log object type for a whole temporal-store map. */
    private static final String LOGGED_MAP_TYPE = "Temporal store";

    private final UpdatableTemporalStoreDao dao;
    private final SqlTemporalStoreDocStore sqlStoreDocStore;
    private final FieldInfoResultPageFactory fieldInfoResultPageFactory;
    private final CoprocessorsFactory coprocessorsFactory;
    private final ResultStoreFactory resultStoreFactory;
    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final TaskManager taskManager;
    private final SecurityContext securityContext;
    private final Provider<StroomEventLoggingService> eventLoggingServiceProvider;

    @Inject
    public UpdatableSqlTemporalStore(final UpdatableTemporalStoreDao dao,
                                     final SqlTemporalStoreDocStore sqlStoreDocStore,
                                     final FieldInfoResultPageFactory fieldInfoResultPageFactory,
                                     final CoprocessorsFactory coprocessorsFactory,
                                     final ResultStoreFactory resultStoreFactory,
                                     final Executor executor,
                                     final TaskContextFactory taskContextFactory,
                                     final TaskManager taskManager,
                                     final SecurityContext securityContext,
                                     final Provider<StroomEventLoggingService> eventLoggingServiceProvider) {
        this.dao = dao;
        this.sqlStoreDocStore = sqlStoreDocStore;
        this.fieldInfoResultPageFactory = fieldInfoResultPageFactory;
        this.coprocessorsFactory = coprocessorsFactory;
        this.resultStoreFactory = resultStoreFactory;
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.taskManager = taskManager;
        this.securityContext = securityContext;
        this.eventLoggingServiceProvider = eventLoggingServiceProvider;
    }

    @Override
    public TemporalEntry create(final TemporalEntry entry) {
        return eventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "create"))
                .withDescription("Create temporal entry")
                .withDefaultEventAction(event.logging.CreateEventAction.builder()
                        .addObject(entryAsLoggedObject(entry))
                        .build())
                .withSimpleLoggedResult(() -> write(entry))
                .getResultAndLog();
    }

    /// Writes an entry, as [#create] does, but audits it as an update.
    ///
    /// The store keeps one value per key and effective time, so creating and updating are the same
    /// write; only the audit event says which the caller asked for.
    ///
    /// @param entry The entry to write.
    /// @return The entry written.
    @Override
    public TemporalEntry update(final TemporalEntry entry) {
        return eventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "update"))
                .withDescription("Update temporal entry")
                .withDefaultEventAction(event.logging.UpdateEventAction.builder()
                        .withAfter()
                        .addObjects(entryAsLoggedObject(entry))
                        .end()
                        .build())
                .withSimpleLoggedResult(() -> write(entry))
                .getResultAndLog();
    }

    // Everything that can fail - including a missing entry - happens here, inside the logged work, so
    // that every failed attempt is audited rather than escaping before anything is logged.
    private TemporalEntry write(final TemporalEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Entry must not be null.");
        }
        final String docUuid = resolveStore(entry.getMap(), DocumentPermission.EDIT).getUuid();
        throwIfInvalid(TemporalEntryValidator.entryProblem(entry));
        return dao.create(docUuid, entry);
    }

    @Override
    public Optional<TemporalEntry> fetch(final TemporalEntryId id) {
        return dao.fetch(resolveStore(id.getMap(), DocumentPermission.VIEW).getUuid(), id);
    }

    @Override
    public boolean delete(final TemporalEntryId id) {
        return eventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "delete"))
                .withDescription("Delete temporal entry")
                .withDefaultEventAction(event.logging.DeleteEventAction.builder()
                        .addObject(id == null
                                ? entryAsLoggedObject(null, null, null)
                                : entryAsLoggedObject(id.getMap(), id.getKey(), id.getEffectiveTimeMs()))
                        .build())
                .withSimpleLoggedResult(() -> {
                    // Checked inside the logged work so that the failed attempt is audited.
                    if (id == null) {
                        throw new IllegalArgumentException("Entry id must not be null.");
                    }
                    final String docUuid = resolveStore(id.getMap(), DocumentPermission.DELETE).getUuid();
                    return dao.delete(docUuid, id);
                })
                .getResultAndLog();
    }

    @Override
    public DocRef resolveStoreByName(final String mapName) {
        return resolveStore(mapName, DocumentPermission.VIEW);
    }

    @Override
    public ResultPage<TemporalEntry> find(final DocRef storeDocRef, final ExpressionCriteria criteria) {
        // The DocRef may have been resolved on an earlier call and cached by the caller, so
        // re-check permission rather than assuming the earlier grant still holds.
        checkPermission(storeDocRef, DocumentPermission.VIEW);
        return dao.find(storeDocRef.getUuid(), criteria);
    }

    @Override
    public ResultPage<TemporalEntry> find(final ExpressionCriteria criteria) {
        // Resolving the Map term up front both scopes the query to one store and authorises it,
        // which is why the per-row permission filter this method used to apply is gone: every
        // row returned belongs to the single store the caller was granted VIEW on.
        final String docUuid = resolveStore(mapNameFromCriteria(criteria), DocumentPermission.VIEW).getUuid();
        return dao.find(docUuid, criteria);
    }

    /// Deletes every entry in a store.
    ///
    /// Requires `DELETE` permission on the store, as deleting a single entry does: clearing is the
    /// larger deletion, so it cannot need less.
    ///
    /// The audit event names the store by the name it really has, looked up on the server, never the
    /// name in the caller's [DocRef]. The lookup is done as the processing user, so that a caller who
    /// may not even see the store is still audited against its real name. The name is only written to
    /// the audit log; it is never returned to the caller.
    ///
    /// @param docRef The store to clear.
    @Override
    public void clear(final DocRef docRef) {
        final String docUuid = NullSafe.get(docRef, DocRef::getUuid);
        final String storeName = docUuid == null
                ? null
                : securityContext.asProcessingUserResult(() -> sqlStoreDocStore.list().stream()
                        .filter(dr -> docUuid.equals(dr.getUuid()))
                        .map(DocRef::getName)
                        .findFirst()
                        .orElse(null));
        eventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "clear"))
                .withDescription("Clear temporal store " + docUuid
                                 + (storeName == null ? "" : " (" + storeName + ")"))
                .withDefaultEventAction(event.logging.DeleteEventAction.builder()
                        // The whole map is the subject here, not a single entry.
                        .addObject(event.logging.OtherObject.builder()
                                .withType(LOGGED_MAP_TYPE)
                                .withId(docUuid)
                                .withName(storeName)
                                .build())
                        .build())
                .withSimpleLoggedResult(() -> {
                    // Checked inside the logged work so that the failed attempt is audited.
                    if (docUuid == null) {
                        throw new IllegalArgumentException("Store DocRef must not be null.");
                    }
                    checkPermission(docRef, DocumentPermission.DELETE);
                    dao.clear(docUuid);
                    return null;
                })
                .getResultAndLog();
    }

    /**
     * Describes a single temporal-store entry as an audit-log object.
     *
     * <p>The {@code event-logging} schema requires every {@code Create}/{@code Delete}
     * action to carry at least one object — an empty action fails schema validation
     * with {@code cvc-complex-type.2.4.b} when the audit events are themselves
     * ingested, which is how this was found. So the entry's natural key is recorded
     * here, which is also what an audit trail actually needs: <em>which</em> entry
     * changed, not merely that something did.</p>
     *
     * @param mapName         the temporal store (map) name
     * @param key             the entry key
     * @param effectiveTimeMs the entry's effective time
     * @return the object to attach to the event action
     */
    static event.logging.OtherObject entryAsLoggedObject(final String mapName,
                                                         final String key,
                                                         final Long effectiveTimeMs) {
        return event.logging.OtherObject.builder()
                .withType(LOGGED_ENTRY_TYPE)
                .withId(key)
                .withName(mapName + "/" + key)
                .withDescription("Effective time " + effectiveTimeMs)
                .build();
    }

    private static event.logging.OtherObject entryAsLoggedObject(final TemporalEntry entry) {
        return entry == null
                ? entryAsLoggedObject(null, null, null)
                : entryAsLoggedObject(entry.getMap(), entry.getKey(), entry.getEffectiveTimeMs());
    }

    /**
     * Returns the latest-at-time version of every key in the specified map.
     *
     * <p>Delegates temporal deduplication to the existing
     * {@link UpdatableTemporalStoreDao#find} implementation: by supplying an
     * {@code EffectiveTime <= timeTo} term the DAO activates its "QueryTime Path"
     * which joins against a grouped {@code MAX(effective_time)} derived table to select only
     * the most-recent version of each key at or before {@code timeTo}, without fetching all
     * historical versions. (The subquery is grouped and uncorrelated, not correlated.)</p>
     *
     * <p>Requires {@code VIEW} permission on the map.</p>
     *
     * @param request specifies the map name and required upper time bound
     * @return deduplicated list of entries, one per key, sorted by key ascending
     */
    @Override
    public List<TemporalEntry> fetchAtTime(final FetchAtTimeRequest request) {
        if (request == null || request.getMapName() == null || request.getMapName().isBlank()) {
            throw new IllegalArgumentException("mapName must be specified in the request.");
        }
        final String docUuid = resolveStore(request.getMapName(), DocumentPermission.VIEW).getUuid();

        // Build criteria that trigger the DAO's temporal-deduplication subquery path.
        // The Map = mapName term scopes the query to the correct store.
        // The EffectiveTime <= timeTo term is taken by the DAO as the snapshot instant, which
        // switches from a plain SELECT to the grouped MAX(effective_time) subquery, returning one
        // row per key rather than all historical versions.
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(ExpressionTerm.builder()
                        .field(UpdatableTemporalStore.MAP_FIELD.getFldName())
                        .condition(ExpressionTerm.Condition.EQUALS)
                        .value(request.getMapName())
                        .build())
                .addTerm(ExpressionTerm.builder()
                        .field(UpdatableTemporalStore.TIME_FIELD.getFldName())
                        .condition(ExpressionTerm.Condition.LESS_THAN_OR_EQUAL_TO)
                        .value(String.valueOf(request.getTimeTo()))
                        .build())
                .build();

        // Unpaged - we want all keys for the map, up to the DAO's unpaged limit, beyond which it fails
        // rather than return a partial list.
        final ExpressionCriteria criteria = new ExpressionCriteria(expression);
        final ResultPage<TemporalEntry> page = dao.find(docUuid, criteria);

        // Sort by key for a predictable, user-friendly order. No per-row permission filter is
        // needed - the query was scoped to the single store authorised above.
        // Keys are case-sensitive, so "Room1" and "room1" are different keys; the second comparator
        // keeps their order stable rather than leaving it to the database.
        return page.getValues().stream()
                .sorted(Comparator.comparing(TemporalEntry::getKey,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER
                                .thenComparing(Comparator.naturalOrder()))))
                .toList();
    }

    /**
     * Returns the absolute latest version of every key in the specified map,
     * with no upper-time constraint.
     *
     * <p>No upper bound is applied, so a future-dated entry is returned like any
     * other — it is that key's latest version. The client supplies no time value.</p>
     *
     * <p>Delegates deduplication to {@link UpdatableTemporalStoreDao#fetchAll(String)}, which
     * does it in the database via a {@code MAX(effective_time)}-per-key subquery joined back for
     * the full row - only one row per key crosses the wire, not the whole history.</p>
     *
     * <p>Requires {@code VIEW} permission on the map.</p>
     *
     * @param mapName name of the map to query; must not be {@code null} or blank
     * @return deduplicated list — one entry per key — sorted by key ascending
     */
    @Override
    public List<TemporalEntry> fetchAll(final String mapName) {
        if (mapName == null || mapName.isBlank()) {
            throw new IllegalArgumentException("mapName must be specified.");
        }
        return dao.fetchAll(resolveStore(mapName, DocumentPermission.VIEW).getUuid());
    }

    /**
     * Returns the minimum and maximum {@code effective_time} values in the
     * store for the given map, for use in initialising the timeline slider.
     *
     * <p>Requires {@code VIEW} permission on the map.</p>
     *
     * @param mapName name of the map to query; must not be {@code null} or blank
     * @return the time range; both fields {@code null} if the store is empty
     */
    @Override
    public TemporalStoreTimeRange getTimeRange(final String mapName) {
        if (mapName == null || mapName.isBlank()) {
            throw new IllegalArgumentException("mapName must be specified.");
        }
        return dao.getTimeRange(resolveStore(mapName, DocumentPermission.VIEW).getUuid());
    }

    /**
     * Applies a list of staged changes atomically.
     *
     * <p>Validates the request, asserts that all operations target the same map
     * (cross-map batches are rejected outright), checks {@code EDIT} permission
     * on that map - or {@code DELETE} if the batch contains a delete, matching
     * {@link #delete} - then delegates to {@link UpdatableTemporalStoreDao#applyChanges}
     * which wraps all operations in a single JOOQ transaction.</p>
     *
     * <p>Any exception thrown is caught and converted into an
     * {@link ApplyChangesResult} with {@code success = false} so that the REST
     * layer always receives a well-formed response rather than an HTTP 500.
     * Validation and permission failures carry their own message; anything else
     * is logged here and reported with a generic message, so database detail
     * such as SQL text never reaches the client.</p>
     *
     * @param request the ordered list of operations; must not be {@code null}
     * @return result indicating success or failure; never {@code null}
     */
    @Override
    public ApplyChangesResult applyChanges(final ApplyChangesRequest request) {
        if (request == null || request.getOperations() == null) {
            return new ApplyChangesResult(false, "Request must not be null.");
        }

        // Extract the map name from every operation and verify they all agree, and validate each
        // operation, so a batch that is bad anywhere is rejected before any DB work is done.
        String mapName = null;
        boolean containsDelete = false;
        for (int i = 0; i < request.getOperations().size(); i++) {
            final ChangeOperation op = request.getOperations().get(i);
            if (op == null) {
                continue;
            }
            final String opMap = op.getType() == ChangeOperation.Type.UPSERT && op.getEntry() != null
                    ? op.getEntry().getMap()
                    : op.getId() != null
                    ? op.getId().getMap()
                    : null;
            if (opMap == null || opMap.isBlank()) {
                return new ApplyChangesResult(false,
                        "Operation at index " + i + " has a null or blank map name.");
            }
            if (mapName == null) {
                mapName = opMap;
            } else if (!mapName.equals(opMap)) {
                return new ApplyChangesResult(false,
                        "All operations in a batch must target the same map. "
                        + "Expected '" + mapName + "' but operation at index " + i
                        + " targets '" + opMap + "'.");
            }
            final String invalidReason = validateOperation(op);
            if (invalidReason != null) {
                return new ApplyChangesResult(false,
                        "Operation at index " + i + " is invalid: " + invalidReason);
            }
            containsDelete |= op.getType() == ChangeOperation.Type.DELETE;
        }

        if (mapName == null) {
            return new ApplyChangesResult(false,
                    "Could not determine map name: the operations list is empty or contains only nulls.");
        }
        final String finalMapName = mapName;
        try {
            final String docUuid = resolveStoreForChanges(mapName, containsDelete).getUuid();
            dao.applyChanges(docUuid, request.getOperations());
            return new ApplyChangesResult(true, null);
        } catch (final PermissionException | IllegalArgumentException | IllegalStateException e) {
            // Our own messages - which store, which permission, which operation - written for the user.
            LOGGER.debug(() -> LogUtil.message("applyChanges rejected for map '{}': {}",
                    finalMapName, e.getMessage()), e);
            return new ApplyChangesResult(false, e.getMessage());
        } catch (final Exception e) {
            LOGGER.error(() -> LogUtil.message("applyChanges failed for map '{}': {}",
                    finalMapName, e.getMessage()), e);
            return new ApplyChangesResult(false,
                    "Failed to apply changes to temporal store '" + mapName
                    + "'. No changes were saved. See the server log for details.");
        }
    }

    /// Resolves the store a batch of changes targets, requiring `EDIT` permission, or `DELETE` if the
    /// batch contains a delete - the permission [#delete] requires on its own.
    ///
    /// A caller who may edit the store but not delete from it is told that, rather than that the
    /// store is not available: they can already see the store, so saying so discloses nothing.
    private DocRef resolveStoreForChanges(final String mapName, final boolean containsDelete) {
        if (!containsDelete) {
            return resolveStore(mapName, DocumentPermission.EDIT);
        }
        try {
            return resolveStore(mapName, DocumentPermission.DELETE);
        } catch (final PermissionException e) {
            final boolean mayEdit = sqlStoreDocStore.list().stream()
                    .filter(dr -> mapName.equalsIgnoreCase(dr.getName()))
                    .anyMatch(dr -> securityContext.hasDocumentPermission(dr, DocumentPermission.EDIT));
            if (!mayEdit) {
                throw e;
            }
            throw new PermissionException(securityContext.getUserRef(),
                    "Deleting entries from temporal store '" + mapName
                    + "' requires DELETE permission on it, which you do not have.");
        }
    }

    // Checks one operation of an applyChanges batch against the rules every write path shares - see
    // TemporalEntryValidator. Returns why the operation is invalid, or null if it is valid.
    private static String validateOperation(final ChangeOperation op) {
        if (op.getType() == null) {
            return "it has no type.";
        }
        if (op.getType() == ChangeOperation.Type.UPSERT) {
            return op.getEntry() == null
                    ? "an upsert must have an entry."
                    : TemporalEntryValidator.entryProblem(op.getEntry());
        }
        return op.getId() == null
                ? "a delete must have an id."
                : TemporalEntryValidator.idProblem(op.getId());
    }

    private static void throwIfInvalid(final String problem) {
        if (problem != null) {
            throw new IllegalArgumentException("Invalid temporal entry: " + problem);
        }
    }

    private void checkPermission(final DocRef docRef, final DocumentPermission permission) {
        if (!securityContext.hasDocumentPermission(docRef, permission)) {
            // Named by UUID, the only part of the caller's DocRef that was checked: its name is
            // whatever the caller sent, and may not be the store's.
            throw new PermissionException(securityContext.getUserRef(),
                    "User does not have " + permission + " permission on temporal store "
                    + docRef.getUuid());
        }
    }

    /**
     * Resolves a store name to the document that bears it, considering <strong>only documents
     * the caller may already see</strong>.
     *
     * <p>Filtering by permission before matching is what stops this method disclosing the
     * existence of documents the caller has no right to know about. </p>
     *
     * <p>Names are matched case-insensitively, consistent with the pipeline-reference gate in
     * {@code ReferenceData}, which compares with {@code equalsIgnoreCase}.</p>
     *
     * <p>Document names are neither unique nor immutable, so a name may match more than one
     * store. That is rejected rather than resolved arbitrarily: silently picking the first
     * match could let one document's operations land on another's data. Storage
     * is keyed by UUID, so an ambiguous <em>name</em> never means mixed data - only that this
     * particular request cannot be attributed to one store.</p>
     *
     * @param mapName    the store name to resolve; must not be {@code null} or blank
     * @param permission the permission the caller must hold on the store
     * @return the DocRef of the single matching store document; never {@code null}
     * @throws PermissionException   if no store of that name is visible to the caller with
     *                               the required permission
     * @throws IllegalStateException if more than one visible store bears the name
     */
    private DocRef resolveStore(final String mapName, final DocumentPermission permission) {
        if (mapName == null || mapName.isBlank()) {
            throw new IllegalArgumentException("Store name must be specified.");
        }
        final List<DocRef> matches = sqlStoreDocStore.list().stream()
                .filter(dr -> securityContext.hasDocumentPermission(dr, permission))
                .filter(dr -> mapName.equalsIgnoreCase(dr.getName()))
                .toList();

        if (matches.isEmpty()) {
            // Deliberately one message for both "no such store" and "exists but you may not
            // see it" - distinguishing them would disclose the existence of a document the
            // caller has no permission to know about.
            throw new PermissionException(securityContext.getUserRef(),
                    "No temporal store named '" + mapName + "' is available.");
        }
        if (matches.size() > 1) {
            throw new IllegalStateException(
                    "'" + mapName + "' does not identify a single temporal store - "
                    + matches.size() + " stores share that name. Rename one so the name is "
                    + "unambiguous.");
        }
        return matches.getFirst();
    }

    /**
     * Extracts the store name from a {@code Map} term in the criteria. The name is used to
     * resolve and authorise the store; it is stripped before the criteria reach SQL, because
     * the {@code map_name} column is only a label.
     */
    private String mapNameFromCriteria(final ExpressionCriteria criteria) {
        if (criteria == null || criteria.getExpression() == null) {
            throw new IllegalArgumentException("Query criteria and expression must be defined.");
        }
        return ExpressionUtil.terms(criteria.getExpression(),
                        List.of(UpdatableTemporalStore.MAP_FIELD.getFldName()))
                .stream()
                .map(ExpressionTerm::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Map name must be defined in the query criteria."));
    }

    @Override
    public String getDataSourceType() {
        return SqlTemporalStoreDoc.TYPE;
    }

    @Override
    public List<DocRef> getDataSourceDocRefs() {
        return sqlStoreDocStore.list();
    }

    @Override
    public Optional<QueryField> getTimeField(final DocRef docRef) {
        return Optional.of(TIME_FIELD);
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        return fieldInfoResultPageFactory.create(criteria, FIELDS);
    }

    @Override
    public int getFieldCount(final DocRef docRef) {
        return FIELDS.size();
    }

    @SuppressWarnings("unused")
    public IndexField getIndexField(final DocRef docRef, final String fieldName) {

        return FIELDS.stream()
                .filter(f -> f.getFldName().equals(fieldName))
                .findFirst()
                .map(f -> IndexFieldImpl.builder().fldName(f.getFldName()).fldType(f.getFldType()).build())
                .orElse(null);
    }

    @Override
    public ResultStore createResultStore(final SearchRequest searchRequest) {
        // Replace expression parameters.
        final SearchRequest modifiedSearchRequest = ExpressionUtil.replaceExpressionParameters(searchRequest);

        // Get the search.
        final Query query = modifiedSearchRequest.getQuery();

        // Load the doc.
        final DocRef docRef = query.getDataSource();

        // Check we have permission to read the doc. The search request names the datasource by
        // DocRef, so authorise and scope on that directly rather than resolving its name.
        checkPermission(docRef, DocumentPermission.VIEW);

        // Create a coprocessor settings list.
        final List<CoprocessorSettings> coprocessorSettingsList = coprocessorsFactory
                .createSettings(modifiedSearchRequest);

        // Create a handler for search results.
        final DataStoreSettings dataStoreSettings = DataStoreSettings
                .createBasicSearchResultStoreSettings();
        final CoprocessorsImpl coprocessors = coprocessorsFactory.create(
                modifiedSearchRequest.getSearchRequestSource(),
                modifiedSearchRequest.getDateTimeSettings(),
                modifiedSearchRequest.getKey(),
                coprocessorSettingsList,
                query.getParams(),
                dataStoreSettings);

        final ResultStore resultStore = resultStoreFactory.create(
                modifiedSearchRequest.getSearchRequestSource(),
                coprocessors);

        final String searchName = "Search '" + modifiedSearchRequest.getKey().toString() + "'";

        final ExpressionOperator.Builder builder = ExpressionOperator.builder();
        if (query.getExpression() != null) {
            builder.addOperator(query.getExpression());
        }
        builder.addTerm(
                UpdatableTemporalStore.MAP_FIELD.getFldName(),
                ExpressionTerm.Condition.EQUALS,
                docRef.getName());

        final ExpressionCriteria criteria = new ExpressionCriteria(builder.build());

        final Runnable runnable = taskContextFactory.context(searchName, taskContext -> {
            // Set if the search is terminated, so the read is skipped if termination was requested
            // before the task got the chance to start, and stopped part-way if it was requested after.
            final AtomicBoolean destroyed = new AtomicBoolean();

            // Links the result store to this task, so it can report the task's progress and
            // terminate the task when the search is cancelled or the result store destroyed.
            final SearchProcess searchProcess = new SearchProcess() {
                @Override
                public SearchTaskProgress getSearchTaskProgress() {
                    final TaskProgress taskProgress = taskManager.getTaskProgress(taskContext);
                    if (taskProgress != null) {
                        return new SearchTaskProgress(
                                taskProgress.getTaskName(),
                                taskProgress.getTaskInfo(),
                                taskProgress.getUserRef(),
                                taskProgress.getThreadName(),
                                taskProgress.getNodeName(),
                                taskProgress.getSubmitTimeMs(),
                                taskProgress.getTimeNowMs());
                    }
                    return null;
                }

                @Override
                public void onTerminate() {
                    destroyed.set(true);
                    taskManager.terminate(taskContext.getTaskId());
                }
            };

            // If the result store has already been terminated, this immediately calls
            // onTerminate(), setting `destroyed`, so the read below is skipped.
            resultStore.setSearchProcess(searchProcess);

            try {
                if (!destroyed.get()) {
                    // The value column is a longtext; only read it if a coprocessor actually wants it.
                    // The timeline histogram asks for times alone, so this keeps every row's payload
                    // out of the result set for it.
                    final boolean includeValue = fieldIndexWants(
                            coprocessors, UpdatableTemporalStore.VALUE_FIELD.getFldName());

                    dao.search(docRef.getUuid(), criteria, includeValue, entry -> {
                        // Throwing stops the database read, which streams from a cursor.
                        if (destroyed.get()) {
                            throw new TaskTerminatedException();
                        }
                        taskContext.checkTermination();
                        coprocessors.accept(toValues(coprocessors, entry));
                    });
                }
            } catch (final TaskTerminatedException e) {
                LOGGER.debug("SQL temporal store search terminated: {}", searchName);
            } catch (final RuntimeException e) {
                LOGGER.error(() -> "Error running SQL temporal store search: " + e.getMessage(), e);
                resultStore.addError(e);
            } finally {
                resultStore.signalComplete();
            }
        });

        CompletableFuture.runAsync(runnable, executor);

        return resultStore;
    }

    @Override
    public long count(final DocRef docRef) {
        checkPermission(docRef, DocumentPermission.VIEW);
        return dao.count(docRef.getUuid());
    }

    /** Converts an entry to the values the search's coprocessors expect, in field-index order. */
    private static Val[] toValues(final CoprocessorsImpl coprocessors, final TemporalEntry entry) {
        final Map<String, Object> attributeMap = new HashMap<>();
        attributeMap.put(UpdatableTemporalStore.MAP_FIELD.getFldName(), entry.getMap());
        attributeMap.put(UpdatableTemporalStore.KEY_FIELD.getFldName(), entry.getKey());
        attributeMap.put(UpdatableTemporalStore.TIME_FIELD.getFldName(), entry.getEffectiveTimeMs());
        attributeMap.put(UpdatableTemporalStore.VALUE_FIELD.getFldName(), entry.getValue());

        final String[] fields = coprocessors.getFieldIndex().getFields();
        final Val[] arr = new Val[fields.length];
        for (int i = 0; i < fields.length; i++) {
            final String fieldName = fields[i];
            Val val = ValNull.INSTANCE;
            if (fieldName != null) {
                final Object o = attributeMap.get(fieldName);
                if (o != null) {
                    if (UpdatableTemporalStore.TIME_FIELD.getFldName().equals(fieldName)) {
                        val = ValDate.create((Long) o);
                    } else {
                        val = ValString.create(String.valueOf(o));
                    }
                }
            }
            arr[i] = val;
        }
        return arr;
    }

    /** Whether the search's coprocessors reference the named field at all. */
    private static boolean fieldIndexWants(final CoprocessorsImpl coprocessors, final String fieldName) {
        for (final String field : coprocessors.getFieldIndex().getFields()) {
            if (fieldName.equals(field)) {
                return true;
            }
        }
        return false;
    }
}
