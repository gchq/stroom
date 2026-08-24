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

package stroom.planb.impl;

import stroom.docref.DocRef;
import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.docstore.impl.StoreFactoryImpl;
import stroom.docstore.impl.memory.MemoryPersistence;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateType;
import stroom.security.mock.MockSecurityContext;
import stroom.util.shared.EntityServiceException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Creating a state store through the REAL store.
 *
 * <p>Plan B applies a default state type on create, built into the document skeleton, so there is no
 * second write to authorise. Applying it instead by reading the new document back and writing it again
 * would work only because {@code hasDocumentPermission} lets an administrator through before
 * consulting any permission row — a non-admin creating a state store would be refused.
 */
class TestPlanBDocStoreImplCreate {

    private PlanBDocStoreImpl store() {
        final MockSecurityContext securityContext = new MockSecurityContext();
        return new PlanBDocStoreImpl(
                new StoreFactoryImpl(new MemoryPersistence(), null, securityContext, null, () -> null),
                securityContext,
                new PlanBDocSerialiser(new Serialiser2FactoryImpl()));
    }

    @Test
    void createAppliesTheDefaultStateType() {
        final PlanBDocStoreImpl store = store();

        final DocRef docRef = store.createDocument("my_state_store");
        final PlanBDoc doc = store.readDocument(docRef);

        assertThat(doc.getName()).isEqualTo("my_state_store");
        assertThat(doc.getStateType())
                .as("a new state store defaults to TEMPORAL_STATE")
                .isEqualTo(StateType.TEMPORAL_STATE);
        assertThat(doc.getCreateUser()).isNotNull();
    }

    @Test
    void createStillValidatesTheName() {
        // The key pattern is checked before anything is created.
        assertThatThrownBy(() -> store().createDocument("Not A Valid Key"))
                .isInstanceOf(EntityServiceException.class);
    }

    @Test
    void createRejectsADuplicateName() {
        // Exercises the rollback path, which deletes the document it just created through the
        // unchecked handle.
        final PlanBDocStoreImpl store = store();
        store.createDocument("my_state_store");

        assertThatThrownBy(() -> store.createDocument("my_state_store"))
                .isInstanceOf(EntityServiceException.class);

        assertThat(store.list())
                .as("the rolled-back document must not be left behind")
                .hasSize(1);
    }
}
