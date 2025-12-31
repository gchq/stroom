/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.credentials.api.KeyStore;
import stroom.credentials.api.StoredSecret;
import stroom.credentials.api.StoredSecrets;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialWithPerms;
import stroom.credentials.shared.FindCredentialRequest;
import stroom.credentials.shared.KeyStoreSecret;
import stroom.credentials.shared.PutCredentialRequest;
import stroom.credentials.shared.Secret;
import stroom.docref.DocRef;
import stroom.resource.api.ResourceStore;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

public class CredentialsService implements StoredSecrets {

    private final CredentialsDao credentialsDao;
    private final SecurityContext securityContext;
    private final Provider<DocumentPermissionService> permissionServiceProvider;
    private final Provider<ResourceStore> resourceStoreProvider;
    private final PathCreator pathCreator;
    private final Provider<CredentialsConfig> credentialsConfigProvider;

    /**
     * Injected constructor.
     */
    @SuppressWarnings("unused")
    @Inject
    public CredentialsService(final CredentialsDao credentialsDao,
                              final SecurityContext securityContext,
                              final Provider<DocumentPermissionService> permissionServiceProvider,
                              final Provider<ResourceStore> resourceStoreProvider,
                              final PathCreator pathCreator,
                              final Provider<CredentialsConfig> credentialsConfigProvider) {
        this.credentialsDao = credentialsDao;
        this.securityContext = securityContext;
        this.permissionServiceProvider = permissionServiceProvider;
        this.resourceStoreProvider = resourceStoreProvider;
        this.pathCreator = pathCreator;
        this.credentialsConfigProvider = credentialsConfigProvider;
    }

    /**
     * Provides a decoration function that will add security permissions to supplied credentials.
     */
    private Function<Credential, CredentialWithPerms> createPermissionDecorator() {
        return credential -> {
            final DocRef docRef = new DocRef(Credential.TYPE, credential.getUuid());
            if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
                return null;
            }

            final boolean canEdit = securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT);
            final boolean canDelete = securityContext.hasDocumentPermission(docRef, DocumentPermission.DELETE);
            final boolean owner = securityContext.hasDocumentPermission(docRef, DocumentPermission.OWNER);
            return new CredentialWithPerms(credential,
                    canEdit || owner,
                    canDelete || owner);
        };
    }

    private Predicate<Credential> createPermissionFilter() {
        return credential -> {
            final DocRef docRef = new DocRef(Credential.TYPE, credential.getUuid());
            return securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW);
        };
    }

    /**
     * Wraps the given credentials object with UI permissions.
     *
     * @param credential The credentials to wrap. Can be null in which case null is returned.
     * @return Credentials with permissions. Null if credentials are null or user doesn't have
     * permission to view these credentials.
     */
    private CredentialWithPerms permissionWrap(final Credential credential) {
        CredentialWithPerms retval = null;
        if (credential != null) {

            final DocRef docRef = new DocRef(Credential.TYPE, credential.getUuid());
            if (securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
                final boolean canEdit = securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT);
                final boolean canDelete = securityContext.hasDocumentPermission(docRef, DocumentPermission.DELETE);
                final boolean owner = securityContext.hasDocumentPermission(docRef, DocumentPermission.OWNER);
                retval = new CredentialWithPerms(credential,
                        canEdit || owner,
                        canDelete || owner);
            }
        }

        return retval;
    }

    /**
     * Returns a list of all the credentials in the database.
     * Permissions: App, View|Edit|Owner (for all individual permissions)
     */
    public ResultPage<CredentialWithPerms> findCredentialsWithPermissions(final FindCredentialRequest request) {
        checkAppPermission();
        return credentialsDao.findCredentialsWithPermissions(request, createPermissionDecorator());
    }

    /**
     * Returns a list of all the credentials in the database.
     * Permissions: App, View|Edit|Owner (for all individual permissions)
     */
    public ResultPage<Credential> findCredentials(final FindCredentialRequest request) {
        checkAppPermission();
        return credentialsDao.findCredentials(request, createPermissionFilter());
    }

    /**
     * Stores the given credential to the database.
     * Permissions: App, Edit|Owner
     *
     * @param request The credentials to store in the database.
     */
    public void storeCredentials(final PutCredentialRequest request) {
        checkAppPermission();
        final Credential credential = request.getCredential();
        final Secret secret = request.getSecret();
        final long now = System.currentTimeMillis();
        final String userName = securityContext.getUserIdentityForAudit();
        byte[] keyStore = null;

        if (secret instanceof final KeyStoreSecret keyStoreSecret) {
            if (keyStoreSecret.getResourceKey() == null) {
                throw new RuntimeException("No key store has been uploaded");
            }
            final Path path = resourceStoreProvider.get().getTempFile(keyStoreSecret.getResourceKey());
            if (path == null) {
                throw new RuntimeException("Temporary key store upload not found");
            }
            try {
                keyStore = Files.readAllBytes(path);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        // Get existing secret.
        final String uuid = credential.getUuid();
        final Credential existing = credentialsDao.getCredentialByUuid(uuid);
        final Credential updatedCredential;
        if (existing == null) {
            // Store the new credential.
            updatedCredential = new Credential(uuid,
                    credential.getName(),
                    now,
                    now,
                    userName,
                    userName,
                    credential.getCredentialType(),
                    credential.getKeyStoreType(),
                    credential.getExpiryTimeMs());
        } else {
            // Check we are allowed to update this existing credential.
            checkEditPermission(uuid);

            updatedCredential = new Credential(uuid,
                    credential.getName(),
                    existing.getCreateTimeMs(),
                    now,
                    existing.getCreateUser(),
                    userName,
                    credential.getCredentialType(),
                    credential.getKeyStoreType(),
                    credential.getExpiryTimeMs());
        }

        final StoredSecret storedSecret = new StoredSecret(updatedCredential, secret, keyStore);
        credentialsDao.putStoredSecret(storedSecret, existing != null);

        if (existing == null) {
            // Ensure newly created credentials are owned by the current user.
            permissionServiceProvider.get().setPermission(
                    credential.asDocRef(),
                    securityContext.getUserRef(),
                    DocumentPermission.OWNER);
        }
    }

    /**
     * Returns the credential matching the UUID.
     * Permissions: App, View|Edit|Owner
     *
     * @return The credential matching the UUID.
     */
    public Credential getCredentialByUuid(final String uuid) {
        checkAppPermission();
        checkViewPermission(uuid);
        return credentialsDao.getCredentialByUuid(uuid);
    }

    /**
     * Returns the credential matching the name.
     * Permissions: App, View|Edit|Owner
     *
     * @return The credential matching the name.
     */
    public Credential getCredentialByName(final String name) {
        checkAppPermission();
        final Credential credential = credentialsDao.getCredentialByName(name);
        if (credential != null) {
            checkViewPermission(credential.getUuid());
        }
        return credential;
    }

    /**
     * Deletes the credentials and secrets matching the UUID.
     * Permissions: App, Delete|Owner
     */
    public void deleteCredentialsAndSecret(final String uuid) {
        checkAppPermission();
        checkDeletePermission(uuid);
        credentialsDao.deleteCredentialsAndSecret(uuid);
    }

    @Override
    public StoredSecret get(final String name) {
        final StoredSecret storedSecret = credentialsDao.getStoredSecretByName(name);
        if (storedSecret == null) {
            return null;
        }

        final DocRef docRef = storedSecret.credential().asDocRef();
        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
            return storedSecret;
        } else {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to view these credentials");
        }
    }

    /**
     * Throws exception if the user does not have permission to access credentials.
     */
    private void checkAppPermission() {
        if (!securityContext.hasAppPermission(AppPermission.CREDENTIALS)) {
            throw new PermissionException(
                    securityContext.getUserRef(),
                    "You do not have permission to use credentials");
        }
    }

    /**
     * Throws exception if the user does not have permission to view the given credentials.
     *
     * @param uuid ID of the credentials to check.
     */
    @SuppressWarnings("StatementWithEmptyBody")
    private void checkViewPermission(final String uuid) {
        if (uuid == null) {
            throw new RuntimeException("Credentials not found");
        }
        final DocRef docRef = new DocRef(Credential.TYPE, uuid);
        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)
            || securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)
            || securityContext.hasDocumentPermission(docRef, DocumentPermission.OWNER)) {
            // OK
        } else {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to view these credentials");
        }
    }

    /**
     * Throws exception if the user does not have permission to edit the given credentials.
     *
     * @param uuid ID of the credentials to check.
     */
    @SuppressWarnings("StatementWithEmptyBody")
    private void checkEditPermission(final String uuid) {
        if (uuid == null) {
            throw new RuntimeException("Credentials not found");
        }
        final DocRef docRef = new DocRef(Credential.TYPE, uuid);
        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)
            || securityContext.hasDocumentPermission(docRef, DocumentPermission.OWNER)) {
            // OK
        } else {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to edit these credentials");
        }
    }

    /**
     * Throws exception if the user does not have permission to delete the given credentials.
     *
     * @param uuid ID of the credentials to check.
     */
    @SuppressWarnings("StatementWithEmptyBody")
    private void checkDeletePermission(final String uuid) {
        if (uuid == null) {
            throw new RuntimeException("Credentials not found");
        }
        final DocRef docRef = new DocRef(Credential.TYPE, uuid);
        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.DELETE)
            || securityContext.hasDocumentPermission(docRef, DocumentPermission.OWNER)) {
            // OK
        } else {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to delete these credentials");
        }
    }

    @Override
    public KeyStore getKeyStore(final String name) {
        if (NullSafe.isBlankString(name)) {
            return null;
        }

        final StoredSecret storedSecret = get(name);
        if (storedSecret == null) {
            throw new RuntimeException("Unable to find secret '" + name + "'");
        }

        if (storedSecret.secret() instanceof KeyStoreSecret keyStoreSecret) {
            final Path path = pathCreator
                    .toAppPath(NullSafe.getOrElse(
                            credentialsConfigProvider.get(),
                            CredentialsConfig::getKeyStoreCachePath,
                            CredentialsConfig.DEFAULT_KEY_STORE_CACHE_PATH));
            final Path keyStorePath = path.resolve(storedSecret.credential().getUuid() + ".keystore");

            // See if the key store file exists and if it is old.
            boolean exists = Files.exists(keyStorePath);
            boolean old = false;
            if (exists) {
                old = isKeyStoreOld(keyStorePath);
            }

            try {
                if (!exists || old) {
                    synchronized (this) {
                        // Try under lock to see if the file is old if it exists.
                        exists = Files.exists(keyStorePath);
                        if (exists) {
                            old = isKeyStoreOld(keyStorePath);
                        }

                        // If the file does not exist or is old then write it.
                        if (!exists || old) {
                            final byte[] bytes = storedSecret.keyStore();
                            if (bytes == null || bytes.length == 0) {
                                throw new RuntimeException("Key store data is missing");
                            }

                            // Create root directory for key stores
                            Files.createDirectories(path);

                            // Write temporary keystore
                            final Path tempFile = path.resolve(UUID.randomUUID() + ".keystore");
                            Files.write(tempFile, bytes);

                            // Move keystore
                            Files.move(tempFile, keyStorePath, StandardCopyOption.ATOMIC_MOVE);
                        }
                    }
                }

                return new KeyStore(
                        FileUtil.getCanonicalPath(keyStorePath),
                        keyStoreSecret.getKeyStorePassword(),
                        NullSafe.getOrElse(keyStoreSecret, KeyStoreSecret::getKeyStoreType, KeyStoreType.JKS).name(),
                        null);

            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            throw new RuntimeException("Secret '" + name + "' is not a key store");
        }
    }

    private boolean isKeyStoreOld(final Path path) {
        try {
            final FileTime fileTime = Files.getLastModifiedTime(path);
            return Instant.now().isAfter(fileTime.toInstant().plus(10, ChronoUnit.MINUTES));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
