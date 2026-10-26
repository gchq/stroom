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
import stroom.cluster.lock.api.ClusterLockService;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.credentials.shared.KeyStoreFileSecret;
import stroom.credentials.shared.PutCredentialRequest;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserService;
import stroom.security.shared.User;
import stroom.util.shared.UserRef;
import stroom.util.shared.UserType;

import event.logging.CreateEventAction;
import event.logging.OtherObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestKeyStoreFileCredentialsBootstrap {

    private static final String PATH_1 = "/etc/stroom/certs/one.p12";
    private static final String PATH_2 = "/etc/stroom/certs/two.jks";
    private static final UserRef PROCESSING_USER = UserRef.builder()
            .uuid("processing-uuid")
            .subjectId("processing")
            .build();
    private static final UserRef ADMINISTRATORS = UserRef.builder()
            .uuid("administrators-uuid")
            .subjectId(User.ADMINISTRATORS_GROUP_SUBJECT_ID)
            .displayName(User.ADMINISTRATORS_GROUP_SUBJECT_ID)
            .group(true)
            .build();

    @Mock
    private CredentialsService credentialsService;
    @Mock
    private UserService userService;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private ClusterLockService clusterLockService;
    @Mock
    private StroomEventLoggingService stroomEventLoggingService;

    // The user the mocked security context is running as
    private final AtomicReference<UserRef> currentUser = new AtomicReference<>();
    // The user that was current when each credential was stored
    private final List<UserRef> storedAs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        lenient().doAnswer(invocation -> {
            runAs(PROCESSING_USER, invocation.getArgument(0));
            return null;
        }).when(securityContext).asProcessingUser(any(Runnable.class));
        lenient().doAnswer(invocation -> {
            runAs(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(securityContext).asUser(any(UserRef.class), any(Runnable.class));
        lenient().doAnswer(invocation -> {
            final Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        }).when(clusterLockService).lock(anyString(), any(Runnable.class));
        lenient().doAnswer(invocation -> {
            storedAs.add(currentUser.get());
            return null;
        }).when(credentialsService).storeCredentials(any());
        lenient().when(userService.getGroupByName(User.ADMINISTRATORS_GROUP_SUBJECT_ID))
                .thenReturn(Optional.of(user(ADMINISTRATORS)));
    }

    @Test
    void testNothingIsDoneWithNoKeyStoreFiles() {
        bootstrap(config(List.of())).startup();

        verifyNoInteractions(securityContext, clusterLockService, credentialsService, stroomEventLoggingService);
    }

    @Test
    void testMissingCredentialIsCreatedAsTheOwner() {
        bootstrap(config(List.of(
                new KeyStoreFileCredentialConfig("keystore 1", KeyStoreType.PKCS12, PATH_1, "pass phrase"))))
                .startup();

        final ArgumentCaptor<PutCredentialRequest> requestCaptor = ArgumentCaptor.forClass(PutCredentialRequest.class);
        verify(credentialsService).storeCredentials(requestCaptor.capture());
        final Credential credential = requestCaptor.getValue().getCredential();
        assertThat(credential.getName()).isEqualTo("keystore 1");
        assertThat(credential.getCredentialType()).isEqualTo(CredentialType.KEY_STORE_FILE);
        assertThat(credential.getUuid()).isNotBlank();
        assertThat(requestCaptor.getValue().getSecret()).isEqualTo(
                new KeyStoreFileSecret(KeyStoreType.PKCS12, PATH_1, "pass phrase"));

        // Stored as the owner, so the owner is made its OWNER and recorded as creating it
        assertThat(storedAs).containsExactly(ADMINISTRATORS);
        verify(clusterLockService).lock(anyString(), any(Runnable.class));

        final CreateEventAction event = captureCreateEvent();
        assertThat(event.getOutcome().isSuccess()).isTrue();
        final OtherObject object = (OtherObject) event.getObjects().getFirst();
        assertThat(object.getType()).isEqualTo(Credential.TYPE);
        assertThat(object.getId()).isEqualTo(credential.getUuid());
        assertThat(object.getName()).isEqualTo("keystore 1");
    }

    @Test
    void testEventIsLoggedAsTheProcessingUser() {
        final List<UserRef> loggedAs = new ArrayList<>();
        doAnswer(invocation -> {
            loggedAs.add(currentUser.get());
            return null;
        }).when(stroomEventLoggingService).log(anyString(), anyString(), any(CreateEventAction.class));

        bootstrap(config(List.of(new KeyStoreFileCredentialConfig("keystore 1", null, PATH_1, null)))).startup();

        assertThat(loggedAs).containsExactly(PROCESSING_USER);
    }

    @Test
    void testExistingCredentialIsNotChanged() {
        when(credentialsService.getCredentialByName("keystore 1"))
                .thenReturn(keyStoreFileCredential("keystore 1", KeyStoreType.PKCS12, PATH_1));

        bootstrap(config(List.of(new KeyStoreFileCredentialConfig("keystore 1", null, PATH_1, null)))).startup();

        verify(credentialsService, never()).storeCredentials(any());
        verifyNoInteractions(stroomEventLoggingService);
    }

    @Test
    void testExistingCredentialWithADifferentPathIsNotChanged() {
        when(credentialsService.getCredentialByName("keystore 1"))
                .thenReturn(keyStoreFileCredential("keystore 1", KeyStoreType.PKCS12, PATH_2));

        bootstrap(config(List.of(new KeyStoreFileCredentialConfig("keystore 1", null, PATH_1, null)))).startup();

        verify(credentialsService, never()).storeCredentials(any());
        verifyNoInteractions(stroomEventLoggingService);
    }

    @Test
    void testExistingCredentialOfAnotherTypeIsNotChanged() {
        when(credentialsService.getCredentialByName("keystore 1")).thenReturn(new Credential("other-uuid",
                "keystore 1",
                null,
                null,
                null,
                null,
                CredentialType.USERNAME_PASSWORD,
                null,
                null));

        bootstrap(config(List.of(new KeyStoreFileCredentialConfig("keystore 1", null, PATH_1, null)))).startup();

        verify(credentialsService, never()).storeCredentials(any());
        verifyNoInteractions(stroomEventLoggingService);
    }

    @Test
    void testExistingCredentialIsLookedUpAsTheProcessingUser() {
        final List<UserRef> lookedUpAs = new ArrayList<>();
        when(credentialsService.getCredentialByName("keystore 1")).thenAnswer(invocation -> {
            lookedUpAs.add(currentUser.get());
            return null;
        });

        bootstrap(config(List.of(new KeyStoreFileCredentialConfig("keystore 1", null, PATH_1, null)))).startup();

        // So that a credential the owner can't see is still found, and never taken over
        assertThat(lookedUpAs).containsExactly(PROCESSING_USER);
    }

    @Test
    void testFailedEntryIsLoggedAndTheRestAreStillCreated() {
        doThrow(new IllegalArgumentException("The file does not exist"))
                .doAnswer(invocation -> null)
                .when(credentialsService).storeCredentials(any());

        bootstrap(config(List.of(
                new KeyStoreFileCredentialConfig("keystore 1", null, PATH_1, null),
                new KeyStoreFileCredentialConfig("keystore 2", KeyStoreType.JKS, PATH_2, null))))
                .startup();

        verify(credentialsService, times(2)).storeCredentials(any());
        final ArgumentCaptor<CreateEventAction> eventCaptor = ArgumentCaptor.forClass(CreateEventAction.class);
        verify(stroomEventLoggingService, times(2)).log(anyString(), anyString(), eventCaptor.capture());
        final CreateEventAction failed = eventCaptor.getAllValues().get(0);
        assertThat(failed.getOutcome().isSuccess()).isFalse();
        assertThat(failed.getOutcome().getDescription())
                .contains("keystore 1")
                .contains("The file does not exist")
                .doesNotContain("environment variable");
        assertThat(eventCaptor.getAllValues().get(1).getOutcome().isSuccess()).isTrue();
    }

    @Test
    void testFailedAuditIsNotReportedAsAFailedCreate() {
        doThrow(new RuntimeException("Audit unavailable"))
                .doAnswer(invocation -> null)
                .when(stroomEventLoggingService).log(anyString(), anyString(), any(CreateEventAction.class));

        bootstrap(config(List.of(
                new KeyStoreFileCredentialConfig("keystore 1", null, PATH_1, null),
                new KeyStoreFileCredentialConfig("keystore 2", KeyStoreType.JKS, PATH_2, null))))
                .startup();

        // Both are created and only success events are logged, the first of which failed
        verify(credentialsService, times(2)).storeCredentials(any());
        final ArgumentCaptor<CreateEventAction> eventCaptor = ArgumentCaptor.forClass(CreateEventAction.class);
        verify(stroomEventLoggingService, times(2)).log(anyString(), anyString(), eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .allSatisfy(event -> assertThat(event.getOutcome().isSuccess()).isTrue());
    }

    @Test
    void testLockFailureDoesNotEscape() {
        doThrow(new RuntimeException("Timed out waiting for lock"))
                .when(clusterLockService).lock(anyString(), any(Runnable.class));

        // Must not throw
        bootstrap(config(List.of(new KeyStoreFileCredentialConfig("keystore 1", null, PATH_1, null)))).startup();

        verifyNoInteractions(credentialsService, stroomEventLoggingService);
    }

    @Test
    void testOwnerLookupFailureDoesNotEscape() {
        when(userService.getGroupByName(User.ADMINISTRATORS_GROUP_SUBJECT_ID))
                .thenThrow(new RuntimeException("Database unavailable"));

        // Must not throw
        bootstrap(config(List.of(new KeyStoreFileCredentialConfig("keystore 1", null, PATH_1, null)))).startup();

        verifyNoInteractions(clusterLockService, credentialsService, stroomEventLoggingService);
    }

    @Test
    void testUnsetEnvironmentVariableIsMentioned() {
        doThrow(new IllegalArgumentException("Unable to read the key store"))
                .when(credentialsService).storeCredentials(any());

        bootstrap(config(List.of(
                new KeyStoreFileCredentialConfig("keystore 1", null, PATH_1, "${KEY_STORE_PASSWORD}"))))
                .startup();

        assertThat(captureCreateEvent().getOutcome().getDescription())
                .contains("environment variable that is not set")
                // The pass phrase itself is never logged
                .doesNotContain("${KEY_STORE_PASSWORD}");
    }

    @Test
    void testNothingIsCreatedIfTheOwnerIsMissing() {
        when(userService.getGroupByName("Missing Group")).thenReturn(Optional.empty());

        bootstrap(config(List.of(new KeyStoreFileCredentialConfig("keystore 1", null, PATH_1, null)),
                "Missing Group",
                UserType.GROUP))
                .startup();

        verifyNoInteractions(clusterLockService, credentialsService, stroomEventLoggingService);
    }

    @Test
    void testNothingIsCreatedIfTheOwnerIsBlank() {
        bootstrap(config(List.of(new KeyStoreFileCredentialConfig("keystore 1", null, PATH_1, null)),
                " ",
                UserType.GROUP))
                .startup();

        verifyNoInteractions(clusterLockService, credentialsService, stroomEventLoggingService);
    }

    @Test
    void testOwnerCanBeAUser() {
        final UserRef jbloggs = UserRef.builder().uuid("jbloggs-uuid").subjectId("jbloggs").build();
        when(userService.getUserBySubjectId("jbloggs")).thenReturn(Optional.of(user(jbloggs)));

        bootstrap(config(List.of(new KeyStoreFileCredentialConfig("keystore 1", null, PATH_1, null)),
                "jbloggs",
                UserType.USER))
                .startup();

        assertThat(storedAs).containsExactly(jbloggs);
        verify(userService, never()).getGroupByName(anyString());
    }

    @Test
    void testOwnerDefaultsToTheAdministratorsGroup() {
        final CredentialsConfig config = config(
                List.of(new KeyStoreFileCredentialConfig("keystore 1", null, PATH_1, null)), null, null);
        assertThat(config.getKeyStoreFilesOwnerSubjectId()).isEqualTo(User.ADMINISTRATORS_GROUP_SUBJECT_ID);
        assertThat(config.getKeyStoreFilesOwnerType()).isEqualTo(UserType.GROUP);

        bootstrap(config).startup();

        assertThat(storedAs).containsExactly(ADMINISTRATORS);
        verify(stroomEventLoggingService).log(anyString(), anyString(), any(CreateEventAction.class));
        verify(credentialsService).storeCredentials(any());
        verify(userService).getGroupByName(eq(User.ADMINISTRATORS_GROUP_SUBJECT_ID));
    }

    private void runAs(final UserRef userRef, final Runnable runnable) {
        final UserRef previous = currentUser.getAndSet(userRef);
        try {
            runnable.run();
        } finally {
            currentUser.set(previous);
        }
    }

    private CreateEventAction captureCreateEvent() {
        final ArgumentCaptor<CreateEventAction> eventCaptor = ArgumentCaptor.forClass(CreateEventAction.class);
        verify(stroomEventLoggingService).log(anyString(), anyString(), eventCaptor.capture());
        return eventCaptor.getValue();
    }

    private KeyStoreFileCredentialsBootstrap bootstrap(final CredentialsConfig config) {
        final Supplier<CredentialsConfig> configSupplier = () -> config;
        return new KeyStoreFileCredentialsBootstrap(configSupplier::get,
                credentialsService,
                userService,
                securityContext,
                clusterLockService,
                stroomEventLoggingService);
    }

    private static CredentialsConfig config(final List<KeyStoreFileCredentialConfig> keyStoreFiles) {
        return config(keyStoreFiles, User.ADMINISTRATORS_GROUP_SUBJECT_ID, UserType.GROUP);
    }

    private static CredentialsConfig config(final List<KeyStoreFileCredentialConfig> keyStoreFiles,
                                            final String ownerSubjectId,
                                            final UserType ownerType) {
        return new CredentialsConfig(null, null, null, keyStoreFiles, ownerSubjectId, ownerType);
    }

    private static Credential keyStoreFileCredential(final String name,
                                                     final KeyStoreType keyStoreType,
                                                     final String path) {
        return new Credential("existing-uuid",
                name,
                null,
                null,
                null,
                null,
                CredentialType.KEY_STORE_FILE,
                keyStoreType,
                path,
                null);
    }

    private static User user(final UserRef userRef) {
        return User.builder()
                .uuid(userRef.getUuid())
                .subjectId(userRef.getSubjectId())
                .displayName(userRef.getDisplayName())
                .group(userRef.isGroup())
                .build();
    }
}
