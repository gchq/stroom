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
import stroom.docstore.impl.db.DocStoreDBPersistenceDbModule;
import stroom.docstore.impl.db.DocStoreDbConnProvider;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.shared.ResultPage;

import com.google.inject.Guice;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.docstore.impl.db.jooq.tables.DocDependency.DOC_DEPENDENCY;

class TestDocDependencyDao {

    @Inject
    private DocDependencyDao dao;
    @Inject
    private DocStoreDbConnProvider connProvider;

    @BeforeEach
    void setUp() throws SQLException {
        Guice.createInjector(
                new DocStoreDBPersistenceDbModule(),
                new DbTestModule()
        ).injectMembers(this);

        // Clear the doc_dependency table
        try (final Connection connection = connProvider.getConnection()) {
            stroom.test.common.util.db.DbTestUtil.clearTables(
                    connection, List.of(DOC_DEPENDENCY.getName()));
        }
    }

    // --- Helper ---

    private DocRef docRef(final String type, final String name) {
        return new DocRef(type, UUID.randomUUID().toString(), name);
    }

    private void createDocRow(final DocRef docRef) {
        // Insert a minimal row into the doc table so LEFT JOINs can resolve names
        JooqUtil.context(connProvider, ctx -> ctx
                .insertInto(stroom.docstore.impl.db.jooq.tables.Doc.DOC)
                .set(stroom.docstore.impl.db.jooq.tables.Doc.DOC.TYPE, docRef.getType())
                .set(stroom.docstore.impl.db.jooq.tables.Doc.DOC.UUID, docRef.getUuid())
                .set(stroom.docstore.impl.db.jooq.tables.Doc.DOC.NAME, docRef.getName())
                .set(stroom.docstore.impl.db.jooq.tables.Doc.DOC.VERSION,
                        UUID.randomUUID().toString())
                .execute());
    }

    // --- setDependencies ---

    @Test
    void testSetDependencies_insertsEdges() {
        final DocRef from = docRef("Dashboard", "My Dashboard");
        final DocRef to1 = docRef("Index", "My Index");
        final DocRef to2 = docRef("Pipeline", "My Pipeline");

        dao.setDependencies(from, Set.of(to1, to2));

        final int count = JooqUtil.contextResult(connProvider, ctx -> ctx
                .selectCount().from(DOC_DEPENDENCY)
                .where(DOC_DEPENDENCY.FROM_UUID.eq(from.getUuid()))
                .fetchOptional()
                .map(r -> r.value1())
                .orElse(0));
        assertThat(count).isEqualTo(2);
    }

    @Test
    void testSetDependencies_replacesExisting() {
        final DocRef from = docRef("Dashboard", "My Dashboard");
        final DocRef to1 = docRef("Index", "Index A");
        final DocRef to2 = docRef("Index", "Index B");

        // Set initial deps
        dao.setDependencies(from, Set.of(to1));

        // Replace with different dep
        dao.setDependencies(from, Set.of(to2));

        final Set<DocRef> deps = dao.getDependenciesOf(from.getUuid());
        assertThat(deps).hasSize(1);
        assertThat(deps.iterator().next().getUuid()).isEqualTo(to2.getUuid());
    }

    @Test
    void testSetDependencies_emptySetDeletesAll() {
        final DocRef from = docRef("Dashboard", "My Dashboard");
        final DocRef to1 = docRef("Index", "My Index");

        dao.setDependencies(from, Set.of(to1));
        dao.setDependencies(from, Set.of());

        assertThat(dao.getDependenciesOf(from.getUuid())).isEmpty();
    }

    @Test
    void testSetDependencies_nullSetDeletesAll() {
        final DocRef from = docRef("Dashboard", "My Dashboard");
        final DocRef to1 = docRef("Index", "My Index");

        dao.setDependencies(from, Set.of(to1));
        dao.setDependencies(from, null);

        assertThat(dao.getDependenciesOf(from.getUuid())).isEmpty();
    }

    // --- getDependenciesOf ---

    @Test
    void testGetDependenciesOf() {
        final DocRef from = docRef("View", "My View");
        final DocRef to1 = docRef("Index", "Index 1");
        final DocRef to2 = docRef("Pipeline", "Pipeline 1");

        dao.setDependencies(from, Set.of(to1, to2));

        final Set<DocRef> deps = dao.getDependenciesOf(from.getUuid());
        assertThat(deps).hasSize(2);
        assertThat(deps.stream().map(DocRef::getUuid)).containsExactlyInAnyOrder(
                to1.getUuid(), to2.getUuid());
    }

    @Test
    void testGetDependenciesOf_noEdgesReturnsEmpty() {
        assertThat(dao.getDependenciesOf(UUID.randomUUID().toString())).isEmpty();
    }

    // --- getDependantsOf ---

    @Test
    void testGetDependantsOf() {
        final DocRef target = docRef("Index", "Shared Index");
        final DocRef dependant1 = docRef("Dashboard", "Dashboard A");
        final DocRef dependant2 = docRef("View", "View B");

        dao.setDependencies(dependant1, Set.of(target));
        dao.setDependencies(dependant2, Set.of(target));

        final Set<DocRef> dependants = dao.getDependantsOf(target.getUuid());
        assertThat(dependants).hasSize(2);
        assertThat(dependants.stream().map(DocRef::getUuid)).containsExactlyInAnyOrder(
                dependant1.getUuid(), dependant2.getUuid());
    }

    @Test
    void testGetDependantsOf_noReverseEdgesReturnsEmpty() {
        assertThat(dao.getDependantsOf(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    void testGetDependantsOf_resolvesLiveNameFromDocTable() {
        final DocRef target = docRef("Index", "Shared Index");
        // The edge stores the snapshot name, but the doc table holds a newer (live) name.
        final DocRef dependant = docRef("Dashboard", "Stored Dashboard Name");
        final DocRef liveDependant = new DocRef("Dashboard", dependant.getUuid(), "Live Dashboard Name");

        createDocRow(liveDependant);
        dao.setDependencies(dependant, Set.of(target));

        final Set<DocRef> dependants = dao.getDependantsOf(target.getUuid());
        assertThat(dependants).hasSize(1);
        // Name is resolved live from the doc table, not the stored snapshot.
        assertThat(dependants.iterator().next().getName()).isEqualTo("Live Dashboard Name");
    }

    @Test
    void testGetDependantsOf_rollsUpNonExplorerDependantToOwner() {
        // A ProcessorFilter depends on a shared Dictionary. The filter is not an explorer tree node;
        // its owning Pipeline is, so the dependant surfaces as the pipeline.
        final DocRef sharedDict = docRef("Dictionary", "Shared Dictionary");
        final DocRef filter = docRef("ProcessorFilter", "");   // no doc row -> non-explorer source
        final DocRef pipeline = docRef("Pipeline", "Owning Pipeline");

        createDocRow(sharedDict);
        createDocRow(pipeline);        // owner exists in the doc table
        // filter is NOT created

        // The filter depends on its pipeline (owner edge) and the shared dictionary.
        dao.setDependencies(filter, Set.of(pipeline, sharedDict));

        final Set<DocRef> dependants = dao.getDependantsOf(sharedDict.getUuid());

        // The dependant is attributed to the pipeline (a real tree node), not the filter, with the
        // owner's live name.
        assertThat(dependants).hasSize(1);
        final DocRef dependant = dependants.iterator().next();
        assertThat(dependant.getUuid()).isEqualTo(pipeline.getUuid());
        assertThat(dependant.getName()).isEqualTo("Owning Pipeline");
    }

    @Test
    void testGetDependantsOf_nonExplorerDependantWithoutOwnerIsNotRolledUp() {
        final DocRef sharedDict = docRef("Dictionary", "Shared Dictionary");
        final DocRef filter = docRef("ProcessorFilter", "Orphan Filter");

        createDocRow(sharedDict);
        // filter has no doc row and no owner-typed (Pipeline) edge.
        dao.setDependencies(filter, Set.of(sharedDict));

        final Set<DocRef> dependants = dao.getDependantsOf(sharedDict.getUuid());

        // No owner to roll up to, so it stays as the filter itself.
        assertThat(dependants).hasSize(1);
        assertThat(dependants.iterator().next().getUuid()).isEqualTo(filter.getUuid());
    }

    // --- deleteAllForDoc ---

    @Test
    void testDeleteAllForDoc() {
        final DocRef from = docRef("Dashboard", "My Dashboard");
        final DocRef to1 = docRef("Index", "Index 1");

        dao.setDependencies(from, Set.of(to1));
        assertThat(dao.getDependenciesOf(from.getUuid())).hasSize(1);

        dao.deleteAllForDoc(from.getUuid());
        assertThat(dao.getDependenciesOf(from.getUuid())).isEmpty();
    }

    @Test
    void testDeleteAllForDoc_doesNotAffectOtherDocs() {
        final DocRef from1 = docRef("Dashboard", "Dashboard 1");
        final DocRef from2 = docRef("Dashboard", "Dashboard 2");
        final DocRef to = docRef("Index", "Shared Index");

        dao.setDependencies(from1, Set.of(to));
        dao.setDependencies(from2, Set.of(to));

        dao.deleteAllForDoc(from1.getUuid());

        assertThat(dao.getDependenciesOf(from1.getUuid())).isEmpty();
        assertThat(dao.getDependenciesOf(from2.getUuid())).hasSize(1);
    }

    // --- updateName ---

    @Test
    void testUpdateName_updatesFromName() {
        final DocRef from = docRef("Dashboard", "Old Name");
        final DocRef to = docRef("Index", "Index");

        dao.setDependencies(from, Set.of(to));
        dao.updateName(from.getUuid(), "New Name");

        // Read the stored from_name directly
        final String storedName = JooqUtil.contextResult(connProvider, ctx -> ctx
                .select(DOC_DEPENDENCY.FROM_NAME)
                .from(DOC_DEPENDENCY)
                .where(DOC_DEPENDENCY.FROM_UUID.eq(from.getUuid()))
                .fetchOne(DOC_DEPENDENCY.FROM_NAME));
        assertThat(storedName).isEqualTo("New Name");
    }

    @Test
    void testUpdateName_updatesToName() {
        final DocRef from = docRef("Dashboard", "Dashboard");
        final DocRef to = docRef("Index", "Old Index Name");

        dao.setDependencies(from, Set.of(to));
        dao.updateName(to.getUuid(), "New Index Name");

        // Read the stored to_name directly
        final String storedName = JooqUtil.contextResult(connProvider, ctx -> ctx
                .select(DOC_DEPENDENCY.TO_NAME)
                .from(DOC_DEPENDENCY)
                .where(DOC_DEPENDENCY.TO_UUID.eq(to.getUuid()))
                .fetchOne(DOC_DEPENDENCY.TO_NAME));
        assertThat(storedName).isEqualTo("New Index Name");
    }

    // --- getBrokenDependencies ---

    @Test
    void testGetBrokenDependencies_detectsMissingTarget() {
        final DocRef from = docRef("Dashboard", "My Dashboard");
        final DocRef missingTarget = docRef("Index", "Ghost Index");

        // Create the 'from' doc in the doc table so it's live
        createDocRow(from);
        // Don't create missingTarget — it's "deleted" or never existed

        dao.setDependencies(from, Set.of(missingTarget));

        final Map<DocRef, Set<DocRef>> broken = dao.getBrokenDependencies(Set.of());
        assertThat(broken).isNotEmpty();

        // Should contain an entry mapping 'from' -> set containing the missing target
        assertThat(broken.values().stream().flatMap(Set::stream).map(DocRef::getUuid))
                .contains(missingTarget.getUuid());
    }

    @Test
    void testGetBrokenDependencies_excludesPseudoRefs() {
        final DocRef from = docRef("Dashboard", "My Dashboard");
        final String pseudoUuid = UUID.randomUUID().toString();
        final DocRef pseudoTarget = new DocRef("Annotations", pseudoUuid, "Annotations");

        createDocRow(from);
        dao.setDependencies(from, Set.of(pseudoTarget));

        // Pseudo-ref should be excluded
        final Map<DocRef, Set<DocRef>> broken = dao.getBrokenDependencies(Set.of(pseudoUuid));
        assertThat(broken).isEmpty();
    }

    @Test
    void testGetBrokenDependencies_noBrokenWhenTargetExists() {
        final DocRef from = docRef("Dashboard", "My Dashboard");
        final DocRef to = docRef("Index", "Live Index");

        createDocRow(from);
        createDocRow(to);  // Target exists

        dao.setDependencies(from, Set.of(to));

        final Map<DocRef, Set<DocRef>> broken = dao.getBrokenDependencies(Set.of());
        assertThat(broken).isEmpty();
    }

    @Test
    void testGetBrokenDependencies_rollsUpNonExplorerSourceToOwner() {
        // A ProcessorFilter is not an explorer tree node; its owning Pipeline is.
        final DocRef filter = docRef("ProcessorFilter", "");    // no doc row -> non-explorer source
        final DocRef pipeline = docRef("Pipeline", "My Pipeline");
        final DocRef missingDict = docRef("Dictionary", "Ghost Dictionary");

        createDocRow(pipeline);        // owner exists in the doc table
        // filter and missingDict are NOT created

        // The filter depends on its pipeline (healthy) and a now-missing dictionary (broken).
        dao.setDependencies(filter, Set.of(pipeline, missingDict));

        final Map<DocRef, Set<DocRef>> broken = dao.getBrokenDependencies(Set.of());

        // The broken dep is attributed to the pipeline (a real tree node), not the filter.
        assertThat(broken).containsOnlyKeys(pipeline);
        assertThat(broken.get(pipeline).stream().map(DocRef::getUuid))
                .containsExactly(missingDict.getUuid());
        // The rolled-up key carries the owner's live name resolved from the doc table.
        assertThat(broken.keySet().iterator().next().getName()).isEqualTo("My Pipeline");
    }

    @Test
    void testGetBrokenDependencies_nonExplorerSourceWithoutOwnerIsNotRolledUp() {
        // Non-explorer source whose only broken edge has no owner-typed (Pipeline) dependency.
        final DocRef filter = docRef("ProcessorFilter", "");
        final DocRef missingDict = docRef("Dictionary", "Ghost Dictionary");

        dao.setDependencies(filter, Set.of(missingDict));

        final Map<DocRef, Set<DocRef>> broken = dao.getBrokenDependencies(Set.of());

        // No owner to roll up to, so it stays keyed on the filter.
        assertThat(broken).containsOnlyKeys(filter);
        assertThat(broken.get(filter).stream().map(DocRef::getUuid))
                .containsExactly(missingDict.getUuid());
    }

    // --- fetchDependencies (paginated) ---

    @Test
    void testFetchDependencies_returnsPaginatedResults() {
        final DocRef from = docRef("Dashboard", "My Dashboard");
        final DocRef to1 = docRef("Index", "Index 1");
        final DocRef to2 = docRef("Pipeline", "Pipeline 1");
        final DocRef to3 = docRef("Dictionary", "Dict 1");

        createDocRow(from);
        createDocRow(to1);
        createDocRow(to2);
        createDocRow(to3);

        dao.setDependencies(from, Set.of(to1, to2, to3));

        final DependencyCriteria criteria = new DependencyCriteria();
        final ResultPage<Dependency> page = dao.fetchDependencies(criteria, dep -> true);

        assertThat(page.getValues()).hasSize(3);
        assertThat(page.getPageResponse().getTotal()).isEqualTo(3);
    }

    @Test
    void testFetchDependencies_resolvesLiveNamesFromDocTable() {
        final DocRef from = docRef("Dashboard", "Dashboard Name");
        final DocRef to = docRef("Index", "Index Name");

        createDocRow(from);
        createDocRow(to);

        dao.setDependencies(from, Set.of(to));

        final DependencyCriteria criteria = new DependencyCriteria();
        final ResultPage<Dependency> page = dao.fetchDependencies(criteria, dep -> true);

        assertThat(page.getValues()).hasSize(1);
        final Dependency dep = page.getValues().getFirst();
        // Names should be resolved from the doc table (COALESCE prefers live name)
        assertThat(dep.getFrom().getName()).isEqualTo("Dashboard Name");
        assertThat(dep.getTo().getName()).isEqualTo("Index Name");
        assertThat(dep.isOk()).isTrue();
    }

    @Test
    void testFetchDependencies_fallsBackToStoredNameForDeletedDoc() {
        final DocRef from = docRef("Dashboard", "Dashboard Name");
        final DocRef to = docRef("Index", "Stored Index Name");

        createDocRow(from);
        // Don't create 'to' in doc table — simulates deleted doc

        dao.setDependencies(from, Set.of(to));

        final DependencyCriteria criteria = new DependencyCriteria();
        final ResultPage<Dependency> page = dao.fetchDependencies(criteria, dep -> true);

        assertThat(page.getValues()).hasSize(1);
        final Dependency dep = page.getValues().getFirst();
        // to name should fall back to the stored name
        assertThat(dep.getTo().getName()).isEqualTo("Stored Index Name");
        assertThat(dep.isOk()).isFalse();  // Target missing = broken
    }

    // --- Edge cases ---

    @Test
    void testIsolationBetweenDocs() {
        final DocRef fromA = docRef("Dashboard", "A");
        final DocRef fromB = docRef("View", "B");
        final DocRef target = docRef("Index", "Shared");

        dao.setDependencies(fromA, Set.of(target));
        dao.setDependencies(fromB, Set.of(target));

        // Each doc has exactly 1 dependency
        assertThat(dao.getDependenciesOf(fromA.getUuid())).hasSize(1);
        assertThat(dao.getDependenciesOf(fromB.getUuid())).hasSize(1);

        // Target has 2 dependants
        assertThat(dao.getDependantsOf(target.getUuid())).hasSize(2);
    }
}
