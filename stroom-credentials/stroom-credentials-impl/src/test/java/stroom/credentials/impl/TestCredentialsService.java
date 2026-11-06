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

import stroom.credentials.api.StoredSecret;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.credentials.shared.CredentialWithPerms;
import stroom.credentials.shared.FindCredentialRequest;
import stroom.credentials.shared.PutCredentialRequest;
import stroom.credentials.shared.Secret;
import stroom.credentials.shared.UsernamePasswordSecret;
import stroom.docref.DocRef;
import stroom.resource.api.ResourceStore;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.io.PathCreator;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import jakarta.inject.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Function;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestCredentialsService {

    private static final String CREDENTIAL_UUID = "credential-uuid";
    private static final String CREDENTIAL_NAME = "credential-name";

    @Mock
    private CredentialsDao credentialsDao;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Provider<DocumentPermissionService> permissionServiceProvider;

    @Mock
    private DocumentPermissionService documentPermissionService;

    @Mock
    private Provider<ResourceStore> resourceStoreProvider;

    @Mock
    private PathCreator pathCreator;

    @Mock
    private Provider<CredentialsConfig> credentialsConfigProvider;

    @Mock
    private UserRef userRef;

    private CredentialsService credentialsService;

    /// Creates the service with mocked collaborators for each test.
    @BeforeEach
    void beforeEach() {
        credentialsService = new CredentialsService(
                credentialsDao,
                securityContext,
                permissionServiceProvider,
                resourceStoreProvider,
                pathCreator,
                credentialsConfigProvider);
    }

    /// Checks the required permission and decorates permitted credentials with edit and delete permissions.
    @Test
    void findCredentialsWithPermissionsChecksRequiredEditAndDeletePermissions() {
        final Credential permittedCredential = createCredential(CREDENTIAL_UUID, CREDENTIAL_NAME);
        final Credential deniedCredential = createCredential("denied-uuid", "denied-name");
        final FindCredentialRequest request = createFindRequest(DocumentPermission.VIEW);

        when(securityContext.hasDocumentPermission(permittedCredential.asDocRef(), DocumentPermission.VIEW))
                .thenReturn(true);
        when(securityContext.hasDocumentPermission(permittedCredential.asDocRef(), DocumentPermission.EDIT))
                .thenReturn(true);
        when(securityContext.hasDocumentPermission(permittedCredential.asDocRef(), DocumentPermission.DELETE))
                .thenReturn(false);
        when(securityContext.hasDocumentPermission(deniedCredential.asDocRef(), DocumentPermission.VIEW))
                .thenReturn(false);

        when(credentialsDao.findCredentialsWithPermissions(eq(request), any()))
                .thenAnswer(invocation -> {
                    final Function<Credential, CredentialWithPerms> permissionDecorator = invocation.getArgument(1);
                    final CredentialWithPerms permittedResult = permissionDecorator.apply(permittedCredential);
                    final CredentialWithPerms deniedResult = permissionDecorator.apply(deniedCredential);

                    assertThat(permittedResult).isNotNull();
                    assertThat(permittedResult.getCredential()).isEqualTo(permittedCredential);
                    assertThat(permittedResult.isEdit()).isTrue();
                    assertThat(permittedResult.isDelete()).isFalse();
                    assertThat(deniedResult).isNull();
                    return ResultPage.empty();
                });

        credentialsService.findCredentialsWithPermissions(request);

        verify(securityContext).hasDocumentPermission(permittedCredential.asDocRef(), DocumentPermission.VIEW);
        verify(securityContext).hasDocumentPermission(permittedCredential.asDocRef(), DocumentPermission.EDIT);
        verify(securityContext).hasDocumentPermission(permittedCredential.asDocRef(), DocumentPermission.DELETE);
        verify(securityContext).hasDocumentPermission(deniedCredential.asDocRef(), DocumentPermission.VIEW);
        verify(securityContext, never())
                .hasDocumentPermission(deniedCredential.asDocRef(), DocumentPermission.EDIT);
        verify(securityContext, never())
                .hasDocumentPermission(deniedCredential.asDocRef(), DocumentPermission.DELETE);
    }

    /// Checks that the required permission is applied to every credential returned by the DAO.
    @Test
    void findCredentialsFiltersCredentialsWithoutRequiredPermission() {
        final Credential permittedCredential = createCredential(CREDENTIAL_UUID, CREDENTIAL_NAME);
        final Credential deniedCredential = createCredential("denied-uuid", "denied-name");
        final FindCredentialRequest request = createFindRequest(DocumentPermission.EDIT);

        when(securityContext.hasDocumentPermission(permittedCredential.asDocRef(), DocumentPermission.EDIT))
                .thenReturn(true);
        when(securityContext.hasDocumentPermission(deniedCredential.asDocRef(), DocumentPermission.EDIT))
                .thenReturn(false);
        when(credentialsDao.findCredentials(eq(request), any()))
                .thenAnswer(invocation -> {
                    final Predicate<Credential> permissionFilter = invocation.getArgument(1);
                    assertThat(permissionFilter.test(permittedCredential)).isTrue();
                    assertThat(permissionFilter.test(deniedCredential)).isFalse();
                    return ResultPage.empty();
                });

        credentialsService.findCredentials(request);

        verify(securityContext).hasDocumentPermission(permittedCredential.asDocRef(), DocumentPermission.EDIT);
        verify(securityContext).hasDocumentPermission(deniedCredential.asDocRef(), DocumentPermission.EDIT);
    }

    /// Prevents storing credentials when the user lacks the credentials application permission.
    @Test
    void storeCredentialsRejectsUserWithoutAppPermission() {
        final PutCredentialRequest request = createPutRequest();
        when(securityContext.hasAppPermission(AppPermission.CREDENTIALS)).thenReturn(false);

        assertThatThrownBy(() -> credentialsService.storeCredentials(request))
                .isInstanceOf(PermissionException.class)
                .hasMessageContaining("permission to use credentials");

        verify(securityContext).hasAppPermission(AppPermission.CREDENTIALS);
        verifyNoInteractions(credentialsDao, permissionServiceProvider);
    }

    /// Stores a new credential when the application permission is present and assigns ownership to the user.
    @Test
    void storeCredentialsCreatesCredentialAndAssignsOwnerPermission() {
        final PutCredentialRequest request = createPutRequest();
        when(securityContext.hasAppPermission(AppPermission.CREDENTIALS)).thenReturn(true);
        when(securityContext.getUserRef()).thenReturn(userRef);
        when(credentialsDao.getCredentialByUuid(CREDENTIAL_UUID)).thenReturn(null);
        when(permissionServiceProvider.get()).thenReturn(documentPermissionService);

        credentialsService.storeCredentials(request);

        final ArgumentCaptor<StoredSecret> storedSecretCaptor = ArgumentCaptor.forClass(StoredSecret.class);
        verify(credentialsDao).putStoredSecret(storedSecretCaptor.capture(), eq(false));
        assertThat(storedSecretCaptor.getValue().credential()).isEqualTo(request.getCredential());
        assertThat(storedSecretCaptor.getValue().secret()).isEqualTo(request.getSecret());
        verify(documentPermissionService).setPermission(
                request.getCredential().asDocRef(),
                userRef,
                DocumentPermission.OWNER);
    }

    /// Prevents updating an existing credential when the user lacks edit permission.
    @Test
    void storeCredentialsRejectsUpdateWithoutEditPermission() {
        final PutCredentialRequest request = createPutRequest();
        final Credential existingCredential = createCredential(CREDENTIAL_UUID, "existing-name");
        final DocRef credentialDocRef = Credential.getDocRef(CREDENTIAL_UUID);
        when(securityContext.hasAppPermission(AppPermission.CREDENTIALS)).thenReturn(true);
        when(credentialsDao.getCredentialByUuid(CREDENTIAL_UUID)).thenReturn(existingCredential);
        when(securityContext.hasDocumentPermission(credentialDocRef, DocumentPermission.EDIT)).thenReturn(false);

        assertThatThrownBy(() -> credentialsService.storeCredentials(request))
                .isInstanceOf(PermissionException.class)
                .hasMessageContaining("Edit permission")
                .hasMessageContaining(CREDENTIAL_UUID);

        verify(securityContext).hasDocumentPermission(credentialDocRef, DocumentPermission.EDIT);
        verify(credentialsDao, never()).putStoredSecret(any(), anyBoolean());
        verify(permissionServiceProvider, never()).get();
    }

    /// Updates an existing credential when the user has edit permission.
    @Test
    void storeCredentialsUpdatesExistingCredentialWithEditPermission() {
        final PutCredentialRequest request = createPutRequest();
        final Credential existingCredential = createCredential(CREDENTIAL_UUID, "existing-name");
        final DocRef credentialDocRef = Credential.getDocRef(CREDENTIAL_UUID);
        when(securityContext.hasAppPermission(AppPermission.CREDENTIALS)).thenReturn(true);
        when(credentialsDao.getCredentialByUuid(CREDENTIAL_UUID)).thenReturn(existingCredential);
        when(securityContext.hasDocumentPermission(credentialDocRef, DocumentPermission.EDIT)).thenReturn(true);

        credentialsService.storeCredentials(request);

        verify(credentialsDao).putStoredSecret(any(StoredSecret.class), eq(true));
        verify(permissionServiceProvider, never()).get();
    }

    /// Prevents reading a credential by UUID when the user lacks use permission.
    @Test
    void getCredentialByUuidRejectsUserWithoutUsePermission() {
        final DocRef credentialDocRef = Credential.getDocRef(CREDENTIAL_UUID);
        when(securityContext.hasDocumentPermission(credentialDocRef, DocumentPermission.USE)).thenReturn(false);

        assertThatThrownBy(() -> credentialsService.getCredentialByUuid(CREDENTIAL_UUID))
                .isInstanceOf(PermissionException.class)
                .hasMessageContaining("Use permission")
                .hasMessageContaining(CREDENTIAL_UUID);

        verify(credentialsDao, never()).getCredentialByUuid(any());
    }

    /// Returns a credential by UUID when the user has use permission.
    @Test
    void getCredentialByUuidReturnsCredentialWithUsePermission() {
        final Credential credential = createCredential(CREDENTIAL_UUID, CREDENTIAL_NAME);
        final DocRef credentialDocRef = Credential.getDocRef(CREDENTIAL_UUID);
        when(securityContext.hasDocumentPermission(credentialDocRef, DocumentPermission.USE)).thenReturn(true);
        when(credentialsDao.getCredentialByUuid(CREDENTIAL_UUID)).thenReturn(credential);

        assertThat(credentialsService.getCredentialByUuid(CREDENTIAL_UUID)).isSameAs(credential);

        verify(securityContext).hasDocumentPermission(credentialDocRef, DocumentPermission.USE);
        verify(credentialsDao).getCredentialByUuid(CREDENTIAL_UUID);
    }

    /// Prevents reading a credential by name when the user lacks use permission.
    @Test
    void getCredentialByNameRejectsUserWithoutUsePermission() {
        final Credential credential = createCredential(CREDENTIAL_UUID, CREDENTIAL_NAME);
        when(credentialsDao.getCredentialByName(CREDENTIAL_NAME)).thenReturn(credential);
        when(securityContext.hasDocumentPermission(credential.asDocRef(), DocumentPermission.USE)).thenReturn(false);

        assertThatThrownBy(() -> credentialsService.getCredentialByName(CREDENTIAL_NAME))
                .isInstanceOf(PermissionException.class)
                .hasMessageContaining("Use permission")
                .hasMessageContaining(CREDENTIAL_UUID);

        verify(credentialsDao).getCredentialByName(CREDENTIAL_NAME);
    }

    /// Returns a credential by name when the user has use permission.
    @Test
    void getCredentialByNameReturnsCredentialWithUsePermission() {
        final Credential credential = createCredential(CREDENTIAL_UUID, CREDENTIAL_NAME);
        when(credentialsDao.getCredentialByName(CREDENTIAL_NAME)).thenReturn(credential);
        when(securityContext.hasDocumentPermission(credential.asDocRef(), DocumentPermission.USE)).thenReturn(true);

        assertThat(credentialsService.getCredentialByName(CREDENTIAL_NAME)).isSameAs(credential);

        verify(securityContext).hasDocumentPermission(credential.asDocRef(), DocumentPermission.USE);
    }

    /// Prevents deletion when the user lacks the credentials application permission.
    @Test
    void deleteCredentialsRejectsUserWithoutAppPermission() {
        when(securityContext.hasAppPermission(AppPermission.CREDENTIALS)).thenReturn(false);

        assertThatThrownBy(() -> credentialsService.deleteCredentialsAndSecret(CREDENTIAL_UUID))
                .isInstanceOf(PermissionException.class)
                .hasMessageContaining("permission to use credentials");

        verify(securityContext, never()).hasDocumentPermission(any(), any());
        verify(credentialsDao, never()).deleteCredentialsAndSecret(any());
    }

    /// Prevents deletion when the user lacks delete permission on the credential.
    @Test
    void deleteCredentialsRejectsUserWithoutDeletePermission() {
        final DocRef credentialDocRef = Credential.getDocRef(CREDENTIAL_UUID);
        when(securityContext.hasAppPermission(AppPermission.CREDENTIALS)).thenReturn(true);
        when(securityContext.hasDocumentPermission(credentialDocRef, DocumentPermission.DELETE)).thenReturn(false);

        assertThatThrownBy(() -> credentialsService.deleteCredentialsAndSecret(CREDENTIAL_UUID))
                .isInstanceOf(PermissionException.class)
                .hasMessageContaining("Delete permission")
                .hasMessageContaining(CREDENTIAL_UUID);

        verify(credentialsDao, never()).deleteCredentialsAndSecret(any());
    }

    /// Deletes a credential when both application and document permissions are present.
    @Test
    void deleteCredentialsDeletesWithAppAndDeletePermissions() {
        final DocRef credentialDocRef = Credential.getDocRef(CREDENTIAL_UUID);
        when(securityContext.hasAppPermission(AppPermission.CREDENTIALS)).thenReturn(true);
        when(securityContext.hasDocumentPermission(credentialDocRef, DocumentPermission.DELETE)).thenReturn(true);

        credentialsService.deleteCredentialsAndSecret(CREDENTIAL_UUID);

        verify(credentialsDao).deleteCredentialsAndSecret(CREDENTIAL_UUID);
    }

    /// Prevents access to a stored secret when the user lacks use permission.
    @Test
    void getRejectsUserWithoutUsePermission() {
        final Secret secret = new UsernamePasswordSecret("username", "password");
        final StoredSecret storedSecret = new StoredSecret(
                createCredential(CREDENTIAL_UUID, CREDENTIAL_NAME),
                secret,
                null);
        when(credentialsDao.getStoredSecretByName(CREDENTIAL_NAME)).thenReturn(storedSecret);
        when(securityContext.hasDocumentPermission(storedSecret.credential().asDocRef(), DocumentPermission.USE))
                .thenReturn(false);

        assertThatThrownBy(() -> credentialsService.get(CREDENTIAL_NAME))
                .isInstanceOf(PermissionException.class)
                .hasMessageContaining("permission to view these credentials");
    }

    /// Returns a stored secret when the user has use permission.
    @Test
    void getReturnsStoredSecretWithUsePermission() {
        final Secret secret = new UsernamePasswordSecret("username", "password");
        final StoredSecret storedSecret = new StoredSecret(
                createCredential(CREDENTIAL_UUID, CREDENTIAL_NAME),
                secret,
                null);
        when(credentialsDao.getStoredSecretByName(CREDENTIAL_NAME)).thenReturn(storedSecret);
        when(securityContext.hasDocumentPermission(storedSecret.credential().asDocRef(), DocumentPermission.USE))
                .thenReturn(true);

        assertThat(credentialsService.get(CREDENTIAL_NAME)).isSameAs(storedSecret);
    }

    private FindCredentialRequest createFindRequest(final DocumentPermission requiredPermission) {
        return new FindCredentialRequest(
                null,
                null,
                null,
                null,
                requiredPermission);
    }

    private PutCredentialRequest createPutRequest() {
        final Credential credential = createCredential(CREDENTIAL_UUID, CREDENTIAL_NAME);
        final Secret secret = new UsernamePasswordSecret("username", "password");
        return new PutCredentialRequest(credential, secret);
    }

    private Credential createCredential(final String uuid, final String name) {
        return new Credential(
                uuid,
                name,
                1L,
                1L,
                "creator",
                "updater",
                CredentialType.USERNAME_PASSWORD,
                null,
                null);
    }
}
