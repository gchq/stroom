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

package stroom.sqldatasource.impl;

import stroom.credentials.api.StoredSecret;
import stroom.credentials.api.StoredSecrets;
import stroom.credentials.shared.UsernamePasswordSecret;
import stroom.sqldatasource.shared.JdbcConnectionSettings;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Objects;

/// Turns the JDBC settings of a SQL Data Source into a [ResolvedJdbcConnection].
///
/// It checks the settings against the administrator's controls, applies the pool size ceiling and
/// looks up the named credential. The credential is looked up as the current user, who needs only
/// `Use` permission on it, so this must run in the user's security context on every use.
@Singleton
public class JdbcConnectionResolver {

    private final Provider<SqlDataSourceConfig> configProvider;
    private final Provider<StoredSecrets> storedSecretsProvider;

    /// Creates the resolver. Guice supplies every argument.
    @Inject
    JdbcConnectionResolver(final Provider<SqlDataSourceConfig> configProvider,
                           final Provider<StoredSecrets> storedSecretsProvider) {
        this.configProvider = configProvider;
        this.storedSecretsProvider = storedSecretsProvider;
    }

    /// Resolves the settings into everything needed to open a connection.
    ///
    /// @param settings The JDBC settings of a SQL Data Source.
    /// @return The resolved connection.
    /// @throws IllegalArgumentException If the settings break the administrator's controls, or the
    ///                                  credential is not a username and password.
    /// @throws PermissionException      If the credential does not exist or the current user may not
    ///                                  use it. Both give the same message, so the names of
    ///                                  credentials a user cannot use are not revealed.
    public ResolvedJdbcConnection resolve(final JdbcConnectionSettings settings) {
        final SqlDataSourceConfig config = configProvider.get();
        JdbcConnectionValidator.validate(settings, config);

        String username = null;
        String password = null;
        final String credentialName = settings.getCredentialName();
        if (NullSafe.isNonBlankString(credentialName)) {
            final UsernamePasswordSecret secret = getUsernamePasswordSecret(credentialName);
            username = secret.getUsername();
            password = secret.getPassword();
        }

        final int requestedPoolSize = Objects.requireNonNullElse(
                settings.getMaxPoolSize(),
                JdbcConnectionSettings.DEFAULT_MAX_POOL_SIZE);
        final int maxPoolSize = Math.max(1, Math.min(requestedPoolSize, config.getMaxPoolSizeLimit()));

        return new ResolvedJdbcConnection(
                settings.getUrl().trim(),
                username,
                password,
                settings.getConnectionProperties(),
                maxPoolSize);
    }

    private UsernamePasswordSecret getUsernamePasswordSecret(final String credentialName) {
        final StoredSecret storedSecret;
        try {
            // Throws if the current user does not have Use permission on the credential.
            storedSecret = storedSecretsProvider.get().get(credentialName);
        } catch (final PermissionException e) {
            throw cannotUse(credentialName);
        }
        if (storedSecret == null) {
            throw cannotUse(credentialName);
        }
        if (storedSecret.secret() instanceof final UsernamePasswordSecret usernamePasswordSecret) {
            return usernamePasswordSecret;
        }
        throw new IllegalArgumentException("Credential '" + credentialName + "' is not a username and password");
    }

    private static PermissionException cannotUse(final String credentialName) {
        return new PermissionException(null, "Unable to use credential '" + credentialName
                                             + "'. It does not exist, or you do not have permission to use it.");
    }
}
