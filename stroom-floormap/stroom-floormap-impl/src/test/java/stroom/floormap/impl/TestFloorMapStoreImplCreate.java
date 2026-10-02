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

package stroom.floormap.impl;

import stroom.docref.DocRef;
import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.docstore.impl.StoreFactoryImpl;
import stroom.docstore.impl.memory.MemoryPersistence;
import stroom.document.asset.impl.DocumentAssetService;
import stroom.floormap.shared.FloorMapDoc;
import stroom.security.api.UserIdentity;
import stroom.security.mock.MockSecurityContext;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Creating a floor map through the real store.
///
/// A create is a single write made as the creating user. The store used to read the new document
/// back and write it again inside `securityContext.asProcessingUser(...)`, a workaround from when
/// the docstore checked permissions on every read and write and a new document had none yet. Those
/// checks now live in `AbstractDocumentStore` and `getStore()` is unchecked, so the workaround
/// guarded nothing, and its second write stamped the processing user as the document's
/// `updateUser`. `MockSecurityContext` does not change identity in `asProcessingUser`, so
/// these tests use a context that does, which is what lets them see who the audit stamp names.
class TestFloorMapStoreImplCreate {

    private static final String CREATING_USER = "admin";
    private static final String PROCESSING_USER = "processing-user";

    @Test
    void createStampsTheCreatingUserAsCreator() {
        final FloorMapDoc doc = createAndRead(new IdentitySwitchingSecurityContext());

        assertThat(doc.getCreateUser()).isEqualTo(CREATING_USER);
    }

    @Test
    void createStampsTheCreatingUserAsUpdater() {
        final FloorMapDoc doc = createAndRead(new IdentitySwitchingSecurityContext());

        assertThat(doc.getUpdateUser())
                .as("a new document is last updated by whoever created it, not the processing user")
                .isEqualTo(CREATING_USER);
    }

    @Test
    void createDoesNotRunAsTheProcessingUser() {
        final IdentitySwitchingSecurityContext securityContext = new IdentitySwitchingSecurityContext();

        createAndRead(securityContext);

        assertThat(securityContext.getProcessingUserCallCount())
                .as("nothing about creating a floor map needs elevated privileges")
                .isZero();
    }

    @Test
    void createReturnsAReadableDocumentWithTheGivenName() {
        final FloorMapStoreImpl store = store(new IdentitySwitchingSecurityContext());

        final DocRef docRef = store.createDocument("Ground Floor");
        final FloorMapDoc doc = store.readDocument(docRef);

        assertThat(docRef.getType()).isEqualTo(FloorMapDoc.TYPE);
        assertThat(docRef.getName()).isEqualTo("Ground Floor");
        assertThat(doc.getUuid()).isEqualTo(docRef.getUuid());
        assertThat(doc.getName()).isEqualTo("Ground Floor");
        assertThat(doc.getVersion()).isNotNull();
    }

    @Test
    void createWithANullNameIsRejected() {
        final FloorMapStoreImpl store = store(new IdentitySwitchingSecurityContext());

        assertThatThrownBy(() -> store.createDocument(null))
                .isInstanceOf(NullPointerException.class);
    }

    private FloorMapDoc createAndRead(final IdentitySwitchingSecurityContext securityContext) {
        final FloorMapStoreImpl store = store(securityContext);
        return store.readDocument(store.createDocument("Ground Floor"));
    }

    private FloorMapStoreImpl store(final MockSecurityContext securityContext) {
        return new FloorMapStoreImpl(
                new StoreFactoryImpl(new MemoryPersistence(), null, securityContext, null, () -> null),
                new FloorMapSerialiser(new Serialiser2FactoryImpl()),
                securityContext,
                Mockito.mock(DocumentAssetService.class));
    }

    /// A [MockSecurityContext] whose `asProcessingUser` really does run as another identity,
    /// and counts how often it is asked to.
    private static class IdentitySwitchingSecurityContext extends MockSecurityContext {

        private static final UserIdentity PROCESSING_IDENTITY = () -> PROCESSING_USER;

        private UserIdentity currentIdentity = null;
        private int processingUserCallCount = 0;

        @Override
        public UserIdentity getUserIdentity() {
            return currentIdentity != null
                    ? currentIdentity
                    : super.getUserIdentity();
        }

        @Override
        public void asProcessingUser(final Runnable runnable) {
            asProcessingUserResult(() -> {
                runnable.run();
                return null;
            });
        }

        @Override
        public <T> T asProcessingUserResult(final Supplier<T> supplier) {
            processingUserCallCount++;
            final UserIdentity previousIdentity = currentIdentity;
            currentIdentity = PROCESSING_IDENTITY;
            try {
                return supplier.get();
            } finally {
                currentIdentity = previousIdentity;
            }
        }

        int getProcessingUserCallCount() {
            return processingUserCallCount;
        }
    }
}
