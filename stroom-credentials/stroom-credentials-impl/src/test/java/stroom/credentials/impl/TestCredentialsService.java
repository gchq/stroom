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

package stroom.credentials.impl;

import stroom.ai.shared.KeyStoreType;
import stroom.credentials.api.StoredSecret;
import stroom.credentials.shared.AccessTokenSecret;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.credentials.shared.KeyStoreSecret;
import stroom.credentials.shared.PutCredentialRequest;
import stroom.credentials.shared.ReplaceSecretRequest;
import stroom.credentials.shared.UsernamePasswordSecret;
import stroom.docref.DocRef;
import stroom.resource.api.ResourceStore;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.io.PathCreator;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestCredentialsService {

    private static final String UUID = "6f3c8a52-1d47-4b8e-9f0a-2c5d7e9b1a34";

    @Mock
    private CredentialsDao credentialsDao;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private DocumentPermissionService documentPermissionService;
    @Mock
    private ResourceStore resourceStore;
    @Mock
    private PathCreator pathCreator;

    private CredentialsService credentialsService;

    @BeforeEach
    void setUp() {
        credentialsService = new CredentialsService(
                credentialsDao,
                securityContext,
                () -> documentPermissionService,
                () -> resourceStore,
                pathCreator,
                CredentialsConfig::new);
        when(securityContext.hasAppPermission(AppPermission.CREDENTIALS)).thenReturn(true);
    }

    @Test
    void testDeleteRemovesTheCredentialsPermissions() {
        when(securityContext.hasDocumentPermission(any(DocRef.class), eq(DocumentPermission.DELETE)))
                .thenReturn(true);

        credentialsService.deleteCredentialsAndSecret(UUID);

        verify(credentialsDao).deleteCredentialsAndSecret(UUID);
        // Creating it made its creator the owner, so a delete that left that row behind would leave
        // a permission on a credential that no longer exists.
        final ArgumentCaptor<DocRef> removed = ArgumentCaptor.forClass(DocRef.class);
        verify(documentPermissionService).removeAllDocumentPermissions(removed.capture());
        assertThat(removed.getValue().getType())
                .isEqualTo(Credential.TYPE);
        assertThat(removed.getValue().getUuid())
                .isEqualTo(UUID);
    }

    @Test
    void testDeleteWithoutPermissionRemovesNothing() {
        assertThatThrownBy(() -> credentialsService.deleteCredentialsAndSecret(UUID))
                .isInstanceOf(PermissionException.class);

        verify(credentialsDao, never()).deleteCredentialsAndSecret(any());
        verify(documentPermissionService, never()).removeAllDocumentPermissions(any(DocRef.class));
    }

    @Test
    void testFailedDeleteKeepsThePermissions() {
        when(securityContext.hasDocumentPermission(any(DocRef.class), eq(DocumentPermission.DELETE)))
                .thenReturn(true);
        doThrow(new RuntimeException("database unavailable"))
                .when(credentialsDao).deleteCredentialsAndSecret(UUID);

        assertThatThrownBy(() -> credentialsService.deleteCredentialsAndSecret(UUID))
                .hasMessage("database unavailable");

        verify(documentPermissionService, never()).removeAllDocumentPermissions(any(DocRef.class));
    }

    @Test
    void testStoringANewCredentialMakesItsCreatorTheOwner() {
        final UserRef creator = UserRef.builder().uuid("creator-uuid").subjectId("creator").build();
        when(securityContext.getUserRef()).thenReturn(creator);
        final Credential credential = new Credential(UUID,
                "a credential",
                null,
                null,
                null,
                null,
                CredentialType.USERNAME_PASSWORD,
                null,
                null);

        credentialsService.storeCredentials(new PutCredentialRequest(credential,
                new UsernamePasswordSecret("user", "password")));

        verify(credentialsDao).putStoredSecret(any(), anyBoolean());
        verify(documentPermissionService).setPermission(credential.asDocRef(), creator, DocumentPermission.OWNER);
    }

    @Test
    void testStoringANewCredentialWithoutASecretIsRejected() {
        when(credentialsDao.getCredentialByUuid(UUID)).thenReturn(null);

        assertThatThrownBy(() -> credentialsService.storeCredentials(new PutCredentialRequest(
                createCredential("a credential", CredentialType.USERNAME_PASSWORD, null),
                null)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(credentialsDao, never()).putStoredSecret(any(), anyBoolean());
    }

    @Test
    void testStoringANewCredentialWithTheWrongSecretTypeIsRejected() {
        when(credentialsDao.getCredentialByUuid(UUID)).thenReturn(null);

        assertThatThrownBy(() -> credentialsService.storeCredentials(new PutCredentialRequest(
                createCredential("a credential", CredentialType.ACCESS_TOKEN, null),
                new UsernamePasswordSecret("user", "password"))))
                .isInstanceOf(IllegalArgumentException.class);

        verify(credentialsDao, never()).putStoredSecret(any(), anyBoolean());
    }

    /// gh-5815: changing the name or expiry of existing credentials must not need the secret
    /// re-entered, and must not overwrite the stored secret.
    @Test
    void testUpdatingAnExistingCredentialKeepsItsSecret() {
        allowEdit();
        when(securityContext.getUserIdentityForAudit()).thenReturn("editor");
        when(credentialsDao.getCredentialByUuid(UUID)).thenReturn(createStoredCredential());
        when(credentialsDao.updateCredential(any())).thenReturn(true);

        credentialsService.storeCredentials(new PutCredentialRequest(
                createCredential("new name", CredentialType.USERNAME_PASSWORD, 2_000L),
                null));

        verify(credentialsDao, never()).putStoredSecret(any(), anyBoolean());
        verify(credentialsDao, never()).updateSecret(any());
        final ArgumentCaptor<Credential> updated = ArgumentCaptor.forClass(Credential.class);
        verify(credentialsDao).updateCredential(updated.capture());
        assertThat(updated.getValue().getName())
                .isEqualTo("new name");
        assertThat(updated.getValue().getExpiryTimeMs())
                .isEqualTo(2_000L);
        assertThat(updated.getValue().getCreateUser())
                .isEqualTo("creator");
        assertThat(updated.getValue().getUpdateUser())
                .isEqualTo("editor");
        verify(documentPermissionService, never()).setPermission(any(), any(), any());
    }

    @Test
    void testUpdatingAnExistingCredentialIgnoresAnySecretSent() {
        allowEdit();
        when(credentialsDao.getCredentialByUuid(UUID)).thenReturn(createStoredCredential());
        when(credentialsDao.updateCredential(any())).thenReturn(true);

        credentialsService.storeCredentials(new PutCredentialRequest(
                createCredential("a credential", CredentialType.USERNAME_PASSWORD, null),
                new UsernamePasswordSecret("user", "not stored")));

        verify(credentialsDao, never()).putStoredSecret(any(), anyBoolean());
        verify(credentialsDao, never()).updateSecret(any());
        verify(credentialsDao).updateCredential(any());
    }

    @Test
    void testUpdatingACredentialDeletedMeanwhileIsRejected() {
        allowEdit();
        when(credentialsDao.getCredentialByUuid(UUID)).thenReturn(createStoredCredential());
        when(credentialsDao.updateCredential(any())).thenReturn(false);

        assertThatThrownBy(() -> credentialsService.storeCredentials(new PutCredentialRequest(
                createCredential("a credential", CredentialType.USERNAME_PASSWORD, null),
                null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void testChangingTheTypeOfAnExistingCredentialIsRejected() {
        allowEdit();
        when(credentialsDao.getCredentialByUuid(UUID)).thenReturn(createStoredCredential());

        assertThatThrownBy(() -> credentialsService.storeCredentials(new PutCredentialRequest(
                createCredential("a credential", CredentialType.ACCESS_TOKEN, null),
                null)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(credentialsDao, never()).updateCredential(any());
    }

    @Test
    void testUpdatingWithoutEditPermissionIsRejected() {
        when(credentialsDao.getCredentialByUuid(UUID)).thenReturn(createStoredCredential());

        assertThatThrownBy(() -> credentialsService.storeCredentials(new PutCredentialRequest(
                createCredential("new name", CredentialType.USERNAME_PASSWORD, null),
                null)))
                .isInstanceOf(PermissionException.class);

        verify(credentialsDao, never()).updateCredential(any());
    }

    /// A new credential with the name of an existing one must be rejected, not stored over the
    /// existing one (which would hand its secret to the new owner).
    @Test
    void testCreatingACredentialWithATakenNameIsRejected() {
        when(credentialsDao.getCredentialByUuid(UUID)).thenReturn(null);
        when(credentialsDao.getCredentialByName("taken")).thenReturn(createOtherCredential("taken"));

        assertThatThrownBy(() -> credentialsService.storeCredentials(new PutCredentialRequest(
                createCredential("taken", CredentialType.USERNAME_PASSWORD, null),
                new UsernamePasswordSecret("user", "password"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exist");

        verify(credentialsDao, never()).putStoredSecret(any(), anyBoolean());
        verify(documentPermissionService, never()).setPermission(any(), any(), any());
    }

    @Test
    void testRenamingToATakenNameIsRejected() {
        allowEdit();
        when(credentialsDao.getCredentialByUuid(UUID)).thenReturn(createStoredCredential());
        when(credentialsDao.getCredentialByName("taken")).thenReturn(createOtherCredential("taken"));

        assertThatThrownBy(() -> credentialsService.storeCredentials(new PutCredentialRequest(
                createCredential("taken", CredentialType.USERNAME_PASSWORD, null),
                null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exist");

        verify(credentialsDao, never()).updateCredential(any());
    }

    @Test
    void testKeepingTheSameNameIsAllowed() {
        allowEdit();
        when(credentialsDao.getCredentialByUuid(UUID)).thenReturn(createStoredCredential());
        when(credentialsDao.getCredentialByName("a credential")).thenReturn(createStoredCredential());
        when(credentialsDao.updateCredential(any())).thenReturn(true);

        credentialsService.storeCredentials(new PutCredentialRequest(
                createCredential("a credential", CredentialType.USERNAME_PASSWORD, 5_000L),
                null));

        verify(credentialsDao).updateCredential(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void testBlankNameIsRejected(final String name) {
        when(credentialsDao.getCredentialByUuid(UUID)).thenReturn(null);

        assertThatThrownBy(() -> credentialsService.storeCredentials(new PutCredentialRequest(
                createCredential(name, CredentialType.USERNAME_PASSWORD, null),
                new UsernamePasswordSecret("user", "password"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");

        verify(credentialsDao, never()).putStoredSecret(any(), anyBoolean());
    }

    @Test
    void testReplaceSecret() {
        allowEdit();
        when(securityContext.getUserIdentityForAudit()).thenReturn("editor");
        when(credentialsDao.getStoredSecretByUuid(UUID)).thenReturn(createStoredSecret());
        when(credentialsDao.updateSecret(any())).thenReturn(true);
        final Credential afterUpdate = createStoredCredential();
        when(credentialsDao.getCredentialByUuid(UUID)).thenReturn(afterUpdate);
        final UsernamePasswordSecret newSecret = new UsernamePasswordSecret("user", "new password");

        final Credential result = credentialsService.replaceSecret(
                new ReplaceSecretRequest(UUID, newSecret, 3_000L));

        final ArgumentCaptor<StoredSecret> stored = ArgumentCaptor.forClass(StoredSecret.class);
        verify(credentialsDao).updateSecret(stored.capture());
        verify(credentialsDao, never()).putStoredSecret(any(), anyBoolean());
        verify(credentialsDao, never()).updateCredential(any());
        assertThat(stored.getValue().secret())
                .isEqualTo(newSecret);
        // The credential keeps its identity so everything that refers to it carries on working
        assertThat(stored.getValue().credential().getUuid())
                .isEqualTo(UUID);
        assertThat(stored.getValue().credential().getUpdateUser())
                .isEqualTo("editor");
        // The expiry belongs to the new secret
        assertThat(stored.getValue().credential().getExpiryTimeMs())
                .isEqualTo(3_000L);
        assertThat(result)
                .isSameAs(afterUpdate);
    }

    @Test
    void testReplaceSecretWithoutASecretIsRejected() {
        allowEdit();
        when(credentialsDao.getStoredSecretByUuid(UUID)).thenReturn(createStoredSecret());

        assertThatThrownBy(() -> credentialsService.replaceSecret(new ReplaceSecretRequest(UUID, null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(credentialsDao, never()).updateSecret(any());
    }

    @Test
    void testReplaceSecretWithTheWrongSecretTypeIsRejected() {
        allowEdit();
        when(credentialsDao.getStoredSecretByUuid(UUID)).thenReturn(createStoredSecret());

        assertThatThrownBy(() -> credentialsService.replaceSecret(new ReplaceSecretRequest(
                UUID,
                new AccessTokenSecret("token"),
                null)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(credentialsDao, never()).updateSecret(any());
    }

    @Test
    void testReplaceSecretOfMissingCredentialIsRejected() {
        allowEdit();
        when(credentialsDao.getStoredSecretByUuid(UUID)).thenReturn(null);

        assertThatThrownBy(() -> credentialsService.replaceSecret(new ReplaceSecretRequest(
                UUID,
                new UsernamePasswordSecret("user", "password"),
                null)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(credentialsDao, never()).updateSecret(any());
    }

    @Test
    void testReplaceSecretOfACredentialDeletedMeanwhileIsRejected() {
        allowEdit();
        when(credentialsDao.getStoredSecretByUuid(UUID)).thenReturn(createStoredSecret());
        when(credentialsDao.updateSecret(any())).thenReturn(false);

        assertThatThrownBy(() -> credentialsService.replaceSecret(new ReplaceSecretRequest(
                UUID,
                new UsernamePasswordSecret("user", "password"),
                null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    /// Without permission the same error must be given whether or not the credential exists,
    /// so it can't be used to find out which credentials exist.
    @Test
    void testReplaceSecretWithoutEditPermissionIsRejectedBeforeLookup() {
        assertThatThrownBy(() -> credentialsService.replaceSecret(new ReplaceSecretRequest(
                UUID,
                new UsernamePasswordSecret("user", "password"),
                null)))
                .isInstanceOf(PermissionException.class);

        verify(credentialsDao, never()).getStoredSecretByUuid(any());
        verify(credentialsDao, never()).getCredentialByUuid(any());
        verify(credentialsDao, never()).updateSecret(any());
    }

    /// The replaced key store must not stay in the key store cache.
    @Test
    void testReplacingAKeyStoreDeletesTheCachedCopy(@TempDir final Path tempDir) throws Exception {
        allowEdit();
        final Path cacheDir = Files.createDirectories(tempDir.resolve("cache"));
        when(pathCreator.toAppPath(any())).thenReturn(cacheDir);
        final String password = "changeit";

        // The old key store, as cached by getKeyStore()
        final KeyStoreSecret oldSecret = new KeyStoreSecret("old-uuid", KeyStoreType.PKCS12, password, null);
        final Path oldCachedFile = cacheDir.resolve("old-uuid.p12");
        writeEmptyKeyStore(oldCachedFile, password);
        // An unrelated cached key store that must be left alone
        final Path otherCachedFile = cacheDir.resolve("other-uuid.p12");
        writeEmptyKeyStore(otherCachedFile, password);

        when(credentialsDao.getStoredSecretByUuid(UUID)).thenReturn(new StoredSecret(
                createStoredCredential(CredentialType.KEY_STORE, KeyStoreType.PKCS12),
                oldSecret,
                Files.readAllBytes(oldCachedFile)));
        when(credentialsDao.updateSecret(any())).thenReturn(true);

        // The new key store, as uploaded
        final ResourceKey resourceKey = new ResourceKey("upload-key", "new.p12");
        final Path upload = tempDir.resolve("upload.p12");
        writeEmptyKeyStore(upload, password);
        when(resourceStore.getTempFile(resourceKey)).thenReturn(upload);

        credentialsService.replaceSecret(new ReplaceSecretRequest(UUID,
                new KeyStoreSecret("new-uuid", KeyStoreType.PKCS12, password, resourceKey),
                null));

        assertThat(oldCachedFile).doesNotExist();
        assertThat(otherCachedFile).exists();
        final ArgumentCaptor<StoredSecret> stored = ArgumentCaptor.forClass(StoredSecret.class);
        verify(credentialsDao).updateSecret(stored.capture());
        assertThat(stored.getValue().keyStore())
                .isEqualTo(Files.readAllBytes(upload));
        assertThat(stored.getValue().credential().getKeyStoreType())
                .isEqualTo(KeyStoreType.PKCS12);
    }

    private static void writeEmptyKeyStore(final Path path, final String password) throws Exception {
        final java.security.KeyStore keyStore = java.security.KeyStore.getInstance("PKCS12");
        keyStore.load(null, password.toCharArray());
        try (final OutputStream outputStream = Files.newOutputStream(path)) {
            keyStore.store(outputStream, password.toCharArray());
        }
    }

    private void allowEdit() {
        when(securityContext.hasDocumentPermission(any(DocRef.class), eq(DocumentPermission.EDIT)))
                .thenReturn(true);
    }

    private Credential createCredential(final String name,
                                        final CredentialType credentialType,
                                        final Long expiryTimeMs) {
        return new Credential(UUID,
                name,
                null,
                null,
                null,
                null,
                credentialType,
                null,
                expiryTimeMs);
    }

    private Credential createStoredCredential() {
        return createStoredCredential(CredentialType.USERNAME_PASSWORD, null);
    }

    private Credential createStoredCredential(final CredentialType credentialType,
                                              final KeyStoreType keyStoreType) {
        return new Credential(UUID,
                "a credential",
                1_000L,
                1_000L,
                "creator",
                "creator",
                credentialType,
                keyStoreType,
                null);
    }

    private StoredSecret createStoredSecret() {
        return new StoredSecret(createStoredCredential(),
                new UsernamePasswordSecret("user", "old password"),
                null);
    }

    private Credential createOtherCredential(final String name) {
        return new Credential("another-uuid",
                name,
                1_000L,
                1_000L,
                "someone else",
                "someone else",
                CredentialType.USERNAME_PASSWORD,
                null,
                null);
    }
}
