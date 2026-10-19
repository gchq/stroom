/*
 * Copyright 2025 Crown Copyright
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
import stroom.credentials.shared.AccessTokenSecret;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.credentials.shared.CredentialWithPerms;
import stroom.credentials.shared.FindCredentialRequest;
import stroom.credentials.shared.KeyStoreSecret;
import stroom.credentials.shared.PutCredentialRequest;
import stroom.credentials.shared.ReplaceSecretRequest;
import stroom.credentials.shared.Secret;
import stroom.credentials.shared.SshKeySecret;
import stroom.credentials.shared.UsernamePasswordSecret;
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
import stroom.util.logging.LogUtil;
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
     * <p>
     * A new credential is stored with its secret, which must be supplied. For an existing
     * credential only its settings (name and expiry) are updated; its type and secret are kept
     * and any secret in the request is ignored. Use {@link #replaceSecret} to change the secret.
     *
     * @param request The credentials to store in the database.
     */
    public void storeCredentials(final PutCredentialRequest request) {
        checkAppPermission();
        final Credential credential = request.getCredential();
        final long now = System.currentTimeMillis();
        final String userName = securityContext.getUserIdentityForAudit();

        final String uuid = credential.getUuid();
        final Credential existing = credentialsDao.getCredentialByUuid(uuid);
        if (existing == null) {
            final Secret secret = request.getSecret();
            if (secret == null) {
                throw new IllegalArgumentException("A new credential must have a secret");
            }
            checkSecretMatchesType(secret, credential.getCredentialType());
            checkName(credential.getName(), uuid);

            final Credential newCredential = new Credential(uuid,
                    credential.getName(),
                    now,
                    now,
                    userName,
                    userName,
                    credential.getCredentialType(),
                    getKeyStoreType(secret),
                    credential.getExpiryTimeMs());
            credentialsDao.putStoredSecret(
                    new StoredSecret(newCredential, secret, readKeyStore(secret)),
                    false);

            // Ensure newly created credentials are owned by the current user.
            permissionServiceProvider.get().setPermission(
                    credential.asDocRef(),
                    securityContext.getUserRef(),
                    DocumentPermission.OWNER);
        } else {
            // Check we are allowed to update this existing credential.
            checkEditPermission(uuid);
            if (credential.getCredentialType() != null
                && credential.getCredentialType() != existing.getCredentialType()) {
                throw new IllegalArgumentException("The type of an existing credential cannot be changed");
            }
            checkName(credential.getName(), uuid);

            final boolean updated = credentialsDao.updateCredential(new Credential(uuid,
                    credential.getName(),
                    existing.getCreateTimeMs(),
                    now,
                    existing.getCreateUser(),
                    userName,
                    existing.getCredentialType(),
                    existing.getKeyStoreType(),
                    credential.getExpiryTimeMs()));
            if (!updated) {
                throw new IllegalArgumentException("Credentials not found");
            }
        }
    }

    /// Replaces the secret held by an existing credential, discarding the old one.
    /// The credential keeps its UUID and name so everything that refers to it uses the new
    /// secret straight away.
    /// Permissions: App, Edit|Owner
    ///
    /// @param request The credential UUID, the new secret and its expiry.
    /// @return The updated credential.
    public Credential replaceSecret(final ReplaceSecretRequest request) {
        checkAppPermission();
        final String uuid = request.getUuid();
        // Check permission before looking the credential up so that a user without permission
        // can't tell whether a credential exists or not
        checkEditPermission(uuid);
        final StoredSecret existing = credentialsDao.getStoredSecretByUuid(uuid);
        if (existing == null) {
            throw new IllegalArgumentException("Credentials not found");
        }

        final Secret secret = request.getSecret();
        if (secret == null) {
            throw new IllegalArgumentException("A new secret must be supplied");
        }
        checkSecretMatchesType(secret, existing.credential().getCredentialType());

        // Only the secret, its expiry and the update audit fields are written, so a rename made
        // at the same time is not undone
        final Credential secretFields = new Credential(uuid,
                null,
                null,
                System.currentTimeMillis(),
                null,
                securityContext.getUserIdentityForAudit(),
                null,
                getKeyStoreType(secret),
                request.getExpiryTimeMs());
        final boolean updated = credentialsDao.updateSecret(
                new StoredSecret(secretFields, secret, readKeyStore(secret)));
        if (!updated) {
            throw new IllegalArgumentException("Credentials not found");
        }
        deleteCachedKeyStore(existing.secret());

        final Credential updatedCredential = credentialsDao.getCredentialByUuid(uuid);
        LOGGER.info(() -> LogUtil.message("Secret replaced for credential '{}' ({})",
                NullSafe.get(updatedCredential, Credential::getName),
                uuid));
        return updatedCredential;
    }

    /// Checks that a credential name is not blank and is not used by any other credential.
    ///
    /// @param name The name to check.
    /// @param uuid The UUID of the credential that will have this name.
    private void checkName(final String name, final String uuid) {
        if (NullSafe.isBlankString(name)) {
            throw new IllegalArgumentException("Credentials must have a name");
        }
        final Credential sameName = credentialsDao.getCredentialByName(name);
        if (sameName != null && !Objects.equals(sameName.getUuid(), uuid)) {
            throw new IllegalArgumentException("Credentials called '" + name + "' already exist");
        }
    }

    /// Deletes the copy of a key store written to the key store cache by [#getKeyStore], so that
    /// a replaced key store does not stay on disk.
    ///
    /// @param oldSecret The secret that has been replaced. Nothing is done unless it is a key store.
    private void deleteCachedKeyStore(final Secret oldSecret) {
        if (oldSecret instanceof final KeyStoreSecret keyStoreSecret) {
            final Path keyStorePath = getCachedKeyStorePath(keyStoreSecret);
            try {
                synchronized (this) {
                    Files.deleteIfExists(keyStorePath);
                }
            } catch (final IOException e) {
                // The secret has been replaced, so don't fail the request over a stale file
                LOGGER.error("Unable to delete replaced key store '{}'", keyStorePath, e);
            }
        }
    }

    private Path getKeyStoreCachePath() {
        return pathCreator.toAppPath(NullSafe.getOrElse(
                credentialsConfigProvider.get(),
                CredentialsConfig::getKeyStoreCachePath,
                CredentialsConfig.DEFAULT_KEY_STORE_CACHE_PATH));
    }

    private static KeyStoreType getCachedKeyStoreType(final KeyStoreSecret keyStoreSecret) {
        return NullSafe.getOrElse(
                keyStoreSecret,
                KeyStoreSecret::getKeyStoreType,
                KeyStoreType.JKS);
    }

    private Path getCachedKeyStorePath(final KeyStoreSecret keyStoreSecret) {
        return getKeyStoreCachePath().resolve(keyStoreSecret.getUuid()
                                              + "."
                                              + getCachedKeyStoreType(keyStoreSecret).getFileExtension());
    }

    private static void checkSecretMatchesType(final Secret secret, final CredentialType credentialType) {
        final CredentialType secretType = switch (secret) {
            case final AccessTokenSecret ignored -> CredentialType.ACCESS_TOKEN;
            case final KeyStoreSecret ignored -> CredentialType.KEY_STORE;
            case final SshKeySecret ignored -> CredentialType.SSH_KEY;
            case final UsernamePasswordSecret ignored -> CredentialType.USERNAME_PASSWORD;
        };
        if (secretType != credentialType) {
            throw new IllegalArgumentException("The secret does not match the credential type");
        }
    }

    private static KeyStoreType getKeyStoreType(final Secret secret) {
        return secret instanceof final KeyStoreSecret keyStoreSecret
                ? keyStoreSecret.getKeyStoreType()
                : null;
    }

    /// Reads and verifies the uploaded key store for a key store secret.
    ///
    /// @return The key store bytes, or null if the secret is not a key store secret.
    private byte[] readKeyStore(final Secret secret) {
        if (!(secret instanceof final KeyStoreSecret keyStoreSecret)) {
            return null;
        }
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
            return Files.readAllBytes(path);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
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
        // Creating a credential made its creator the owner (storeCredentials), so the permissions
        // go with it, as the explorer's delete does for a document. Done after the delete so that a
        // failed delete leaves the credential still usable by its owner.
        permissionServiceProvider.get().removeAllDocumentPermissions(new DocRef(Credential.TYPE, uuid));
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

        if (storedSecret.secret() instanceof final KeyStoreSecret keyStoreSecret) {
            final Path path = getKeyStoreCachePath();
            final KeyStoreType keyStoreType = getCachedKeyStoreType(keyStoreSecret);
            final String fileExtension = "." + keyStoreType.getFileExtension();
            final Path keyStorePath = getCachedKeyStorePath(keyStoreSecret);

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
