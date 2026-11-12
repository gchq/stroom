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

package stroom.externaldatasource.impl;

import stroom.credentials.api.StoredSecrets;
import stroom.credentials.shared.UsernamePasswordSecret;
import stroom.externaldatasource.shared.LdapSearchScope;
import stroom.externaldatasource.shared.LdapSettings;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;

import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPURL;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Objects;

/// Turns the LDAP settings of an External Data Source into a [ResolvedLdapConnection].
///
/// It checks the settings against the administrator's controls and looks up the named credential, whose
/// user name is the bind DN. The credential is looked up as the current user, who needs only `Use`
/// permission on it, so this must run in the user's security context on every use.
@Singleton
class LdapConnectionResolver {

    private final Provider<ExternalDataSourceConfig> configProvider;
    private final Provider<StoredSecrets> storedSecretsProvider;

    /// Creates the resolver. Guice supplies every argument.
    @Inject
    LdapConnectionResolver(final Provider<ExternalDataSourceConfig> configProvider,
                           final Provider<StoredSecrets> storedSecretsProvider) {
        this.configProvider = configProvider;
        this.storedSecretsProvider = storedSecretsProvider;
    }

    /// Resolves the settings into everything needed to connect, bind and search.
    ///
    /// @param settings The LDAP settings of an External Data Source.
    /// @return The resolved connection.
    /// @throws IllegalArgumentException If the settings break the administrator's controls, or the
    ///                                  credential is not a username and password with both set.
    /// @throws PermissionException      If the credential does not exist or the current user may not
    ///                                  use it. Both give the same message, so the names of credentials
    ///                                  a user cannot use are not revealed.
    ResolvedLdapConnection resolve(final LdapSettings settings) {
        LdapConnectionValidator.validate(settings, configProvider.get());
        final UsernamePasswordSecret secret = CredentialSecrets.get(
                storedSecretsProvider.get(),
                settings.getCredentialName(),
                UsernamePasswordSecret.class,
                "a username and password");
        // An empty password would make a simple bind anonymous, which is not allowed.
        if (NullSafe.isBlankString(secret.getUsername()) || NullSafe.isEmptyString(secret.getPassword())) {
            throw new IllegalArgumentException("Credential '" + settings.getCredentialName() + "' must hold "
                                               + "both a bind DN, as its username, and a password.");
        }
        final LDAPURL url = parse(settings.getUrl().trim());
        final int pageSize = Objects.requireNonNullElse(settings.getPageSize(), LdapSettings.DEFAULT_PAGE_SIZE);
        return new ResolvedLdapConnection(
                url.getHost(),
                url.getPort(),
                LdapConnectionValidator.LDAPS_SCHEME.equals(url.getScheme()),
                NullSafe.isTrue(settings.getStartTls()),
                secret.getUsername().trim(),
                secret.getPassword(),
                settings.getBaseDn().trim(),
                Objects.requireNonNullElse(settings.getScope(), LdapSearchScope.SUBTREE),
                pageSize);
    }

    // The validator has already parsed it, so this cannot fail.
    private static LDAPURL parse(final String url) {
        try {
            return new LDAPURL(url);
        } catch (final LDAPException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }
}
