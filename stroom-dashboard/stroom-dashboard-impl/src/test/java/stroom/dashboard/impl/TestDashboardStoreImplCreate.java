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

package stroom.dashboard.impl;

import stroom.dashboard.shared.DashboardDoc;
import stroom.docref.DocRef;
import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.docstore.impl.StoreFactoryImpl;
import stroom.docstore.impl.memory.MemoryPersistence;
import stroom.security.mock.MockSecurityContext;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Creating a dashboard through the REAL store.
 *
 * <p>The store-level permission tests use a synthetic store over a simple document type, which proves
 * the checks but says nothing about whether a given store produces the document it is supposed to.
 * Dashboard is the one that does real work on create: it applies a template.
 *
 * <p>That template is built into the document skeleton, so creating a dashboard is a single write.
 * Applying it instead by reading the new document back and writing it again inside
 * {@code securityContext.asProcessingUser(...)} would make creating a dashboard impossible, because
 * the audit stamp needs a {@code UserRef} the processing identity does not have (gwt-bugs #31). This
 * asserts what the skeleton has to deliver: a new dashboard arrives with its template, and with the
 * creating user on it.
 */
class TestDashboardStoreImplCreate {

    private DashboardStoreImpl store() {
        final MockSecurityContext securityContext = new MockSecurityContext();
        return new DashboardStoreImpl(
                new StoreFactoryImpl(new MemoryPersistence(), null, securityContext, null, () -> null),
                securityContext,
                new DashboardSerialiser(new Serialiser2FactoryImpl()));
    }

    @Test
    void createAppliesTheTemplate() {
        final DashboardStoreImpl store = store();

        final DocRef docRef = store.createDocument("dash1");
        final DashboardDoc doc = store.readDocument(docRef);

        assertThat(docRef).isNotNull();
        assertThat(doc.getName()).isEqualTo("dash1");
        assertThat(doc.getDashboardConfig())
                .as("a new dashboard is created from the template, not empty")
                .isNotNull();
        assertThat(doc.getDashboardConfig().getComponents())
                .as("the template's components")
                .isNotEmpty();
    }

    @Test
    void createStampsTheCreatingUser() {
        final DashboardStoreImpl store = store();

        final DashboardDoc doc = store.readDocument(store.createDocument("dash1"));

        assertThat(doc.getCreateUser()).isNotNull();
        assertThat(doc.getUpdateUser()).isNotNull();
        assertThat(doc.getUuid()).isNotNull();
        assertThat(doc.getVersion()).isNotNull();
    }
}
