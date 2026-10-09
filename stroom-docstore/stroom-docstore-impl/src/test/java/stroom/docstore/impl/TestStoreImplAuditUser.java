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

package stroom.docstore.impl;

import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.DocDependencyService;
import stroom.docstore.api.Store;
import stroom.docstore.shared.AuditAction;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.api.exception.AuthenticationException;
import stroom.security.mock.MockSecurityContext;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests which {@link UserRef} {@link StoreImpl} records in the audit trail for each write.
 * <p>
 * Document writes performed as the internal processing (service) user have no stroom user account to
 * record, because no service user identity implements {@link stroom.security.shared.HasUserRef}. Such
 * writes must still succeed with a null audit user rather than failing the whole operation, otherwise
 * anything running inside {@link SecurityContext#asProcessingUser} that touches a document breaks,
 * e.g. auto-creation of the singleton Data Retention document.
 *
 * @see StoreImpl#getAuditUserRef()
 */
class TestStoreImplAuditUser {

    /**
     * A {@link SecurityContext} behaving as it does while running as the internal processing user, i.e.
     * the current identity has no associated stroom user, so {@link SecurityContext#getUserRef()} fails.
     */
    private static class ProcessingUserSecurityContext extends MockSecurityContext {

        @Override
        public UserIdentity getUserIdentity() {
            // Deliberately not a HasUserRef, as per InternalIdpProcessingUserIdentity/ServiceUserIdentity.
            return () -> "INTERNAL_PROCESSING_USER";
        }

        @Override
        public UserRef getUserRef() {
            throw new AuthenticationException("Expecting a stroom user identity");
        }
    }

    /**
     * A {@link SecurityContext} with no logged in user at all.
     */
    private static class NoUserSecurityContext extends MockSecurityContext {

        @Override
        public UserIdentity getUserIdentity() {
            return null;
        }

        @Override
        public UserRef getUserRef() {
            throw new AuthenticationException("No user is currently logged in");
        }
    }

    @Test
    void create_asRealUser_recordsThatUser() throws IOException {
        final Persistence persistence = mock(Persistence.class);
        final SecurityContext securityContext = new MockSecurityContext();
        final Store<DictionaryDoc> store = createStore(persistence, securityContext);

        store.createDocument("dict1");

        assertThat(capturedWriteUserRef(persistence, AuditAction.CREATE))
                .isEqualTo(securityContext.getUserRef());
    }

    @Test
    void create_asProcessingUser_succeedsAndRecordsNoUser() throws IOException {
        final Persistence persistence = mock(Persistence.class);
        final Store<DictionaryDoc> store = createStore(persistence, new ProcessingUserSecurityContext());

        // Must not throw, even though there is no stroom user to record.
        final DocRef docRef = store.createDocument("dict1");

        assertThat(docRef).isNotNull();
        assertThat(capturedWriteUserRef(persistence, AuditAction.CREATE)).isNull();
    }

    @Test
    void delete_asProcessingUser_succeedsAndRecordsNoUser() {
        final Persistence persistence = mock(Persistence.class);
        final Store<DictionaryDoc> store = createStore(persistence, new ProcessingUserSecurityContext());
        final DocRef docRef = store.createDocument("dict1");

        store.deleteDocument(docRef);

        final ArgumentCaptor<UserRef> captor = ArgumentCaptor.forClass(UserRef.class);
        verify(persistence).delete(eq(docRef), captor.capture());
        assertThat(captor.getValue()).isNull();
    }

    @Test
    void create_withNoUser_stillFails() {
        final Persistence persistence = mock(Persistence.class);
        final Store<DictionaryDoc> store = createStore(persistence, new NoUserSecurityContext());

        // Not having a user at all remains an error, as distinct from running as the processing user.
        assertThatThrownBy(() -> store.createDocument("dict1"))
                .isInstanceOf(AuthenticationException.class);
    }

    private static UserRef capturedWriteUserRef(final Persistence persistence, final AuditAction auditAction)
            throws IOException {
        final ArgumentCaptor<UserRef> captor = ArgumentCaptor.forClass(UserRef.class);
        verify(persistence).write(any(), eq(auditAction), captor.capture(), any(), any(), any());
        return captor.getValue();
    }

    /**
     * A lightweight store over a mocked persistence so we can capture what gets audited.
     * entityEventBus and docFinderProvider are not needed for these paths and are nullable.
     */
    private static Store<DictionaryDoc> createStore(final Persistence persistence,
                                                    final SecurityContext securityContext) {
        final StoreFactoryImpl storeFactory = new StoreFactoryImpl(
                persistence,
                null,
                securityContext,
                null,
                () -> mock(DocDependencyService.class));

        return storeFactory.createStore(
                new JsonSerialiser2<>(DictionaryDoc.class),
                DictionaryDoc.TYPE,
                DictionaryDoc::builder,
                DictionaryDoc::copy,
                () -> (doc, remapper) -> doc);
    }
}
