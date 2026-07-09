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

package stroom.docstore.impl.dao;

import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
import stroom.docstore.impl.db.DocStoreDbConnProvider;
import stroom.docstore.impl.db.jooq.tables.Doc;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.util.shared.CriteriaFieldSort;
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

    // Aliased doc tables for the LEFT JOINs
    private static final Doc FROM_DOC = DOC.as("d1");
    private static final Doc TO_DOC = DOC.as("d2");

    // Computed fields for the query
    private static final Field<String> FROM_NAME_RESOLVED =
            DSL.coalesce(FROM_DOC.NAME, DOC_DEPENDENCY.FROM_NAME).as("from_name_resolved");
    private static final Field<String> TO_NAME_RESOLVED =
            DSL.coalesce(TO_DOC.NAME, DOC_DEPENDENCY.TO_NAME).as("to_name_resolved");
    private static final Field<Boolean> OK_FIELD =
            DSL.field(TO_DOC.UUID.isNotNull()).as("ok");

    // Sort field mapping
    private static final Map<String, Field<?>> SORT_FIELD_MAP = Map.of(
            DependencyCriteria.FIELD_FROM_TYPE, DOC_DEPENDENCY.FROM_TYPE,
            DependencyCriteria.FIELD_FROM_NAME, FROM_NAME_RESOLVED,
            DependencyCriteria.FIELD_FROM_UUID, DOC_DEPENDENCY.FROM_UUID,
            DependencyCriteria.FIELD_TO_TYPE, DOC_DEPENDENCY.TO_TYPE,
            DependencyCriteria.FIELD_TO_NAME, TO_NAME_RESOLVED,
            DependencyCriteria.FIELD_TO_UUID, DOC_DEPENDENCY.TO_UUID,
            DependencyCriteria.FIELD_STATUS, OK_FIELD
    );

    // Explorer-document types that "own" a non-explorer entity (e.g. a ProcessorFilter is owned by its
    // Pipeline). A broken dependency whose source is a non-explorer entity (its from_uuid is absent
    // from the doc table) is rolled up to the owning document — found via one of the source's own
    // dependency edges of these types — so it can surface on a real explorer tree node rather than
    // being invisible. Extend this set if other non-explorer entities with a different owner type are
    // added to the dependency store.
    private static final Set<String> OWNER_DOC_TYPES = Set.of("Pipeline");

    private final DocStoreDbConnProvider connProvider;

    @Inject
    DocDependencyDao(final DocStoreDbConnProvider connProvider) {
        this.connProvider = connProvider;
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
     */
    public Set<DocRef> getDependantsOf(final String toUuid) {
        return JooqUtil.contextResult(connProvider, context ->
                new HashSet<>(context
                        .select(DOC_DEPENDENCY.FROM_TYPE,
                                DOC_DEPENDENCY.FROM_UUID,
                                DOC_DEPENDENCY.FROM_NAME)
                        .from(DOC_DEPENDENCY)
                        .where(DOC_DEPENDENCY.TO_UUID.eq(toUuid))
                        .fetch(r -> new DocRef(
                                r.get(DOC_DEPENDENCY.FROM_TYPE),
                                r.get(DOC_DEPENDENCY.FROM_UUID),
                                r.get(DOC_DEPENDENCY.FROM_NAME)))));
    }

    /**
     * Fetch dependencies with pagination, filtering, and sorting.
     * Uses LEFT JOINs to the doc table to resolve live names and determine
     * whether the target document still exists.
     * <p>
     * The {@code filter} predicate is applied in Java after rows are fetched
     * from the database. This allows callers to apply checks that cannot be
     * expressed in SQL (e.g. document permission checks). Pagination is
     * applied <b>after</b> filtering via {@link ResultPage#collector}, so
     * page sizes and totals are correct even when rows are filtered out.
     *
     * @param criteria the search/sort/page criteria
     * @param filter   a predicate applied to each row; only rows passing
     *                 the predicate are counted and included in the page
     */
    public ResultPage<Dependency> fetchDependencies(final DependencyCriteria criteria,
                                                    final Predicate<Dependency> filter) {
        final PageRequest pageRequest = NullSafe.get(criteria, DependencyCriteria::getPageRequest);

        return JooqUtil.contextResult(connProvider, context -> {
            // Build filter condition
            Condition condition = DSL.trueCondition();
            final String partialName = criteria != null
                    ? criteria.getPartialName()
                    : null;
            if (NullSafe.isNonBlankString(partialName)) {
                final String likePattern = "%" + partialName + "%";
                condition = condition.and(
                        DSL.coalesce(FROM_DOC.NAME, DOC_DEPENDENCY.FROM_NAME).likeIgnoreCase(likePattern)
                                .or(DSL.coalesce(TO_DOC.NAME, DOC_DEPENDENCY.TO_NAME).likeIgnoreCase(likePattern))
                );
            }

            // Build sort order
            final List<OrderField<?>> orderFields = new ArrayList<>();
            if (criteria != null && NullSafe.hasItems(criteria.getSortList())) {
                for (final CriteriaFieldSort sort : criteria.getSortList()) {
                    final Field<?> field = SORT_FIELD_MAP.get(sort.getId());
                    if (field != null) {
                        if (sort.isDesc()) {
                            orderFields.add(field.desc());
                        } else {
                            orderFields.add(field.asc());
                        }
                    }
                }
            }

            // Fetch ALL matching rows (no SQL LIMIT — pagination is done in Java
            // after the filter predicate is applied, so totals are correct)
            return context
                    .select(DOC_DEPENDENCY.FROM_TYPE,
                            DOC_DEPENDENCY.FROM_UUID,
                            FROM_NAME_RESOLVED,
                            DOC_DEPENDENCY.TO_TYPE,
                            DOC_DEPENDENCY.TO_UUID,
                            TO_NAME_RESOLVED,
                            OK_FIELD)
                    .from(DOC_DEPENDENCY)
                    .leftJoin(FROM_DOC).on(FROM_DOC.UUID.eq(DOC_DEPENDENCY.FROM_UUID)
                            .and(FROM_DOC.DELETED.isNull()))
                    .leftJoin(TO_DOC).on(TO_DOC.UUID.eq(DOC_DEPENDENCY.TO_UUID)
                            .and(TO_DOC.DELETED.isNull()))
                    .where(condition)
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
                        final boolean ok = Boolean.TRUE.equals(r.get(OK_FIELD));
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
