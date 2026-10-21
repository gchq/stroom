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

import stroom.cluster.lock.api.ClusterLockService;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.credentials.shared.KeyStoreFileSecret;
import stroom.credentials.shared.PutCredentialRequest;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserService;
import stroom.security.shared.User;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.util.shared.UserType;

import event.logging.CreateEventAction;
import event.logging.OtherObject;
import event.logging.Outcome;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/// Creates the 'Key Store File' credentials defined in [CredentialsConfig#getKeyStoreFiles()] when
/// Stroom starts, if no credential with the same name exists. Existing credentials are never
/// changed. Each credential is created as, and owned by, the user or group in
/// [CredentialsConfig#getKeyStoreFilesOwnerSubjectId()].
public class KeyStoreFileCredentialsBootstrap {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(KeyStoreFileCredentialsBootstrap.class);

    static final String CONFIG_PROPERTY = "stroom.credentials.keyStoreFiles";
    static final String OWNER_CONFIG_PROPERTY = "stroom.credentials.keyStoreFilesOwnerSubjectId";

    private static final String LOCK_NAME = "KeyStoreFileCredentialsBootstrap";
    private static final String BASE_TYPE = KeyStoreFileCredentialsBootstrap.class.getSimpleName();

    private final Provider<CredentialsConfig> credentialsConfigProvider;
    private final CredentialsService credentialsService;
    private final UserService userService;
    private final SecurityContext securityContext;
    private final ClusterLockService clusterLockService;
    private final StroomEventLoggingService stroomEventLoggingService;

    /// @param credentialsConfigProvider Provides the config holding the credentials to create.
    /// @param credentialsService        Creates the credentials.
    /// @param userService               Finds the user or group that will own the credentials.
    /// @param securityContext           Runs the work as the processing user and the owner.
    /// @param clusterLockService        Stops two nodes creating the same credential.
    /// @param stroomEventLoggingService Audits each credential created.
    @Inject
    public KeyStoreFileCredentialsBootstrap(final Provider<CredentialsConfig> credentialsConfigProvider,
                                            final CredentialsService credentialsService,
                                            final UserService userService,
                                            final SecurityContext securityContext,
                                            final ClusterLockService clusterLockService,
                                            final StroomEventLoggingService stroomEventLoggingService) {
        this.credentialsConfigProvider = credentialsConfigProvider;
        this.credentialsService = credentialsService;
        this.userService = userService;
        this.securityContext = securityContext;
        this.clusterLockService = clusterLockService;
        this.stroomEventLoggingService = stroomEventLoggingService;
    }

    /// Creates any configured key store file credentials that don't already exist. An entry that
    /// can't be created is logged and skipped, so it never stops Stroom from starting.
    public void startup() {
        final CredentialsConfig config = credentialsConfigProvider.get();
        final List<KeyStoreFileCredentialConfig> keyStoreFiles = NullSafe.list(config.getKeyStoreFiles());
        if (keyStoreFiles.isEmpty()) {
            LOGGER.debug("startup() - No key store file credentials configured");
            return;
        }

        try {
            securityContext.asProcessingUser(() -> {
                final UserRef owner = findOwner(config, keyStoreFiles.size());
                if (owner != null) {
                    // Each node has its own config, so every node must process its own list. Waiting for
                    // the lock, rather than skipping if another node has it, stops two nodes creating
                    // the same credential without missing any entry.
                    clusterLockService.lock(LOCK_NAME, () ->
                            keyStoreFiles.forEach(keyStoreFile -> createIfMissing(keyStoreFile, owner)));
                }
            });
        } catch (final RuntimeException e) {
            // E.g. the lock could not be obtained in time or the owner could not be looked up.
            // Errors for each entry are handled in createIfMissing.
            LOGGER.error("Unable to create the credentials in {}: {}", CONFIG_PROPERTY, e.getMessage(), e);
        }
    }

    private UserRef findOwner(final CredentialsConfig config, final int count) {
        final String subjectId = config.getKeyStoreFilesOwnerSubjectId();
        final boolean isGroup = config.getKeyStoreFilesOwnerType() == UserType.GROUP;
        if (NullSafe.isBlankString(subjectId)) {
            LOGGER.error("{} is not set so none of the {} credentials in {} will be created",
                    OWNER_CONFIG_PROPERTY, count, CONFIG_PROPERTY);
            return null;
        }

        final Optional<User> optOwner = isGroup
                ? userService.getGroupByName(subjectId)
                : userService.getUserBySubjectId(subjectId);
        if (optOwner.isEmpty()) {
            LOGGER.error("A {} cannot be found with subject ID '{}' ({}) so none of the {} credentials in {} "
                         + "will be created",
                    (isGroup
                            ? "group"
                            : "user"),
                    subjectId,
                    OWNER_CONFIG_PROPERTY,
                    count,
                    CONFIG_PROPERTY);
            return null;
        }
        return optOwner.get().asRef();
    }

    private void createIfMissing(final KeyStoreFileCredentialConfig keyStoreFile, final UserRef owner) {
        final String name = keyStoreFile.getName();
        final String uuid = UUID.randomUUID().toString();
        try {
            final Credential existing = credentialsService.getCredentialByName(name);
            if (existing != null) {
                logExisting(keyStoreFile, existing);
                return;
            }

            final Credential credential = new Credential(uuid,
                    name,
                    null,
                    null,
                    null,
                    null,
                    CredentialType.KEY_STORE_FILE,
                    null,
                    null,
                    null);
            final KeyStoreFileSecret secret = new KeyStoreFileSecret(keyStoreFile.getKeyStoreType(),
                    keyStoreFile.getKeyStorePath(),
                    keyStoreFile.getKeyStorePassword());
            // Run as the owner so that the owner is recorded as creating the credential, is made its
            // owner, and must have permission to create credentials
            securityContext.asUser(owner, () ->
                    credentialsService.storeCredentials(new PutCredentialRequest(credential, secret)));
        } catch (final RuntimeException e) {
            final String message = LogUtil.message(
                    "Unable to create '{}' credentials '{}' for key store file '{}' from {}: {}{}",
                    CredentialType.KEY_STORE_FILE.getDisplayValue(),
                    name,
                    keyStoreFile.getKeyStorePath(),
                    CONFIG_PROPERTY,
                    e.getMessage(),
                    getUnsetEnvVarHint(keyStoreFile));
            LOGGER.error(message);
            LOGGER.debug(message, e);
            logCreateEvent(uuid, name, owner, false, message);
            return;
        }

        // Outside the try so that a failure to log is never reported as a failure to create
        final String message = LogUtil.message(
                "Created '{}' credentials '{}' for key store file '{}' from {}, owned by '{}'",
                CredentialType.KEY_STORE_FILE.getDisplayValue(),
                name,
                keyStoreFile.getKeyStorePath(),
                CONFIG_PROPERTY,
                owner.toDisplayString());
        LOGGER.info(message);
        logCreateEvent(uuid, name, owner, true, message);
    }

    private void logExisting(final KeyStoreFileCredentialConfig keyStoreFile, final Credential existing) {
        final String name = keyStoreFile.getName();
        if (existing.getCredentialType() != CredentialType.KEY_STORE_FILE) {
            LOGGER.warn("Credentials '{}' in {} have not been created as '{}' credentials with that name "
                        + "already exist. Rename one of them.",
                    name,
                    CONFIG_PROPERTY,
                    NullSafe.get(existing.getCredentialType(), CredentialType::getDisplayValue));
        } else if (existing.getKeyStoreType() != keyStoreFile.getKeyStoreType()
                   || !Objects.equals(existing.getKeyStorePath(), keyStoreFile.getKeyStorePath())) {
            LOGGER.warn("Credentials '{}' already exist for {} key store file '{}', not the {} key store "
                        + "file '{}' in {}. They have not been changed. Use Replace Secret to change them.",
                    name,
                    existing.getKeyStoreType(),
                    existing.getKeyStorePath(),
                    keyStoreFile.getKeyStoreType(),
                    keyStoreFile.getKeyStorePath(),
                    CONFIG_PROPERTY);
        } else {
            LOGGER.info("Credentials '{}' in {} already exist so have not been changed", name, CONFIG_PROPERTY);
        }
    }

    // Unset environment variables are left as they are in the YAML, so the pass phrase would be
    // the literal text, e.g. '${KEY_STORE_PASSWORD}'
    private static String getUnsetEnvVarHint(final KeyStoreFileCredentialConfig keyStoreFile) {
        final String password = keyStoreFile.getKeyStorePassword();
        return password != null && password.contains("${")
                ? " The key store pass phrase looks like an environment variable that is not set."
                : "";
    }

    // Logged as the processing user, as Stroom created the credential on the owner's behalf.
    // A failure to log is itself logged so that it never stops the remaining entries being created.
    private void logCreateEvent(final String uuid,
                                final String name,
                                final UserRef owner,
                                final boolean wasSuccessful,
                                final String description) {
        try {
            stroomEventLoggingService.log(
                    BASE_TYPE + ".createCredential",
                    LogUtil.message("Create credentials '{}' from {}, owned by '{}'",
                            name, CONFIG_PROPERTY, owner.toDisplayString()),
                    CreateEventAction.builder()
                            .addObject(OtherObject.builder()
                                    .withType(Credential.TYPE)
                                    .withId(uuid)
                                    .withName(name)
                                    .build())
                            .withOutcome(Outcome.builder()
                                    .withSuccess(wasSuccessful)
                                    .withDescription(description)
                                    .build())
                            .build());
        } catch (final RuntimeException e) {
            LOGGER.error("Unable to log the audit event for creating credentials '{}' from {}: {}",
                    name, CONFIG_PROPERTY, e.getMessage(), e);
        }
    }
}
