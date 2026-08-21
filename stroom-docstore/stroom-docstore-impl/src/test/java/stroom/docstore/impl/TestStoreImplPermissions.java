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

package stroom.docstore.impl;

import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.impl.memory.MemoryPersistence;
import stroom.docstore.shared.AuditAction;
import stroom.importexport.api.ImportExportDocument;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.Message;
import stroom.util.shared.PermissionException;
import stroom.util.shared.Severity;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * The permission matrix of the document store, and the identity that reaches the audit trail.
 *
 * <p>These pin behaviour that is easy to lose silently. A permission check that stops happening
 * produces no error, no log and no failing screen, so it has to be asserted rather than observed; and
 * an audit record that stops naming the real user looks identical to one that still does, until
 * somebody needs it.
 *
 * <p>Driven through {@link AbstractDocumentStore}, because that is the level that decides: a document
 * store is the service layer for its type, and the {@link Store} beneath it is persistence that does
 * what it is asked. Pointed at the raw {@code Store} these tests assert nothing useful — every denial
 * case fails, correctly, which is exactly what happened when the checks moved and this class had not
 * followed them.
 *
 * <p>Two invariants here are load-bearing rather than incidental:
 * <ul>
 *     <li><b>Create requires no document permission.</b> It cannot: the document does not exist yet,
 *         and authority was established one frame up by {@code checkCreatePermission} on the
 *         destination folder. Anything that starts requiring one breaks creation for every non-admin.
 *     <li><b>The real user reaches the persistence layer.</b> {@code DOC_AUDIT} accepts a null user
 *         only for backwards compatibility; a write that records nobody, or that records the
 *         processing user, defeats the audit trail it exists for. This is the invariant that
 *         gwt-bugs #31 broke.
 * </ul>
 */
class TestStoreImplPermissions {

    private static final UserRef ALICE = UserRef.builder().uuid("alice-uuid").subjectId("alice").build();

    /**
     * A user who is not an administrator and holds only the permissions given.
     *
     * <p>The distinction matters more than it looks: {@code hasDocumentPermission} lets an
     * administrator through <b>before</b> consulting any permission row, so a check that has quietly
     * stopped working still passes every test run as an admin. Every denial case here therefore has to
     * be a non-admin.
     */
    private static class TestUser extends MockSecurityContext {

        private final Set<DocumentPermission> granted;

        private TestUser(final DocumentPermission... granted) {
            this.granted = granted.length == 0
                    ? EnumSet.noneOf(DocumentPermission.class)
                    : EnumSet.copyOf(Set.of(granted));
        }

        @Override
        public boolean isAdmin() {
            return false;
        }

        @Override
        public boolean hasDocumentPermission(final DocRef docRef, final DocumentPermission permission) {
            return granted.contains(permission);
        }

        @Override
        public UserRef getUserRef() {
            return ALICE;
        }
    }

    /**
     * One persistence per test, shared by every store built in it.
     *
     * <p>The denial cases need a document to exist before a user without permission tries to touch it,
     * so the document is created through an admin store and then reached through a restricted one.
     * Both have to be looking at the same storage or the test proves only that the document is
     * missing — which is what the first version of this class actually asserted.
     */
    private final Persistence persistence = spy(new MemoryPersistence());

    /**
     * A minimal concrete document store.
     *
     * <p>The tests drive {@link AbstractDocumentStore} rather than the {@link Store} beneath it,
     * because that is the level that decides. Pointed at the raw {@code Store} they assert only what
     * the persistence layer does, which is "whatever it was asked" — the denial cases all fail, and
     * correctly so.
     */
    private static class TestDocumentStore extends AbstractDocumentStore<DictionaryDoc> {

        TestDocumentStore(final StoreFactory storeFactory, final SecurityContext securityContext) {
            super(storeFactory,
                    securityContext,
                    new JsonSerialiser2<>(DictionaryDoc.class),
                    DictionaryDoc.TYPE,
                    DictionaryDoc::builder,
                    DictionaryDoc::copy);
        }

        /** Re-exposes the protected seam so the default can be asserted from here. */
        boolean checkRequired() {
            return isDocumentPermissionCheckRequired();
        }
    }

    /**
     * A store whose documents carry no document permissions — the shape of the singleton configuration
     * documents (data receipt rules, data retention rules, content templates), which are authorised by
     * an application permission at their own entry points instead.
     */
    private static class OptedOutDocumentStore extends TestDocumentStore {

        OptedOutDocumentStore(final StoreFactory storeFactory, final SecurityContext securityContext) {
            super(storeFactory, securityContext);
        }

        @Override
        protected boolean isDocumentPermissionCheckRequired() {
            return false;
        }
    }

    private record Fixture(AbstractDocumentStore<DictionaryDoc> store, Persistence persistence) {

    }

    private StoreFactory storeFactory(final MockSecurityContext securityContext) {
        return new StoreFactoryImpl(persistence, null, securityContext, null, () -> null);
    }

    private Fixture fixture(final MockSecurityContext securityContext) {
        return new Fixture(new TestDocumentStore(storeFactory(securityContext), securityContext), persistence);
    }

    /** An admin fixture, for setting up documents a denial case then tries to touch. */
    private Fixture asAdmin() {
        return fixture(new MockSecurityContext());
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void create_needsNoDocumentPermission() {
        // The document does not exist yet, so there is nothing to hold a permission on.
        final Fixture f = fixture(new TestUser());

        final DocRef docRef = f.store().createDocument("dict1");

        assertThat(docRef).isNotNull();
        assertThat(docRef.getName()).isEqualTo("dict1");
    }

    @Test
    void create_recordsTheRealUserInTheAuditTrail() throws Exception {
        final Fixture f = fixture(new TestUser());

        f.store().createDocument("dict1");

        final ArgumentCaptor<UserRef> user = ArgumentCaptor.forClass(UserRef.class);
        verify(f.persistence(), atLeastOnce())
                .write(any(), eq(AuditAction.CREATE), user.capture(), any(), any(), any());
        assertThat(user.getValue())
                .as("the audit trail must name the user who created the document")
                .isEqualTo(ALICE);
    }

    // ── read ─────────────────────────────────────────────────────────────────

    @Test
    void read_isDeniedWithoutView() {
        final Fixture admin = asAdmin();
        final DocRef docRef = admin.store().createDocument("dict1");

        final Fixture f = fixture(new TestUser());
        assertThatThrownBy(() -> f.store().readDocument(docRef))
                .isInstanceOf(PermissionException.class);
    }

    @Test
    void read_isAllowedWithView() {
        final Fixture admin = asAdmin();
        final DocRef docRef = admin.store().createDocument("dict1");

        final Fixture f = fixture(new TestUser(DocumentPermission.VIEW));
        assertThat(f.store().readDocument(docRef)).isNotNull();
    }

    // ── write ────────────────────────────────────────────────────────────────

    @Test
    void write_isDeniedWithoutEdit() {
        final Fixture admin = asAdmin();
        final DocRef docRef = admin.store().createDocument("dict1");
        final DictionaryDoc doc = admin.store().readDocument(docRef);

        // VIEW but not EDIT: readable, not writable.
        final Fixture f = fixture(new TestUser(DocumentPermission.VIEW));
        assertThatThrownBy(() -> f.store().writeDocument(doc))
                .isInstanceOf(PermissionException.class);
    }

    @Test
    void write_isAllowedWithEdit_andRecordsTheRealUser() throws Exception {
        final Fixture admin = asAdmin();
        final DocRef docRef = admin.store().createDocument("dict1");
        final DictionaryDoc doc = admin.store().readDocument(docRef);

        final Fixture f = fixture(new TestUser(DocumentPermission.VIEW, DocumentPermission.EDIT));
        assertThat(f.store().writeDocument(doc)).isNotNull();

        final ArgumentCaptor<UserRef> user = ArgumentCaptor.forClass(UserRef.class);
        verify(f.persistence(), atLeastOnce())
                .write(any(), eq(AuditAction.UPDATE), user.capture(), any(), any(), any());
        assertThat(user.getValue()).isEqualTo(ALICE);
    }

    // ── delete ───────────────────────────────────────────────────────────────

    @Test
    void delete_isDeniedWithoutDelete() {
        final Fixture admin = asAdmin();
        final DocRef docRef = admin.store().createDocument("dict1");

        final Fixture f = fixture(new TestUser(DocumentPermission.VIEW));
        assertThatThrownBy(() -> f.store().deleteDocument(docRef))
                .isInstanceOf(PermissionException.class);
    }

    @Test
    void delete_isAllowedWithDelete_andRecordsTheRealUser() {
        final Fixture admin = asAdmin();
        final DocRef docRef = admin.store().createDocument("dict1");

        final Fixture f = fixture(new TestUser(DocumentPermission.DELETE));
        f.store().deleteDocument(docRef);

        verify(f.persistence()).delete(any(), eq(ALICE));
    }

    // ── list ─────────────────────────────────────────────────────────────────

    @Test
    void listDocuments_omitsDocumentsTheUserCannotView() {
        final Fixture admin = asAdmin();
        admin.store().createDocument("dict1");
        admin.store().createDocument("dict2");

        assertThat(fixture(new TestUser()).store().listDocuments())
                .as("a user with no VIEW sees nothing")
                .isEmpty();
        assertThat(fixture(new TestUser(DocumentPermission.VIEW)).store().listDocuments())
                .as("a user with VIEW sees both")
                .hasSize(2);
    }

    // ── rename / copy / move ─────────────────────────────────────────────────
    // These three took their authorisation from passing through the store's read() and update()
    // rather than declaring it, so moving those checks up left all three unguarded. Rename was the
    // dangerous one: ExplorerServiceImpl checks nothing of its own on that path.

    @Test
    void rename_isDeniedWithoutEdit() {
        final Fixture admin = asAdmin();
        final DocRef docRef = admin.store().createDocument("dict1");

        // VIEW is not enough to rename.
        final Fixture f = fixture(new TestUser(DocumentPermission.VIEW));
        assertThatThrownBy(() -> f.store().renameDocument(docRef, "renamed"))
                .isInstanceOf(PermissionException.class);
    }

    @Test
    void rename_isAllowedWithEdit() {
        final Fixture admin = asAdmin();
        final DocRef docRef = admin.store().createDocument("dict1");

        final Fixture f = fixture(new TestUser(DocumentPermission.VIEW, DocumentPermission.EDIT));
        assertThat(f.store().renameDocument(docRef, "renamed").getName()).isEqualTo("renamed");
    }

    @Test
    void copy_isDeniedWithoutViewOnTheSource() {
        final Fixture admin = asAdmin();
        final DocRef docRef = admin.store().createDocument("dict1");

        final Fixture f = fixture(new TestUser());
        assertThatThrownBy(() -> f.store().copyDocument(docRef, "copy", false, Set.of()))
                .isInstanceOf(PermissionException.class);
    }

    @Test
    void move_isDeniedWithoutView() {
        final Fixture admin = asAdmin();
        final DocRef docRef = admin.store().createDocument("dict1");

        final Fixture f = fixture(new TestUser());
        assertThatThrownBy(() -> f.store().moveDocument(docRef))
                .isInstanceOf(PermissionException.class);
    }

    // ── the opt-out seam ─────────────────────────────────────────────────────

    @Test
    void theCheckIsRequiredUnlessAStoreSaysOtherwise() {
        // The default has to be safe: a store that says nothing gets the check. A store that opts out
        // is making a statement about its document type, not forgetting to call super.
        final TestUser testUser = new TestUser();
        assertThat(new TestDocumentStore(storeFactory(testUser), testUser).checkRequired())
                .as("default")
                .isTrue();
        assertThat(new OptedOutDocumentStore(storeFactory(testUser), testUser).checkRequired())
                .as("opted out")
                .isFalse();
    }

    @Test
    void aStoreThatOptsOutIsNotDocumentChecked_butStillRecordsTheRealUser() throws Exception {
        // This is what the three Administration singleton documents rely on. The alternative route to
        // the same access — running as the processing user — costs the audit trail the name of whoever
        // made the change, so the attribution half is asserted here too, not just the access half.
        final AbstractDocumentStore<DictionaryDoc> admin = asAdmin().store();
        final DocRef docRef = admin.createDocument("dict1");
        final DictionaryDoc doc = admin.readDocument(docRef);

        // A user with NO document permissions at all.
        final TestUser testUser = new TestUser();
        final AbstractDocumentStore<DictionaryDoc> store =
                new OptedOutDocumentStore(storeFactory(testUser), testUser);

        assertThatCode(() -> store.readDocument(docRef)).doesNotThrowAnyException();
        assertThatCode(() -> store.writeDocument(doc)).doesNotThrowAnyException();
        assertThat(store.listDocuments()).hasSize(1);

        final ArgumentCaptor<UserRef> user = ArgumentCaptor.forClass(UserRef.class);
        verify(persistence, atLeastOnce())
                .write(any(), eq(AuditAction.UPDATE), user.capture(), any(), any(), any());
        assertThat(user.getValue())
                .as("skipping the document check must not change who the audit trail names")
                .isEqualTo(ALICE);
    }

    // ── export ───────────────────────────────────────────────────────────────

    @Test
    void export_isDeniedWithoutView() {
        final Fixture admin = asAdmin();
        final DocRef docRef = admin.store().createDocument("dict1");

        final Fixture f = fixture(new TestUser());
        final List<Message> messages = new ArrayList<>();
        assertThatThrownBy(() -> f.store().exportDocument(docRef, true, messages))
                .isInstanceOf(PermissionException.class);
    }

    // ── remapDependencies ────────────────────────────────────────────────────

    @Test
    void remapDependencies_isDeniedWithoutEdit() {
        // It reads the document and writes it back, so it is a mutation. The denial has to be raised
        // above StoreImpl, which catches RuntimeException and logs it: swallowed there, an
        // unauthorised remap leaves a copy silently pointing at the originals' dependencies.
        final Fixture admin = asAdmin();
        final DocRef docRef = admin.store().createDocument("dict1");

        final Fixture f = fixture(new TestUser(DocumentPermission.VIEW));
        assertThatThrownBy(() -> f.store().remapDependencies(docRef, Map.of()))
                .isInstanceOf(PermissionException.class);
    }

    // ── list() ───────────────────────────────────────────────────────────────
    // Separate from listDocuments(), and separately filtered — moving only the latter's filter would
    // have left this one returning everything.

    @Test
    void list_omitsDocumentsTheUserCannotView() {
        final Fixture admin = asAdmin();
        admin.store().createDocument("dict1");
        admin.store().createDocument("dict2");

        assertThat(fixture(new TestUser()).store().list())
                .as("a user with no VIEW sees nothing")
                .isEmpty();
        assertThat(fixture(new TestUser(DocumentPermission.VIEW)).store().list())
                .as("a user with VIEW sees both")
                .hasSize(2);
    }

    // ── getIndexableData ─────────────────────────────────────────────────────

    @Test
    void indexableData_isEmptyWithoutView() {
        // This one feeds an index rather than answering a user, so it returns nothing rather than
        // throwing — the pre-existing contract, preserved when the check moved.
        final Fixture admin = asAdmin();
        final DocRef docRef = admin.store().createDocument("dict1");

        assertThat(fixture(new TestUser()).store().getIndexableData(docRef))
                .as("no VIEW")
                .isEmpty();
        assertThat(fixture(new TestUser(DocumentPermission.VIEW)).store().getIndexableData(docRef))
                .as("with VIEW")
                .isNotNull();
    }

    // ── importDocument ───────────────────────────────────────────────────────

    @Test
    void import_ofANewDocumentNeedsNoDocumentPermission() {
        // The invariant that must not regress: a document being imported for the first time cannot be
        // authorised against itself, for the same reason a create cannot. This is the path that loads
        // content packs at startup, so breaking it breaks the instance rather than a screen. The check
        // for the update case is conditional on the document existing and stays in StoreImpl.
        final Fixture admin = asAdmin();
        final DocRef source = admin.store().createDocument("dict1");
        final ImportExportDocument exported = admin.store().exportDocument(source, false, new ArrayList<>());

        final DocRef fresh = new DocRef(DictionaryDoc.TYPE, "imported-uuid", "imported");
        final ImportState importState = new ImportState(fresh, "imported");
        final Fixture f = fixture(new TestUser());

        assertThatCode(() -> f.store().importDocument(
                fresh, exported, importState, ImportSettings.auto()))
                .doesNotThrowAnyException();

        // Assert it actually landed. Import collects failures onto the ImportState rather than
        // throwing, so "did not throw" on its own would pass even if nothing had happened.
        assertThat(importState.getSeverity())
                .as("import reported: " + importState.getMessageList())
                .isNotEqualTo(Severity.ERROR);
        assertThat(asAdmin().store().listDocuments())
                .as("the imported document exists alongside the source")
                .hasSize(2);
    }
}
