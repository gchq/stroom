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

import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.credentials.shared.PutCredentialRequest;
import stroom.credentials.shared.UsernamePasswordSecret;
import stroom.docref.DocRef;
import stroom.resource.api.ResourceStore;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.io.PathCreator;
import stroom.util.shared.PermissionException;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
