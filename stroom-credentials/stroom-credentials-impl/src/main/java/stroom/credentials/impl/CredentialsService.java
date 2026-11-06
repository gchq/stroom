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
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

public class CredentialsService implements StoredSecrets {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CredentialsService.class);

    private final CredentialsDao credentialsDao;
    private final SecurityContext securityContext;
    private final Provider<DocumentPermissionService> permissionServiceProvider;
    private final Provider<ResourceStore> resourceStoreProvider;
    private final PathCreator pathCreator;
    private final Provider<CredentialsConfig> credentialsConfigProvider;

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

    /// Provides a decoration function that will add security permissions to supplied credentials.
    private Function<Credential, CredentialWithPerms> createPermissionDecorator(
            final FindCredentialRequest request) {

        return credential -> {
            final DocRef docRef = credential.asDocRef();
            // requiredPermission is non-null so that effectively means the user must hold at least USE
            if (!securityContext.hasDocumentPermission(docRef, request.getRequiredPermission())) {
                return null;
            }

            final boolean canEdit = securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT);
            final boolean canDelete = securityContext.hasDocumentPermission(docRef, DocumentPermission.DELETE);
            return new CredentialWithPerms(credential, canEdit, canDelete);
        };
    }

    private Predicate<Credential> createPermissionFilter(final FindCredentialRequest request) {
        // requiredPermission is non-null so that effectively means the user must hold at least USE
        final DocumentPermission requiredPermission = request.getRequiredPermission();
        return credential ->
                securityContext.hasDocumentPermission(credential.asDocRef(), requiredPermission);
    }

    /// Returns a list of all the credentials in the database.
    /// Permissions: App, View|Edit|Owner (for all individual permissions)
    public ResultPage<CredentialWithPerms> findCredentialsWithPermissions(final FindCredentialRequest request) {
        return credentialsDao.findCredentialsWithPermissions(request, createPermissionDecorator(request));
    }

    /// Returns a list of all the credentials in the database.
    /// Permissions: App, View|Edit|Owner (for all individual permissions)
    public ResultPage<Credential> findCredentials(final FindCredentialRequest request) {
        return credentialsDao.findCredentials(request, createPermissionFilter(request));
    }

    /// Stores the given credential to the database.
    /// Permissions: App, Edit|Owner
    ///
    /// @param request The credentials to store in the database.
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

            // Verify keystore can be read and that the password is valid
            verifyKeystore(keyStoreSecret, path);

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
            updatedCredential = new Credential(
                    uuid,
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
            checkPermission(uuid, DocumentPermission.EDIT);

            updatedCredential = new Credential(
                    uuid,
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

    /// Returns the credential matching the UUID.
    /// Permissions: App, View|Edit|Owner
    ///
    /// @return The credential matching the UUID.
    public Credential getCredentialByUuid(final String uuid) {
        checkPermission(uuid, DocumentPermission.USE);
        return credentialsDao.getCredentialByUuid(uuid);
    }

    /// Returns the credential matching the name.
    /// Permissions: App, View|Edit|Owner
    ///
    /// @return The credential matching the name.
    public Credential getCredentialByName(final String name) {
        final Credential credential = credentialsDao.getCredentialByName(name);
        if (credential != null) {
            checkPermission(credential.asDocRef(), DocumentPermission.USE);
        }
        return credential;
    }

    /// Deletes the credentials and secrets matching the UUID.
    /// Permissions: App, Delete|Owner
    public void deleteCredentialsAndSecret(final String uuid) {
        Objects.requireNonNull(uuid, "uuid must not be null");
        checkAppPermission();
        checkPermission(uuid, DocumentPermission.DELETE);
        credentialsDao.deleteCredentialsAndSecret(uuid);
    }

    @Override
    public StoredSecret get(final String name) {
        final StoredSecret storedSecret = credentialsDao.getStoredSecretByName(name);
        if (storedSecret == null) {
            return null;
        }

        final DocRef docRef = storedSecret.credential().asDocRef();
        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.USE)) {
            return storedSecret;
        } else {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to view these credentials");
        }
    }

    /// Throws exception if the user does not have permission to access credentials.
    private void checkAppPermission() {
        if (!securityContext.hasAppPermission(AppPermission.CREDENTIALS)) {
            throw new PermissionException(
                    securityContext.getUserRef(),
                    "You do not have permission to use credentials");
        }
    }

    /// Throws exception if the user does not have permission to delete the given credentials.
    ///
    /// @param credentialUuid     UUID of the credentials to check.
    /// @param requiredPermission Required permission to check.
    private void checkPermission(final String credentialUuid,
                                 final DocumentPermission requiredPermission) {
        Objects.requireNonNull(credentialUuid, "Credentials UUID must not be null");
        final DocRef docRef = Credential.getDocRef(credentialUuid);
        checkPermission(docRef, requiredPermission);
    }

    /// Throws exception if the user does not have permission to delete the given credentials.
    ///
    /// @param credentialDocRef   [DocRef] of the credentials to check.
    /// @param requiredPermission Required permission to check.
    private void checkPermission(final DocRef credentialDocRef,
                                 final DocumentPermission requiredPermission) {
        Objects.requireNonNull(credentialDocRef, "credentialDocRef must not be null");
        Objects.requireNonNull(requiredPermission, "Required permission must not be null");
        if (!securityContext.hasDocumentPermission(credentialDocRef, requiredPermission)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have " + requiredPermission.getDisplayValue()
                    + " permission on credentials with UUID '" + credentialDocRef.getUuid() + "'");
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

        if (storedSecret.secret() instanceof final KeyStoreSecret keyStoreSecret) {
            final Path path = pathCreator
                    .toAppPath(NullSafe.getOrElse(
                            credentialsConfigProvider.get(),
                            CredentialsConfig::getKeyStoreCachePath,
                            CredentialsConfig.DEFAULT_KEY_STORE_CACHE_PATH));
            final KeyStoreType keyStoreType = NullSafe.getOrElse(
                    keyStoreSecret,
                    KeyStoreSecret::getKeyStoreType,
                    KeyStoreType.JKS);
            final String fileExtension = "." + keyStoreType.getFileExtension();
            final Path keyStorePath = path.resolve(keyStoreSecret.getUuid() + fileExtension);

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
                            final Path tempFile = path.resolve(UUID.randomUUID() + fileExtension);
                            Files.write(tempFile, bytes);

                            // Move keystore
                            Files.move(tempFile, keyStorePath, StandardCopyOption.ATOMIC_MOVE);

                            // Verify the store.
                            verifyKeystore(keyStoreSecret, keyStorePath);
                        }
                    }
                }

                return new KeyStore(
                        FileUtil.getCanonicalPath(keyStorePath),
                        keyStoreSecret.getKeyStorePassword(),
                        keyStoreType.name(),
                        null);

            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            throw new RuntimeException("Secret '" + name + "' is not a key store");
        }
    }

    private void verifyKeystore(final KeyStoreSecret keyStoreSecret,
                                final Path path) {
        try {
            final java.security.KeyStore keyStore = java.security.KeyStore.getInstance(
                    keyStoreSecret.getKeyStoreType().getDisplayValue());
            final char[] password = NullSafe.get(
                    keyStoreSecret,
                    KeyStoreSecret::getKeyStorePassword,
                    String::toCharArray);
            try (final InputStream inputStream = Files.newInputStream(path)) {
                keyStore.load(inputStream, password);
            }
        } catch (final KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
            LOGGER.error(e::getMessage, e);
            throw new RuntimeException(e.getMessage(), e);
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
