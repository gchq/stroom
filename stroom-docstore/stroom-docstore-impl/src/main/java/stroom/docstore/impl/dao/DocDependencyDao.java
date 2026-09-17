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

package stroom.docstore.impl.dao;

import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
import stroom.docstore.impl.db.DocStoreDbConnProvider;
import stroom.docstore.impl.db.jooq.tables.Doc;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.query.common.v2.FieldProviderImpl;
import stroom.query.common.v2.SimpleStringExpressionParser;
import stroom.query.common.v2.SimpleStringExpressionParser.FieldProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static stroom.docstore.impl.db.jooq.tables.Doc.DOC;
import static stroom.docstore.impl.db.jooq.tables.DocDependency.DOC_DEPENDENCY;

@Singleton
public class DocDependencyDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocDependencyDao.class);

    // Aliased doc tables for the LEFT JOINs
    private static final Doc FROM_DOC = DOC.as("d1");
    private static final Doc TO_DOC = DOC.as("d2");

    private static final String STATUS_OK = "OK";
    private static final String STATUS_MISSING = "Missing";

    // Computed fields for the query.
    //
    // Each exists in two forms. The unaliased *_EXPR form is for the WHERE clause: jOOQ renders an
    // .as(...) aliased field as a bare alias reference outside the SELECT clause, and MySQL does
    // not allow select aliases in WHERE. The aliased form is for SELECT and ORDER BY.
    private static final Field<String> FROM_NAME_EXPR =
            DSL.coalesce(FROM_DOC.NAME, DOC_DEPENDENCY.FROM_NAME);
    private static final Field<String> TO_NAME_EXPR =
            DSL.coalesce(TO_DOC.NAME, DOC_DEPENDENCY.TO_NAME);
    private static final Field<String> FROM_NAME_RESOLVED = FROM_NAME_EXPR.as("from_name_resolved");
    private static final Field<String> TO_NAME_RESOLVED = TO_NAME_EXPR.as("to_name_resolved");

    /**
     * Sort field mapping. Sorting is requested by column display name, unlike filtering which uses
     * the filter qualifier, so these keys are the FIELD_* display names.
     * <p>
     * Built per call rather than held as a static because the status expression depends on the
     * pseudo-ref set. Sorting must use the <b>same</b> expression instance the SELECT does, or a
     * pseudo-ref row would be ordered as missing while being displayed as OK.
     */
    private static Map<String, Field<?>> sortFieldMap(final Field<String> statusField) {
        return Map.of(
                DependencyCriteria.FIELD_FROM_TYPE, DOC_DEPENDENCY.FROM_TYPE,
                DependencyCriteria.FIELD_FROM_NAME, FROM_NAME_RESOLVED,
                DependencyCriteria.FIELD_FROM_UUID, DOC_DEPENDENCY.FROM_UUID,
                DependencyCriteria.FIELD_TO_TYPE, DOC_DEPENDENCY.TO_TYPE,
                DependencyCriteria.FIELD_TO_NAME, TO_NAME_RESOLVED,
                DependencyCriteria.FIELD_TO_UUID, DOC_DEPENDENCY.TO_UUID,
                DependencyCriteria.FIELD_STATUS, statusField);
    }

    private static final FieldProvider FIELD_PROVIDER =
            new FieldProviderImpl(DependencyCriteria.FIELD_DEFINITIONS);

    /**
     * A dependency is OK if its target still exists as a non deleted doc, or if it is a known
     * pseudo-ref (e.g. a Searchable or Annotation data source) which by design lives outside the
     * doc table. Exposed as the "OK"/"Missing" text the user filters and sorts on so the status
     * column, the {@code status:} filter and the status sort all derive from one definition.
     */
    private static Field<String> statusExpr(final Set<String> pseudoRefUuids) {
        return DSL
                .when(okCondition(pseudoRefUuids), STATUS_OK)
                .otherwise(STATUS_MISSING);
    }

    private static Condition okCondition(final Set<String> pseudoRefUuids) {
        final Condition targetExists = TO_DOC.UUID.isNotNull();
        return NullSafe.hasItems(pseudoRefUuids)
                ? targetExists.or(DOC_DEPENDENCY.TO_UUID.in(pseudoRefUuids))
                : targetExists;
    }

    // Explorer-document types that "own" a non-explorer entity (e.g. a ProcessorFilter is owned by its
    // Pipeline). A broken dependency whose source is a non-explorer entity (its from_uuid is absent
    // from the doc table) is rolled up to the owning document — found via one of the source's own
    // dependency edges of these types — so it can surface on a real explorer tree node rather than
    // being invisible. Extend this set if other non-explorer entities with a different owner type are
    // added to the dependency store.
    private static final Set<String> OWNER_DOC_TYPES = Set.of("Pipeline");

    private final DocStoreDbConnProvider connProvider;
    private final ExpressionMapperFactory expressionMapperFactory;

    @Inject
    DocDependencyDao(final DocStoreDbConnProvider connProvider,
                     final ExpressionMapperFactory expressionMapperFactory) {
        this.connProvider = connProvider;
        this.expressionMapperFactory = expressionMapperFactory;
    }

    /**
     * The status expression depends on the pseudo-ref set, which varies per call, so the mapper is
     * built per call rather than held as state. Cheap - it is just a map of term handlers.
     */
    private ExpressionMapper createExpressionMapper(final Set<String> pseudoRefUuids) {
        return expressionMapperFactory.create()
                .map(DependencyCriteria.QF_FROM_TYPE, DOC_DEPENDENCY.FROM_TYPE, value -> value)
                .map(DependencyCriteria.QF_FROM_NAME, FROM_NAME_EXPR, value -> value)
                .map(DependencyCriteria.QF_FROM_UUID, DOC_DEPENDENCY.FROM_UUID, value -> value)
                .map(DependencyCriteria.QF_TO_TYPE, DOC_DEPENDENCY.TO_TYPE, value -> value)
                .map(DependencyCriteria.QF_TO_NAME, TO_NAME_EXPR, value -> value)
                .map(DependencyCriteria.QF_TO_UUID, DOC_DEPENDENCY.TO_UUID, value -> value)
                .map(DependencyCriteria.QF_STATUS, statusExpr(pseudoRefUuids), value -> value);
    }

    /**
     * Replace all outgoing dependency edges for the given document.
     * Runs in a transaction: deletes existing edges, then inserts new ones.
     */
    public void setDependencies(final DocRef from, final Set<DocRef> deps) {
        JooqUtil.transaction(connProvider, context -> {
            // Delete existing edges for this doc
            context
                    .deleteFrom(DOC_DEPENDENCY)
                    .where(DOC_DEPENDENCY.FROM_UUID.eq(from.getUuid()))
                    .execute();

            // Insert new edges
            if (NullSafe.hasItems(deps)) {
                final String fromType = NullSafe.nonBlankStringElse(from.getType(), "");
                final String fromName = NullSafe.nonBlankStringElse(from.getName(), "");
                for (final DocRef to : deps) {
                    final String toType = NullSafe.nonBlankStringElse(to.getType(), "");
                    final String toName = NullSafe.nonBlankStringElse(to.getName(), "");
                    // Upsert on the (from_uuid, to_uuid) unique key. Use onDuplicateKeyUpdate rather than
                    // onDuplicateKeyIgnore so any genuine constraint failure surfaces rather than being
                    // silently swallowed; on a duplicate edge we refresh the type/name columns.
                    context
                            .insertInto(DOC_DEPENDENCY)
                            .set(DOC_DEPENDENCY.FROM_TYPE, fromType)
                            .set(DOC_DEPENDENCY.FROM_UUID, from.getUuid())
                            .set(DOC_DEPENDENCY.FROM_NAME, fromName)
                            .set(DOC_DEPENDENCY.TO_TYPE, toType)
                            .set(DOC_DEPENDENCY.TO_UUID, to.getUuid())
                            .set(DOC_DEPENDENCY.TO_NAME, toName)
                            .onDuplicateKeyUpdate()
                            .set(DOC_DEPENDENCY.FROM_TYPE, fromType)
                            .set(DOC_DEPENDENCY.FROM_NAME, fromName)
                            .set(DOC_DEPENDENCY.TO_TYPE, toType)
                            .set(DOC_DEPENDENCY.TO_NAME, toName)
                            .execute();
                }
            }
        });
    }

    /**
     * Propagate a name change to all edges that reference the given UUID.
     */
    public void updateName(final String uuid, final String name) {
        final String safeName = NullSafe.nonBlankStringElse(name, "");
        JooqUtil.context(connProvider, context -> {
            context
                    .update(DOC_DEPENDENCY)
                    .set(DOC_DEPENDENCY.TO_NAME, safeName)
                    .where(DOC_DEPENDENCY.TO_UUID.eq(uuid))
                    .execute();
            context
                    .update(DOC_DEPENDENCY)
                    .set(DOC_DEPENDENCY.FROM_NAME, safeName)
                    .where(DOC_DEPENDENCY.FROM_UUID.eq(uuid))
                    .execute();
        });
    }

    /**
     * Get all documents that the given document depends on.
     */
    public Set<DocRef> getDependenciesOf(final String fromUuid) {
        return JooqUtil.contextResult(connProvider, context ->
                new HashSet<>(context
                        .select(DOC_DEPENDENCY.TO_TYPE,
                                DOC_DEPENDENCY.TO_UUID,
                                DOC_DEPENDENCY.TO_NAME)
                        .from(DOC_DEPENDENCY)
                        .where(DOC_DEPENDENCY.FROM_UUID.eq(fromUuid))
                        .fetch(r -> new DocRef(
                                r.get(DOC_DEPENDENCY.TO_TYPE),
                                r.get(DOC_DEPENDENCY.TO_UUID),
                                r.get(DOC_DEPENDENCY.TO_NAME)))));
    }

    /**
     * Get all documents that depend on the given document (reverse lookup for safe-delete).
     * <p>
     * Names are resolved live from the doc table (falling back to the stored snapshot). A dependant
     * that is a non-explorer entity (its {@code from_uuid} is absent from the doc table, e.g. a
     * ProcessorFilter) is rolled up to its owning explorer document (e.g. the filter's Pipeline, found
     * via one of the source's own {@link #OWNER_DOC_TYPES owner-typed} dependency edges) so the
     * dependant surfaces as a real explorer tree node. A non-explorer source with no such owner edge is
     * returned as-is.
     */
    public Set<DocRef> getDependantsOf(final String toUuid) {
        return JooqUtil.contextResult(connProvider, ctx -> {
            // FROM_DOC.UUID tells us whether the dependant is itself an explorer document (non-null) or
            // a non-explorer entity that needs rolling up (null).
            final Result<Record> records = ctx
                    .select(List.of(
                            DOC_DEPENDENCY.FROM_TYPE,
                            DOC_DEPENDENCY.FROM_UUID,
                            FROM_NAME_RESOLVED,
                            FROM_DOC.UUID))
                    .from(DOC_DEPENDENCY)
                    .leftJoin(FROM_DOC).on(FROM_DOC.UUID.eq(DOC_DEPENDENCY.FROM_UUID)
                            .and(FROM_DOC.DELETED.isNull()))
                    .where(DOC_DEPENDENCY.TO_UUID.eq(toUuid))
                    .fetch();

            final Set<DocRef> dependants = new HashSet<>();
            // Non-explorer sources, keyed by uuid, deferred until we've resolved their owners.
            final Map<String, DocRef> nonExplorerSourceByUuid = new HashMap<>();
            for (final Record r : records) {
                final DocRef fromRef = new DocRef(
                        r.get(DOC_DEPENDENCY.FROM_TYPE),
                        r.get(DOC_DEPENDENCY.FROM_UUID),
                        r.get(FROM_NAME_RESOLVED));
                if (r.get(FROM_DOC.UUID) == null) {
                    nonExplorerSourceByUuid.put(fromRef.getUuid(), fromRef);
                } else {
                    dependants.add(fromRef);
                }
            }

            // Roll up any non-explorer source to its owning explorer document, falling back to the
            // source itself where no owner edge exists.
            if (!nonExplorerSourceByUuid.isEmpty()) {
                final Map<String, DocRef> ownerBySourceUuid =
                        getOwnerDocRefs(ctx, nonExplorerSourceByUuid.keySet());
                nonExplorerSourceByUuid.forEach((uuid, sourceRef) -> {
                    final DocRef owner = ownerBySourceUuid.get(uuid);
                    dependants.add(owner != null ? owner : sourceRef);
                });
            }

            return dependants;
        });
    }

    /**
     * Fetch dependencies with pagination, filtering, and sorting.
     * Uses LEFT JOINs to the doc table to resolve live names and determine
     * whether the target document still exists.
     * <p>
     * The criteria's quick filter text is parsed and applied as SQL conditions, so it narrows the
     * result set in the database. Text that cannot be parsed, or that uses a condition outside a
     * field's {@code ConditionSet}, matches nothing rather than failing the request - the quick
     * filter queries on a debounce as the user types, so partial input is expected.
     * <p>
     * The {@code filter} predicate is a separate concern, applied in Java after rows are fetched
     * from the database. This allows callers to apply checks that cannot be
     * expressed in SQL (e.g. document permission checks). Pagination is
     * applied <b>after</b> filtering via {@link ResultPage#collector}, so
     * page sizes and totals are correct even when rows are filtered out.
     *
     * @param criteria       the search/sort/page criteria
     * @param pseudoRefUuids UUIDs of known pseudo-refs that live outside the doc table and so
     *                       must be reported as OK rather than missing
     * @param filter         a predicate applied to each row; only rows passing
     *                       the predicate are counted and included in the page
     */
    public ResultPage<Dependency> fetchDependencies(final DependencyCriteria criteria,
                                                    final Set<String> pseudoRefUuids,
                                                    final Predicate<Dependency> filter) {
        final PageRequest pageRequest = NullSafe.get(criteria, DependencyCriteria::getPageRequest);
        final Field<String> statusField = statusExpr(pseudoRefUuids).as("status");

        // Turn the quick filter text into SQL conditions. Both the parse and the mapping can throw
        // on input that is partially typed or uses a condition this field does not support, which
        // is expected while the user types, so match nothing rather than failing the request.
        final List<Condition> conditions = new ArrayList<>();
        final String filterInput = NullSafe.get(criteria, DependencyCriteria::getPartialName);
        try {
            SimpleStringExpressionParser
                    .create(FIELD_PROVIDER, filterInput)
                    .ifPresent(expression ->
                            conditions.add(createExpressionMapper(pseudoRefUuids).apply(expression)));
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            return ResultPage.empty();
        }

        return JooqUtil.contextResult(connProvider, context -> {
            final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(sortFieldMap(statusField), criteria);

            // Fetch ALL matching rows (no SQL LIMIT — pagination is done in Java
            // after the filter predicate is applied, so totals are correct)
            return context
                    .select(DOC_DEPENDENCY.FROM_TYPE,
                            DOC_DEPENDENCY.FROM_UUID,
                            FROM_NAME_RESOLVED,
                            DOC_DEPENDENCY.TO_TYPE,
                            DOC_DEPENDENCY.TO_UUID,
                            TO_NAME_RESOLVED,
                            statusField)
                    .from(DOC_DEPENDENCY)
                    .leftJoin(FROM_DOC).on(FROM_DOC.UUID.eq(DOC_DEPENDENCY.FROM_UUID)
                            .and(FROM_DOC.DELETED.isNull()))
                    .leftJoin(TO_DOC).on(TO_DOC.UUID.eq(DOC_DEPENDENCY.TO_UUID)
                            .and(TO_DOC.DELETED.isNull()))
                    .where(conditions)
                    .orderBy(orderFields)
                    .fetchStream()
                    .map(r -> {
                        final DocRef fromRef = new DocRef(
                                r.get(DOC_DEPENDENCY.FROM_TYPE),
                                r.get(DOC_DEPENDENCY.FROM_UUID),
                                r.get(FROM_NAME_RESOLVED));
                        final DocRef toRef = new DocRef(
                                r.get(DOC_DEPENDENCY.TO_TYPE),
                                r.get(DOC_DEPENDENCY.TO_UUID),
                                r.get(TO_NAME_RESOLVED));
                        final boolean ok = STATUS_OK.equals(r.get(statusField));
                        return new Dependency(fromRef, toRef, ok);
                    })
                    .filter(filter)
                    .collect(ResultPage.collector(pageRequest));
        });
    }

    /**
     * Delete all outgoing dependency edges for the given document.
     */
    public void deleteAllForDoc(final String fromUuid) {
        JooqUtil.context(connProvider, context -> context
                .deleteFrom(DOC_DEPENDENCY)
                .where(DOC_DEPENDENCY.FROM_UUID.eq(fromUuid))
                .execute());
    }

    /**
     * Find broken dependencies: edges where the target UUID does not exist in the doc table
     * (as a non-deleted document). Returns a map of source DocRef to the set of missing target DocRefs.
     * <p>
     * A broken edge whose source is a non-explorer entity (its {@code from_uuid} is absent from the
     * doc table, e.g. a ProcessorFilter) is rolled up to the source's owning explorer document (found
     * via one of the source's own {@link #OWNER_DOC_TYPES owner-typed} dependency edges) so that it can
     * be surfaced on a real explorer tree node.
     *
     * @param pseudoRefUuids UUIDs of known pseudo-refs that should NOT be considered broken
     */
    public Map<DocRef, Set<DocRef>> getBrokenDependencies(final Set<String> pseudoRefUuids) {
        return JooqUtil.contextResult(connProvider, ctx -> {
            // Find all edges where the target is not in the doc table (non-deleted)
            Condition brokenCondition = TO_DOC.UUID.isNull();

            // Exclude known pseudo-refs
            if (NullSafe.hasItems(pseudoRefUuids)) {
                brokenCondition = brokenCondition
                        .and(DOC_DEPENDENCY.TO_UUID.notIn(pseudoRefUuids));
            }

            // FROM_DOC.UUID tells us whether the source is itself an explorer document (non-null) or a
            // non-explorer entity that needs rolling up (null).
            final Result<Record> records = ctx
                    .select(List.of(
                            DOC_DEPENDENCY.FROM_TYPE,
                            DOC_DEPENDENCY.FROM_UUID,
                            FROM_NAME_RESOLVED,
                            FROM_DOC.UUID,
                            DOC_DEPENDENCY.TO_TYPE,
                            DOC_DEPENDENCY.TO_UUID,
                            DOC_DEPENDENCY.TO_NAME))
                    .from(DOC_DEPENDENCY)
                    .leftJoin(FROM_DOC).on(FROM_DOC.UUID.eq(DOC_DEPENDENCY.FROM_UUID)
                            .and(FROM_DOC.DELETED.isNull()))
                    .leftJoin(TO_DOC).on(TO_DOC.UUID.eq(DOC_DEPENDENCY.TO_UUID)
                            .and(TO_DOC.DELETED.isNull()))
                    .where(brokenCondition)
                    .fetch();

            final Map<DocRef, Set<DocRef>> result = new HashMap<>();
            final Set<String> nonExplorerSourceUuids = new HashSet<>();
            for (final Record r : records) {
                final DocRef fromRef = new DocRef(
                        r.get(DOC_DEPENDENCY.FROM_TYPE),
                        r.get(DOC_DEPENDENCY.FROM_UUID),
                        r.get(FROM_NAME_RESOLVED));
                final DocRef toRef = new DocRef(
                        r.get(DOC_DEPENDENCY.TO_TYPE),
                        r.get(DOC_DEPENDENCY.TO_UUID),
                        r.get(DOC_DEPENDENCY.TO_NAME));
                result.computeIfAbsent(fromRef, k -> new HashSet<>()).add(toRef);
                if (r.get(FROM_DOC.UUID) == null) {
                    nonExplorerSourceUuids.add(fromRef.getUuid());
                }
            }

            // Roll up any non-explorer source to its owning explorer document.
            if (!nonExplorerSourceUuids.isEmpty()) {
                final Map<String, DocRef> ownerBySourceUuid = getOwnerDocRefs(ctx, nonExplorerSourceUuids);
                if (!ownerBySourceUuid.isEmpty()) {
                    final Map<DocRef, Set<DocRef>> rolledUp = new HashMap<>();
                    result.forEach((source, missing) -> {
                        final DocRef owner = ownerBySourceUuid.get(source.getUuid());
                        rolledUp.computeIfAbsent(owner != null ? owner : source, k -> new HashSet<>())
                                .addAll(missing);
                    });
                    return rolledUp;
                }
            }
            return result;
        });
    }

    /**
     * For each supplied non-explorer source UUID, find its owning explorer document: the target of one
     * of the source's dependency edges whose type is an {@link #OWNER_DOC_TYPES owner type} and which
     * still exists in the doc table. Returns a map of source UUID to owner DocRef (with the owner's
     * live name). Sources with no such edge are simply absent from the map.
     */
    private Map<String, DocRef> getOwnerDocRefs(final DSLContext ctx, final Set<String> sourceUuids) {
        return ctx
                .select(DOC_DEPENDENCY.FROM_UUID,
                        DOC_DEPENDENCY.TO_TYPE,
                        DOC_DEPENDENCY.TO_UUID,
                        TO_DOC.NAME)
                .from(DOC_DEPENDENCY)
                .join(TO_DOC).on(TO_DOC.UUID.eq(DOC_DEPENDENCY.TO_UUID)
                        .and(TO_DOC.DELETED.isNull()))
                .where(DOC_DEPENDENCY.FROM_UUID.in(sourceUuids)
                        .and(DOC_DEPENDENCY.TO_TYPE.in(OWNER_DOC_TYPES)))
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        r -> r.get(DOC_DEPENDENCY.FROM_UUID),
                        r -> new DocRef(
                                r.get(DOC_DEPENDENCY.TO_TYPE),
                                r.get(DOC_DEPENDENCY.TO_UUID),
                                r.get(TO_DOC.NAME)),
                        (a, b) -> a));
    }
}
