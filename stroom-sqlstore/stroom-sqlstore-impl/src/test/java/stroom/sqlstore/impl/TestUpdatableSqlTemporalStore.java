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

import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.TermHandlerFactory;
import stroom.docref.DocRef;
import stroom.event.logging.mock.MockStroomEventLoggingService;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionUtil;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.sqlstore.api.UpdatableTemporalStore;
import stroom.sqlstore.shared.ApplyChangesRequest;
import stroom.sqlstore.shared.ApplyChangesResult;
import stroom.sqlstore.shared.ChangeOperation;
import stroom.sqlstore.shared.SqlTemporalStoreDoc;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.shared.PermissionException;
import stroom.util.shared.TemporalEntry;
import stroom.util.shared.TemporalEntryId;

import event.logging.CreateEventAction;
import event.logging.DeleteEventAction;
import event.logging.Event;
import event.logging.EventAction;
import event.logging.EventLoggerBuilder.TypeIdStep;
import event.logging.HasOutcome;
import event.logging.OtherObject;
import event.logging.UpdateEventAction;
import event.logging.impl.DefaultEventLoggingService;
import org.jooq.Condition;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Unit tests for the StroomQL to JOOQ SQL conversion of Updatable Temporal Store queries.
 * This test class verifies that various ExpressionCriteria trees (containing AND, OR, NOT operations
 * and different field types) are translated correctly into JOOQ Conditions mapping to the underlying
 * `updatable_temporal_store` MySQL table columns.
 */
class TestUpdatableSqlTemporalStore extends StroomUnitTest {

    private static final String MAP = "myMap";

    private static final DocRef STORE_DOC_REF = DocRef.builder()
            .type(SqlTemporalStoreDoc.TYPE)
            .uuid("store-uuid")
            .name(MAP)
            .build();

    private ExpressionMapper expressionMapper;

    @BeforeEach
    void setUp() {
        // Setup the expression mapper in the same way as the DAO implementation
        final TermHandlerFactory termHandlerFactory = new TermHandlerFactory(null, null, null);
        final ExpressionMapperFactory expressionMapperFactory = new ExpressionMapperFactory(termHandlerFactory);
        this.expressionMapper = expressionMapperFactory.create();

        expressionMapper.map(UpdatableTemporalStore.MAP_FIELD,
                stroom.sqlstore.impl.db.jooq.tables.UpdatableTemporalStore.UPDATABLE_TEMPORAL_STORE.MAP_NAME,
                String::valueOf);
        expressionMapper.map(UpdatableTemporalStore.KEY_FIELD,
                stroom.sqlstore.impl.db.jooq.tables.UpdatableTemporalStore.UPDATABLE_TEMPORAL_STORE.MAP_KEY,
                String::valueOf);
        expressionMapper.map(UpdatableTemporalStore.TIME_FIELD,
                stroom.sqlstore.impl.db.jooq.tables.UpdatableTemporalStore.UPDATABLE_TEMPORAL_STORE.EFFECTIVE_TIME,
                Long::valueOf);
        expressionMapper.map(UpdatableTemporalStore.VALUE_FIELD,
                stroom.sqlstore.impl.db.jooq.tables.UpdatableTemporalStore.UPDATABLE_TEMPORAL_STORE.MAP_VALUE,
                String::valueOf);
    }

    /**
     * Test that a simple equality term translates into a direct SQL equality condition.
     */
    @Test
    void testSimpleEqualityTranslation() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(UpdatableTemporalStore.MAP_FIELD.getFldName(), ExpressionTerm.Condition.EQUALS, "my-map")
                .build();

        final Condition condition = expressionMapper.apply(expression);

        assertThat(condition).isNotNull();
        assertThat(condition.toString())
                .contains("map_name\" = 'my-map'");
    }

    /**
     * Test that a composite AND expression translates to a correct SQL AND block.
     */
    @Test
    void testCompositeAndTranslation() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .op(ExpressionOperator.Op.AND)
                .addTerm(UpdatableTemporalStore.MAP_FIELD.getFldName(), ExpressionTerm.Condition.EQUALS, "my-map")
                .addTerm(UpdatableTemporalStore.KEY_FIELD.getFldName(), ExpressionTerm.Condition.EQUALS, "my-key")
                .build();

        final Condition condition = expressionMapper.apply(expression);

        assertThat(condition).isNotNull();
        assertThat(condition.toString())
                .contains("map_name\" = 'my-map'")
                .contains("map_key\" = 'my-key'");
    }

    /**
     * Test that numeric fields (EffectiveTime) are parsed and compared correctly.
     */
    @Test
    void testNumericTimeFieldTranslation() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(UpdatableTemporalStore.TIME_FIELD.getFldName(), ExpressionTerm.Condition.GREATER_THAN, "1000")
                .build();

        final Condition condition = expressionMapper.apply(expression);

        assertThat(condition).isNotNull();
        assertThat(condition.toString())
                .contains("effective_time\" > 1000");
    }

    /**
     * Test nested operators (AND with nested OR) translate into the appropriate SQL structure.
     */
    @Test
    void testNestedOperatorsTranslation() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .op(ExpressionOperator.Op.AND)
                .addTerm(UpdatableTemporalStore.MAP_FIELD.getFldName(), ExpressionTerm.Condition.EQUALS, "my-map")
                .addOperator(ExpressionOperator.builder()
                        .op(ExpressionOperator.Op.OR)
                        .addTerm(UpdatableTemporalStore.KEY_FIELD.getFldName(), ExpressionTerm.Condition.EQUALS, "key1")
                        .addTerm(UpdatableTemporalStore.KEY_FIELD.getFldName(), ExpressionTerm.Condition.EQUALS, "key2")
                        .build())
                .build();

        final Condition condition = expressionMapper.apply(expression);

        assertThat(condition).isNotNull();
        assertThat(condition.toString())
                .contains("map_name\" = 'my-map'")
                .contains("map_key\" = 'key1'")
                .contains("map_key\" = 'key2'");
    }

    /**
     * Test that we can extract the query timestamp and filter out the time field.
     */
    @Test
    void testTimeExtractionAndFiltering() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .op(ExpressionOperator.Op.AND)
                .addTerm(UpdatableTemporalStore.MAP_FIELD.getFldName(), ExpressionTerm.Condition.EQUALS, "my-map")
                .addTerm(UpdatableTemporalStore.TIME_FIELD.getFldName(), ExpressionTerm.Condition.EQUALS, "12345")
                .build();

        // 1. Extract query time
        final List<ExpressionTerm> timeTerms = ExpressionUtil.terms(
                expression,
                List.of(UpdatableTemporalStore.TIME_FIELD.getFldName()));

        Long queryTime = null;
        for (final ExpressionTerm term : timeTerms) {
            if (UpdatableTemporalStore.TIME_FIELD.getFldName().equals(term.getField()) &&
                    (term.getCondition() == ExpressionTerm.Condition.EQUALS ||
                    term.getCondition() == ExpressionTerm.Condition.LESS_THAN_OR_EQUAL_TO)) {
                queryTime = Long.valueOf(term.getValue());
                break;
            }
        }
        assertThat(queryTime).isEqualTo(12345L);

        // 2. Filter out time field
        final ExpressionOperator filtered = ExpressionUtil.copyOperator(
                expression,
                item -> {
                    if (item instanceof ExpressionTerm) {
                        return !UpdatableTemporalStore.TIME_FIELD.getFldName()
                                .equals(((ExpressionTerm) item).getField());
                    }
                    return true;
                });

        assertThat(filtered).isNotNull();
        final Condition condition = expressionMapper.apply(filtered);
        assertThat(condition.toString())
                .contains("map_name\" = 'my-map'")
                .doesNotContain("effective_time");
    }

    // -----------------------------------------------------------------------
    // applyChanges - permissions, validation and error reporting
    // -----------------------------------------------------------------------

    /**
     * Regression test: {@code delete()} requires DELETE, but {@code applyChanges()} only required
     * EDIT although its batch may contain deletes, so an EDIT-only user could delete entries through
     * the batch path.
     */
    @Test
    void testApplyChangesWithADeleteRequiresDeletePermission() {
        final UpdatableTemporalStoreDao dao = Mockito.mock(UpdatableTemporalStoreDao.class);
        final UpdatableSqlTemporalStore store = createStore(dao,
                EnumSet.of(DocumentPermission.VIEW, DocumentPermission.EDIT));

        final ApplyChangesResult result = store.applyChanges(new ApplyChangesRequest(List.of(
                ChangeOperation.upsert(entry("gate")),
                ChangeOperation.delete(id("desk")))));

        assertThat(result.isSuccess()).isFalse();
        Mockito.verify(dao, Mockito.never()).applyChanges(anyString(), anyList());
    }

    /// A user who may edit the store, and so already knows it exists, is told why their delete was
    /// refused rather than that the store is not available.
    @Test
    void testApplyChangesTellsAnEditOnlyUserThatDeletingNeedsDelete() {
        final UpdatableTemporalStoreDao dao = Mockito.mock(UpdatableTemporalStoreDao.class);
        final UpdatableSqlTemporalStore store = createStore(dao,
                EnumSet.of(DocumentPermission.VIEW, DocumentPermission.EDIT));

        final ApplyChangesResult result = store.applyChanges(new ApplyChangesRequest(List.of(
                ChangeOperation.delete(id("desk")))));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage())
                .contains(MAP)
                .contains("requires DELETE permission");
    }

    /// A user who may not even see the store is told nothing more than that it is not available.
    @Test
    void testApplyChangesDoesNotDiscloseAStoreTheUserCannotSee() {
        final UpdatableTemporalStoreDao dao = Mockito.mock(UpdatableTemporalStoreDao.class);
        final UpdatableSqlTemporalStore store = createStore(dao, EnumSet.noneOf(DocumentPermission.class));

        final ApplyChangesResult result = store.applyChanges(new ApplyChangesRequest(List.of(
                ChangeOperation.delete(id("desk")))));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage())
                .contains("No temporal store named")
                .doesNotContain("DELETE");
    }

    @Test
    void testApplyChangesWithADeleteSucceedsWithDeletePermission() {
        final UpdatableTemporalStoreDao dao = Mockito.mock(UpdatableTemporalStoreDao.class);
        final UpdatableSqlTemporalStore store = createStore(dao,
                EnumSet.of(DocumentPermission.VIEW, DocumentPermission.EDIT, DocumentPermission.DELETE));

        final ApplyChangesResult result = store.applyChanges(new ApplyChangesRequest(List.of(
                ChangeOperation.delete(id("desk")))));

        assertThat(result.isSuccess()).isTrue();
        Mockito.verify(dao).applyChanges(eq(STORE_DOC_REF.getUuid()), anyList());
    }

    @Test
    void testApplyChangesWithOnlyUpsertsNeedsOnlyEdit() {
        final UpdatableTemporalStoreDao dao = Mockito.mock(UpdatableTemporalStoreDao.class);
        final UpdatableSqlTemporalStore store = createStore(dao,
                EnumSet.of(DocumentPermission.VIEW, DocumentPermission.EDIT));

        final ApplyChangesResult result = store.applyChanges(new ApplyChangesRequest(List.of(
                ChangeOperation.upsert(entry("gate")))));

        assertThat(result.isSuccess()).isTrue();
    }

    /**
     * Regression test: {@code applyChanges} skipped the key checks {@code create} makes, so during
     * ingest one over-long key failed in the database and took its whole 1,000-entry batch with it,
     * with a message that did not say which entry was at fault.
     */
    @Test
    void testApplyChangesRejectsAnOverLongKeyBeforeAnyDbWork() {
        final UpdatableTemporalStoreDao dao = Mockito.mock(UpdatableTemporalStoreDao.class);
        final UpdatableSqlTemporalStore store = createStore(dao, EnumSet.allOf(DocumentPermission.class));
        final String longKey = "k".repeat(UpdatableTemporalStore.MAX_KEY_LENGTH + 1);

        final ApplyChangesResult result = store.applyChanges(new ApplyChangesRequest(List.of(
                ChangeOperation.upsert(entry("fine")),
                ChangeOperation.upsert(entry(longKey)))));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage())
                .contains("index 1")
                .contains(String.valueOf(UpdatableTemporalStore.MAX_KEY_LENGTH));
        Mockito.verify(dao, Mockito.never()).applyChanges(anyString(), anyList());
    }

    @Test
    void testApplyChangesAcceptsAKeyOfExactlyTheMaximumLength() {
        final UpdatableTemporalStoreDao dao = Mockito.mock(UpdatableTemporalStoreDao.class);
        final UpdatableSqlTemporalStore store = createStore(dao, EnumSet.allOf(DocumentPermission.class));

        final ApplyChangesResult result = store.applyChanges(new ApplyChangesRequest(List.of(
                ChangeOperation.upsert(entry("k".repeat(UpdatableTemporalStore.MAX_KEY_LENGTH))))));

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void testApplyChangesRejectsBlankKeysAndMissingTimes() {
        final UpdatableTemporalStoreDao dao = Mockito.mock(UpdatableTemporalStoreDao.class);
        final UpdatableSqlTemporalStore store = createStore(dao, EnumSet.allOf(DocumentPermission.class));

        final List<ChangeOperation> invalidOps = List.of(
                ChangeOperation.upsert(entry("  ")),
                ChangeOperation.delete(new TemporalEntryId(MAP, "", 1000L)),
                ChangeOperation.upsert(new TemporalEntry(MAP, "gate", null, "{}")),
                ChangeOperation.delete(new TemporalEntryId(MAP, "gate", null)));
        for (final ChangeOperation op : invalidOps) {
            final ApplyChangesResult result = store.applyChanges(new ApplyChangesRequest(List.of(op)));
            assertThat(result.isSuccess())
                    .as("result for %s", op)
                    .isFalse();
            assertThat(result.getErrorMessage())
                    .as("message for %s", op)
                    .contains("index 0");
        }
        Mockito.verify(dao, Mockito.never()).applyChanges(anyString(), anyList());
    }

    /**
     * Regression test: the raw exception message went back to the client, which for a database
     * failure can carry SQL text.
     */
    @Test
    void testApplyChangesDoesNotReturnDatabaseErrorDetail() {
        final UpdatableTemporalStoreDao dao = Mockito.mock(UpdatableTemporalStoreDao.class);
        Mockito.doThrow(new DataAccessException("SQL [insert into `updatable_temporal_store` ...]; boom"))
                .when(dao).applyChanges(anyString(), anyList());
        final UpdatableSqlTemporalStore store = createStore(dao, EnumSet.allOf(DocumentPermission.class));

        final ApplyChangesResult result = store.applyChanges(new ApplyChangesRequest(List.of(
                ChangeOperation.upsert(entry("gate")))));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage())
                .contains(MAP)
                .doesNotContain("SQL")
                .doesNotContain("updatable_temporal_store");
    }

    /** A permission failure is ours to describe, so its message does reach the client. */
    @Test
    void testApplyChangesReportsAPermissionFailure() {
        final UpdatableTemporalStoreDao dao = Mockito.mock(UpdatableTemporalStoreDao.class);
        final UpdatableSqlTemporalStore store = createStore(dao, EnumSet.of(DocumentPermission.VIEW));

        final ApplyChangesResult result = store.applyChanges(new ApplyChangesRequest(List.of(
                ChangeOperation.upsert(entry("gate")))));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains(MAP);
    }

    @Test
    void testCreateWithoutEditIsDenied() {
        final UpdatableTemporalStoreDao dao = Mockito.mock(UpdatableTemporalStoreDao.class);
        final UpdatableSqlTemporalStore store = createStore(dao, EnumSet.of(DocumentPermission.VIEW));

        assertThatThrownBy(() -> store.create(entry("gate")))
                .isInstanceOf(PermissionException.class);
        Mockito.verify(dao, Mockito.never()).create(anyString(), any());
    }

    /// Regression test: clearing a store needed only EDIT, so a user who could not delete a single
    /// entry could still delete all of them.
    @Test
    void testClearWithoutDeleteIsDenied() {
        final UpdatableTemporalStoreDao dao = Mockito.mock(UpdatableTemporalStoreDao.class);
        final UpdatableSqlTemporalStore store = createStore(dao,
                EnumSet.of(DocumentPermission.VIEW, DocumentPermission.EDIT));

        assertThatThrownBy(() -> store.clear(STORE_DOC_REF))
                .isInstanceOf(PermissionException.class)
                .hasMessageContaining(DocumentPermission.DELETE.toString());
        Mockito.verify(dao, Mockito.never()).clear(anyString());
    }

    @Test
    void testClearWithDeleteIsAllowed() {
        final UpdatableTemporalStoreDao dao = Mockito.mock(UpdatableTemporalStoreDao.class);
        final UpdatableSqlTemporalStore store = createStore(dao,
                EnumSet.of(DocumentPermission.VIEW, DocumentPermission.EDIT, DocumentPermission.DELETE));

        store.clear(STORE_DOC_REF);

        Mockito.verify(dao).clear(STORE_DOC_REF.getUuid());
    }

    /// Regression test: the clear audit named the store by whatever name the caller's DocRef carried,
    /// and by nothing at all if the caller could not see the store. It is now named by the store's
    /// real name, which is still not disclosed to the caller.
    @Test
    void testClearIsAuditedUnderTheStoresRealName() {
        final UpdatableTemporalStoreDao dao = Mockito.mock(UpdatableTemporalStoreDao.class);
        final List<Event> loggedEvents = new ArrayList<>();
        final UpdatableSqlTemporalStore store = createStore(dao, EnumSet.noneOf(DocumentPermission.class),
                loggedEvents);
        final DocRef spoofed = STORE_DOC_REF.copy().name("spoofed-name").build();

        assertThatThrownBy(() -> store.clear(spoofed))
                .isInstanceOf(PermissionException.class)
                .hasMessageContaining(STORE_DOC_REF.getUuid())
                .hasMessageNotContaining("spoofed-name")
                .hasMessageNotContaining(MAP);

        assertThat(loggedEvents).hasSize(1);
        final EventAction action = loggedEvents.getFirst().getEventDetail().getEventAction();
        assertThat(action).isInstanceOfSatisfying(DeleteEventAction.class, deleteAction -> {
            assertThat(deleteAction.getOutcome().isSuccess()).isFalse();
            assertThat(deleteAction.getObjects())
                    .singleElement()
                    .isInstanceOfSatisfying(OtherObject.class, object -> {
                        assertThat(object.getId()).isEqualTo(STORE_DOC_REF.getUuid());
                        assertThat(object.getName()).isEqualTo(MAP);
                    });
        });
    }

    /// Regression test: a null argument was rejected before the logged work started, so the request
    /// was not audited at all.
    @Test
    void testARequestWithANullArgumentIsStillAudited() {
        final UpdatableTemporalStoreDao dao = Mockito.mock(UpdatableTemporalStoreDao.class);
        final List<Event> loggedEvents = new ArrayList<>();
        final UpdatableSqlTemporalStore store = createStore(dao, EnumSet.allOf(DocumentPermission.class),
                loggedEvents);

        assertThatThrownBy(() -> store.create(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.update(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.delete(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.clear(null)).isInstanceOf(IllegalArgumentException.class);

        assertThat(loggedEvents)
                .extracting(event -> event.getEventDetail().getEventAction())
                .hasExactlyElementsOfTypes(CreateEventAction.class,
                        UpdateEventAction.class,
                        DeleteEventAction.class,
                        DeleteEventAction.class)
                .allSatisfy(action -> assertThat(((HasOutcome) action).getOutcome().isSuccess())
                        .as("the attempt is recorded as a failure")
                        .isFalse());
        Mockito.verifyNoInteractions(dao);
    }

    /// Regression test: update delegated to create, so every update was audited as a create.
    @Test
    void testUpdateIsAuditedAsAnUpdate() {
        final UpdatableTemporalStoreDao dao = Mockito.mock(UpdatableTemporalStoreDao.class);
        final List<Event> loggedEvents = new ArrayList<>();
        final UpdatableSqlTemporalStore store = createStore(dao, EnumSet.allOf(DocumentPermission.class),
                loggedEvents);

        store.update(entry("gate"));

        Mockito.verify(dao).create(STORE_DOC_REF.getUuid(), entry("gate"));
        assertThat(loggedEvents)
                .singleElement()
                .extracting(event -> event.getEventDetail().getEventAction())
                .isInstanceOf(UpdateEventAction.class);
    }

    /// Create applies the same key rules as every other write path.
    @Test
    void testCreateRejectsAnOverLongKey() {
        final UpdatableTemporalStoreDao dao = Mockito.mock(UpdatableTemporalStoreDao.class);
        final UpdatableSqlTemporalStore store = createStore(dao, EnumSet.allOf(DocumentPermission.class));

        assertThatThrownBy(() -> store.create(entry("k".repeat(UpdatableTemporalStore.MAX_KEY_LENGTH + 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(UpdatableTemporalStore.MAX_KEY_LENGTH));
        Mockito.verify(dao, Mockito.never()).create(anyString(), any());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * A store over one document, {@link #STORE_DOC_REF}, on which the caller holds exactly the
     * given permissions.
     */
    private static UpdatableSqlTemporalStore createStore(final UpdatableTemporalStoreDao dao,
                                                         final Set<DocumentPermission> granted) {
        return createStore(dao, granted, new ArrayList<>());
    }

    /// As [#createStore(UpdatableTemporalStoreDao, Set)], also capturing every audit event logged.
    private static UpdatableSqlTemporalStore createStore(final UpdatableTemporalStoreDao dao,
                                                         final Set<DocumentPermission> granted,
                                                         final List<Event> loggedEvents) {
        final SqlTemporalStoreDocStore docStore = Mockito.mock(SqlTemporalStoreDocStore.class);
        Mockito.lenient().when(docStore.list()).thenReturn(List.of(STORE_DOC_REF));
        final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.lenient()
                .when(securityContext.hasDocumentPermission(any(DocRef.class), any(DocumentPermission.class)))
                .thenAnswer(invocation -> granted.contains(invocation.<DocumentPermission>getArgument(1)));
        Mockito.lenient()
                .when(securityContext.asProcessingUserResult(any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
        // The library's real builder, which logs, in place of the mock's, which does not.
        final DefaultEventLoggingService capturingService = new DefaultEventLoggingService() {
            @Override
            public void log(final Event event) {
                loggedEvents.add(event);
            }
        };
        final MockStroomEventLoggingService eventLoggingService = new MockStroomEventLoggingService() {
            @Override
            public TypeIdStep loggedWorkBuilder() {
                return capturingService.loggedWorkBuilder();
            }
        };
        return new UpdatableSqlTemporalStore(
                dao,
                docStore,
                null,
                null,
                null,
                null,
                null,
                null,
                securityContext,
                () -> eventLoggingService);
    }

    private static TemporalEntry entry(final String key) {
        return new TemporalEntry(MAP, key, 1000L, "{}");
    }

    private static TemporalEntryId id(final String key) {
        return new TemporalEntryId(MAP, key, 1000L);
    }
}
