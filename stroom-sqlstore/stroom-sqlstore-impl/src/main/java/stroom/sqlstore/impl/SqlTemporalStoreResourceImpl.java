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
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.sqlstore.shared.ApplyChangesRequest;
import stroom.sqlstore.shared.ApplyChangesResult;
import stroom.sqlstore.shared.ChangeOperation;
import stroom.sqlstore.shared.FetchAtTimeRequest;
import stroom.sqlstore.shared.SqlTemporalStoreResource;
import stroom.sqlstore.shared.TemporalStoreTimeRange;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TemporalEntry;
import stroom.util.shared.TemporalEntryId;

import event.logging.BaseObject;
import event.logging.ComplexLoggedOutcome;
import event.logging.OtherObject;
import event.logging.UpdateEventAction;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;
import java.util.Objects;

/// REST resource for the SQL temporal store.
///
/// The read methods, `count` among them, are auto-logged. The write methods are not: `create`, `update`, `delete` and
/// `clear` are audited by [UpdatableSqlTemporalStore] itself, so auto-logging them as well wrote
/// every event twice, and `applyChanges` is audited here, because the auto-logger could neither
/// infer an operation from the name "apply" nor see that a `success = false` result was a failure.
@AutoLogged
class SqlTemporalStoreResourceImpl implements SqlTemporalStoreResource {

    // Audit-log object type standing in for the changes when a request names none.
    private static final String LOGGED_REQUEST_TYPE = "Temporal store change request";

    private final Provider<UpdatableSqlTemporalStore> updatableSqlTemporalStoreProvider;
    private final Provider<StroomEventLoggingService> eventLoggingServiceProvider;

    @Inject
    SqlTemporalStoreResourceImpl(
            final Provider<UpdatableSqlTemporalStore> updatableSqlTemporalStoreProvider,
            final Provider<StroomEventLoggingService> eventLoggingServiceProvider) {
        this.updatableSqlTemporalStoreProvider = updatableSqlTemporalStoreProvider;
        this.eventLoggingServiceProvider = eventLoggingServiceProvider;
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public Boolean clear(final DocRef docRef) {
        updatableSqlTemporalStoreProvider.get().clear(docRef);
        return true;
    }

    @AutoLogged(OperationType.VIEW)
    @Override
    public Long count(final DocRef docRef) {
        return updatableSqlTemporalStoreProvider.get().count(docRef);
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public TemporalEntry create(final TemporalEntry entry) {
        return updatableSqlTemporalStoreProvider.get().create(entry);
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public TemporalEntry update(final TemporalEntry entry) {
        return updatableSqlTemporalStoreProvider.get().update(entry);
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public Boolean delete(final TemporalEntryId id) {
        return updatableSqlTemporalStoreProvider.get().delete(id);
    }

    @Override
    public ResultPage<TemporalEntry> find(final ExpressionCriteria criteria) {
        return updatableSqlTemporalStoreProvider.get().find(criteria);
    }

    @Override
    public List<TemporalEntry> fetchAtTime(final FetchAtTimeRequest request) {
        return updatableSqlTemporalStoreProvider.get().fetchAtTime(request);
    }

    @Override
    public List<TemporalEntry> fetchAll(final String mapName) {
        return updatableSqlTemporalStoreProvider.get().fetchAll(mapName);
    }

    @Override
    public TemporalStoreTimeRange getTimeRange(final String mapName) {
        return updatableSqlTemporalStoreProvider.get().getTimeRange(mapName);
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public ApplyChangesResult applyChanges(final ApplyChangesRequest request) {
        final List<ChangeOperation> operations = NullSafe.list(
                NullSafe.get(request, ApplyChangesRequest::getOperations));
        final List<BaseObject> operationObjects = operations.stream()
                .filter(Objects::nonNull)
                .map(SqlTemporalStoreResourceImpl::operationAsLoggedObject)
                .filter(Objects::nonNull)
                .toList();
        // The event-logging schema requires at least one object, and a request with no usable
        // operations - which is rejected - must still be audited with a valid event.
        final List<BaseObject> changedEntries = operationObjects.isEmpty()
                ? List.of(OtherObject.builder()
                        .withType(LOGGED_REQUEST_TYPE)
                        .withDescription("A request with no valid operations")
                        .build())
                : operationObjects;
        final UpdateEventAction updateEventAction = UpdateEventAction.builder()
                .withAfter()
                .addObjects(changedEntries)
                .end()
                .build();

        return eventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "applyChanges"))
                .withDescription("Apply " + operations.size() + " staged change(s) to a temporal store")
                .withDefaultEventAction(updateEventAction)
                .withComplexLoggedResult(eventAction -> {
                    final ApplyChangesResult result = updatableSqlTemporalStoreProvider.get()
                            .applyChanges(request);
                    // A failure comes back as a result rather than an exception, so the outcome has
                    // to be set from it - including permission denials.
                    return result.isSuccess()
                            ? ComplexLoggedOutcome.success(result, eventAction)
                            : ComplexLoggedOutcome.failure(result, eventAction, result.getErrorMessage());
                })
                .getResultAndLog();
    }

    /// Describes one staged operation as an audit-log object, naming the entry it changed and how.
    ///
    /// @param op A non-null operation.
    /// @return The object, or `null` if the operation names no entry - validation rejects those.
    private static BaseObject operationAsLoggedObject(final ChangeOperation op) {
        if (op.getType() == ChangeOperation.Type.UPSERT && op.getEntry() != null) {
            final TemporalEntry entry = op.getEntry();
            final OtherObject object = UpdatableSqlTemporalStore.entryAsLoggedObject(
                    entry.getMap(), entry.getKey(), entry.getEffectiveTimeMs());
            object.setDescription("Upsert, " + object.getDescription());
            return object;
        } else if (op.getType() == ChangeOperation.Type.DELETE && op.getId() != null) {
            final TemporalEntryId id = op.getId();
            final OtherObject object = UpdatableSqlTemporalStore.entryAsLoggedObject(
                    id.getMap(), id.getKey(), id.getEffectiveTimeMs());
            object.setDescription("Delete, " + object.getDescription());
            return object;
        }
        return null;
    }
}
