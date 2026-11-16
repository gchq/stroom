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

package stroom.dataconnection.impl;

import stroom.credentials.api.StoredSecrets;
import stroom.credentials.shared.AwsKeySecret;
import stroom.dataconnection.shared.AthenaConnectionSettings;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Objects;

/// Turns the Amazon Athena settings of a Data Connection into a [ResolvedAthenaConnection].
///
/// It checks the settings against the administrator's controls, applies defaults and looks up the
/// named credential, which must be an AWS access key. The credential is looked up as the current
/// user, who needs only `Use` permission on it, so this must run in the user's security context on
/// every use. With no credential, Stroom's own AWS identity is used, if the administrator allows it.
@Singleton
public class AthenaConnectionResolver {

    private final Provider<DataConnectionConfig> configProvider;
    private final Provider<StoredSecrets> storedSecretsProvider;

    /// Creates the resolver. Guice supplies every argument.
    @Inject
    AthenaConnectionResolver(final Provider<DataConnectionConfig> configProvider,
                             final Provider<StoredSecrets> storedSecretsProvider) {
        this.configProvider = configProvider;
        this.storedSecretsProvider = storedSecretsProvider;
    }

    /// Resolves the settings into everything needed to run a query.
    ///
    /// @param settings The Athena settings of a Data Connection.
    /// @return The resolved connection.
    /// @throws IllegalArgumentException If the settings break the administrator's controls, or the
    ///                                  credential is not an AWS access key.
    /// @throws stroom.util.shared.PermissionException If the credential does not exist or the
    ///                                  current user may not use it.
    public ResolvedAthenaConnection resolve(final AthenaConnectionSettings settings) {
        AthenaConnectionValidator.validate(settings, configProvider.get());

        final AwsKeySecret awsKey = NullSafe.isNonBlankString(settings.getCredentialName())
                ? CredentialSecrets.get(
                        storedSecretsProvider.get(),
                        settings.getCredentialName(),
                        AwsKeySecret.class,
                        "an AWS access key")
                : null;

        return new ResolvedAthenaConnection(
                settings.getRegion().trim(),
                AthenaConnectionValidator.workGroup(settings),
                AthenaConnectionValidator.orDefault(settings.getCatalog(), AthenaConnectionSettings.DEFAULT_CATALOG),
                AthenaConnectionValidator.orDefault(settings.getDatabase(),
                        AthenaConnectionSettings.DEFAULT_DATABASE),
                NullSafe.isBlankString(settings.getOutputLocation())
                        ? null
                        : settings.getOutputLocation().trim(),
                Math.max(0, Objects.requireNonNullElse(settings.getResultReuseMinutes(), 0)),
                awsKey);
    }
}
