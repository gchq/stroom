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

package stroom.planb.impl;

import stroom.docref.DocRef;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.PlanBDocument;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.planb.shared.SnapshotSettings;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TemporalStateSettings;
import stroom.planb.shared.TraceSettings;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which node a Plan B query is pinned to.
 *
 * <p>This fork generalised {@code getNode} from "only {@code PlanBDoc}" to "any registered Plan B
 * store whose data is node-local", because it adds a store type of its own. The tests that matter
 * most here are the ones showing <b>upstream's own behaviour is unchanged</b> — that is the claim
 * the generalisation rests on, and the one a merge from master could quietly break.</p>
 *
 * <p>Returning {@code null} is not a failure: the query then runs wherever it landed and is answered
 * from that node's snapshot. It means live data is not guaranteed, which is why it must be the
 * caller's choice rather than an accident of document type.</p>
 */
class TestQueryNodeResolverImpl {

    private static final String STORAGE_NODE = "node1";
    private static final String OTHER_LOCAL_TYPE = "SomeForkStore";

    /** A cache that answers from a fixed name → document map. */
    private static PlanBDocCache cacheOf(final Map<String, PlanBDocument> docs) {
        return new PlanBDocCache() {
            @Override
            public List<PlanBDocument> getAll() {
                return List.copyOf(docs.values());
            }

            @Override
            public PlanBDocument get(final String name) {
                return docs.get(name);
            }

            @Override
            public void remove(final String name) {
                // Not exercised: the resolver only reads.
            }
        };
    }

    private static QueryNodeResolverImpl resolver(final Map<String, PlanBDocument> docs,
                                                  final Set<String> registeredTypes) {
        final PlanBConfig config = new PlanBConfig(
                null, List.of(STORAGE_NODE), null, null, null, null);
        return new QueryNodeResolverImpl(cacheOf(docs), () -> config, registeredTypes);
    }

    private static PlanBDoc planBDoc(final String name, final AbstractPlanBSettings settings) {
        return PlanBDoc.builder()
                .uuid("uuid-" + name)
                .name(name)
                .stateType(StateType.TEMPORAL_STATE)
                .settings(settings)
                .build();
    }

    /**
     * A document standing in for one of another registered type.
     *
     * <p>Its own class does not matter: the resolver reads the type from the {@link DocRef} and the
     * document only for its settings, which is exactly how a real one of another type arrives —
     * {@code PlanBDocCache} returns whatever {@link PlanBDocument} that type registered.</p>
     */
    private static PlanBDocument forkDoc(final String name, final AbstractPlanBSettings settings) {
        return planBDoc(name, settings);
    }

    private static TemporalStateSettings nodeLocal() {
        return new TemporalStateSettings.Builder().build();
    }

    private static TemporalStateSettings snapshotsForQuery() {
        return new TemporalStateSettings.Builder()
                .snapshotSettings(new SnapshotSettings(true, true, true))
                .build();
    }

    private static TraceSettings onSharedStorage() {
        return new TraceSettings.Builder()
                .sharedFileStore(new SharedFileStoreSettings(1, "/mnt/shared"))
                .build();
    }

    // ------------------------------------------------------------------
    // Upstream's behaviour, which this change must not alter.
    // ------------------------------------------------------------------

    @Test
    void planBDocIsPinnedToTheStorageNode() {
        final QueryNodeResolverImpl resolver = resolver(
                Map.of("events", planBDoc("events", nodeLocal())),
                Set.of(PlanBDoc.TYPE));

        assertThat(resolver.getNode(DocRef.builder().type(PlanBDoc.TYPE).uuid("uuid-events").name("events").build()))
                .isEqualTo(STORAGE_NODE);
    }

    @Test
    void planBDocAskingForSnapshotReadsIsNotPinned() {
        final QueryNodeResolverImpl resolver = resolver(
                Map.of("events", planBDoc("events", snapshotsForQuery())),
                Set.of(PlanBDoc.TYPE));

        assertThat(resolver.getNode(DocRef.builder().type(PlanBDoc.TYPE).uuid("uuid-events").name("events").build()))
                .isNull();
    }

    /**
     * The safety clause: {@code PlanBDoc} is named explicitly as well as looked up.
     *
     * <p>So a missing or broken multibinding cannot change upstream's behaviour — it could only
     * affect this fork's own types.</p>
     */
    @Test
    void planBDocIsStillPinnedWhenNoTypesAreRegistered() {
        final QueryNodeResolverImpl resolver = resolver(
                Map.of("events", planBDoc("events", nodeLocal())),
                Set.of());

        assertThat(resolver.getNode(DocRef.builder().type(PlanBDoc.TYPE).uuid("uuid-events").name("events").build()))
                .isEqualTo(STORAGE_NODE);
    }

    // ------------------------------------------------------------------
    // What the generalisation adds.
    // ------------------------------------------------------------------

    @Test
    void anotherRegisteredTypeWithNodeLocalDataIsPinned() {
        final QueryNodeResolverImpl resolver = resolver(
                Map.of("events", forkDoc("events", nodeLocal())),
                Set.of(PlanBDoc.TYPE, OTHER_LOCAL_TYPE));

        assertThat(resolver.getNode(DocRef.builder().type(OTHER_LOCAL_TYPE).uuid("uuid-events").name("events").build()))
                .as("this is the whole point: without it the query reads a snapshot instead")
                .isEqualTo(STORAGE_NODE);
    }

    @Test
    void anotherRegisteredTypeAlsoHonoursItsOwnSnapshotSetting() {
        final QueryNodeResolverImpl resolver = resolver(
                Map.of("events", forkDoc("events", snapshotsForQuery())),
                Set.of(PlanBDoc.TYPE, OTHER_LOCAL_TYPE));

        assertThat(resolver.getNode(DocRef.builder().type(OTHER_LOCAL_TYPE).uuid("uuid-events").name("events").build()))
                .as("the setting was silently inert before, which is the defect")
                .isNull();
    }

    /**
     * A store on shared storage is reachable from every node, so there is no node to pin to.
     *
     * <p>This is what keeps traces out, and it excludes them for the reason rather than by naming the
     * type — so any future shared-file-store type is excluded too.</p>
     */
    @Test
    void registeredTypeOnSharedStorageIsNotPinned() {
        final QueryNodeResolverImpl resolver = resolver(
                Map.of("traces", forkDoc("traces", onSharedStorage())),
                Set.of(PlanBDoc.TYPE, OTHER_LOCAL_TYPE));

        assertThat(resolver.getNode(DocRef.builder().type(OTHER_LOCAL_TYPE).uuid("uuid-traces").name("traces").build()))
                .isNull();
    }

    // ------------------------------------------------------------------
    // Everything else.
    // ------------------------------------------------------------------

    @Test
    void anUnregisteredTypeIsNotPinned() {
        final QueryNodeResolverImpl resolver = resolver(
                Map.of("events", planBDoc("events", nodeLocal())),
                Set.of(PlanBDoc.TYPE));

        assertThat(resolver.getNode(DocRef.builder().type("LuceneIndex").uuid("uuid-events").name("events").build()))
                .isNull();
    }

    @Test
    void nullDocRefIsNotPinned() {
        assertThat(resolver(Map.of(), Set.of(PlanBDoc.TYPE)).getNode(null)).isNull();
    }

    @Test
    void registeredTypeWithNoDocumentIsNotPinned() {
        final QueryNodeResolverImpl resolver =
                resolver(Map.of(), Set.of(PlanBDoc.TYPE, OTHER_LOCAL_TYPE));

        final DocRef missing = DocRef.builder()
                .type(OTHER_LOCAL_TYPE)
                .uuid("uuid-missing")
                .name("missing")
                .build();

        assertThat(resolver.getNode(missing)).isNull();
    }
}
