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
import stroom.docstore.api.DocFinder;
import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.docstore.impl.StoreFactoryImpl;
import stroom.docstore.impl.memory.MemoryPersistence;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.planb.shared.PlanBDoc;
import stroom.security.mock.MockSecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.Severity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// A state store's name must be unique across every Plan B store type, because Plan B resolves a
/// store by name alone and fails when a name matches more than one store.
class TestPlanBDocStoreImpl {

    // Another Plan B store type, standing in for a Floor Map event store or a Traces store.
    private static final String OTHER_TYPE = "OtherPlanBType";

    private DocFinder docFinder;
    private ToggleableSecurityContext securityContext;
    private PlanBDocStoreImpl store;

    @BeforeEach
    void setUp() {
        docFinder = Mockito.mock(DocFinder.class);
        securityContext = new ToggleableSecurityContext();
        store = new PlanBDocStoreImpl(
                new StoreFactoryImpl(new MemoryPersistence(), null, securityContext, null, () -> null),
                new PlanBDocSerialiser(new Serialiser2FactoryImpl()),
                securityContext,
                () -> null,
                new PlanBStoreNames(securityContext, () -> docFinder, Set.of(PlanBDoc.TYPE, OTHER_TYPE)));
    }

    /// Regression test: a state store could take the name of a Floor Map event store, because names
    /// were only checked among state stores.
    @Test
    void createRefusesANameAStoreOfAnotherTypeHas() {
        givenAStoreOfAnotherTypeNamed("events");

        assertThatThrownBy(() -> store.createDocument("events"))
                .isInstanceOf(EntityServiceException.class)
                .hasMessageContaining("events");
        assertThat(store.list())
                .as("the rolled-back document must not be left behind")
                .isEmpty();
    }

    @Test
    void renameRefusesANameAStoreOfAnotherTypeHas() {
        final DocRef docRef = store.createDocument("mine");
        givenAStoreOfAnotherTypeNamed("events");

        assertThatThrownBy(() -> store.renameDocument(docRef, "events"))
                .isInstanceOf(EntityServiceException.class);
        assertThat(store.readDocument(docRef).getName()).isEqualTo("mine");
    }

    @Test
    void aStoreMayBeRenamedToItsOwnName() {
        final DocRef docRef = store.createDocument("mine");

        assertThat(store.renameDocument(docRef, "mine").getName()).isEqualTo("mine");
    }

    /// Regression test: the name a copy was given was only made unique among state stores.
    @Test
    void aCopyIsNamedClearOfStoresOfOtherTypes() {
        final DocRef docRef = store.createDocument("events");
        Mockito.when(docFinder.findByName(OTHER_TYPE, "*", true))
                .thenReturn(List.of(new DocRef(OTHER_TYPE, "other-uuid", "events2")));

        final DocRef copy = store.copyDocument(docRef, "events", true, Set.of());

        assertThat(copy.getName()).isEqualTo("events3");
    }

    @Test
    void aCopyMayNotTakeANameAStoreOfAnotherTypeHas() {
        final DocRef docRef = store.createDocument("mine");
        givenAStoreOfAnotherTypeNamed("events");

        assertThatThrownBy(() -> store.copyDocument(docRef, "events", false, Set.of()))
                .isInstanceOf(EntityServiceException.class);
    }

    /// Regression test: names were checked only among the state stores the user could see, but a
    /// store hidden from them breaks name resolution just the same.
    @Test
    void aStateStoreTheUserCannotSeeStillHasItsName() {
        store.createDocument("events");
        securityContext.canView = false;

        assertThatThrownBy(() -> store.createDocument("events"))
                .isInstanceOf(EntityServiceException.class);
    }

    /// Regression test: import did not check names at all, so a content pack could bring in a store
    /// with a name another Plan B store already had.
    @Test
    void importRefusesANewStoreWithANameAStoreOfAnotherTypeHas() {
        givenAStoreOfAnotherTypeNamed("events");
        final DocRef imported = new DocRef(PlanBDoc.TYPE, "new-uuid", "events");
        final ImportState importState = new ImportState(imported, "events");

        store.importDocument(imported, null, importState, ImportSettings.auto());

        assertThat(importState.getMessageList())
                .singleElement()
                .satisfies(message -> {
                    assertThat(message.getSeverity()).isEqualTo(Severity.ERROR);
                    assertThat(message.getMessage()).contains("events");
                });
    }

    /// An existing store keeps its own name on import, so re-importing it never clashes with itself.
    @Test
    void importOfAnExistingStoreIsNotRefused() {
        final DocRef existing = store.createDocument("events");
        final ImportState importState = new ImportState(existing, "events");

        store.importDocument(existing, null, importState, ImportSettings.auto());

        assertThat(importState.getMessageList()).isEmpty();
    }

    private void givenAStoreOfAnotherTypeNamed(final String name) {
        Mockito.when(docFinder.findByName(OTHER_TYPE, name))
                .thenReturn(List.of(new DocRef(OTHER_TYPE, "other-uuid", name)));
    }

    // A security context whose user can be made unable to see any document.
    private static class ToggleableSecurityContext extends MockSecurityContext {

        private boolean canView = true;

        @Override
        public boolean hasDocumentPermission(final DocRef docRef, final DocumentPermission permission) {
            return canView;
        }
    }
}
