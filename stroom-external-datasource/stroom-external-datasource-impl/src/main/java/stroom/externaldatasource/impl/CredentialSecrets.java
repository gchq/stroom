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

import stroom.credentials.api.StoredSecret;
import stroom.credentials.api.StoredSecrets;
import stroom.credentials.shared.Secret;
import stroom.util.shared.PermissionException;

/// Looks up the secret of a named credential for an External Data Source.
///
/// The lookup is made as the current user, who needs only `Use` permission on the credential. A
/// credential that does not exist and one the user may not use give the same error, so a document
/// editor cannot find out which credential names exist.
final class CredentialSecrets {

    private CredentialSecrets() {
        // Static methods only.
    }

    /// Gets the secret of a credential, which must be of the given type.
    ///
    /// @param storedSecrets   The credentials store.
    /// @param credentialName  The credential's name.
    /// @param secretType      The type of secret the credential must hold.
    /// @param typeDescription The type of secret in words, for the error message.
    /// @param <T>             The type of secret.
    /// @return The secret.
    /// @throws PermissionException      If the credential does not exist or the user may not use it.
    /// @throws IllegalArgumentException If the credential holds another type of secret.
    static <T extends Secret> T get(final StoredSecrets storedSecrets,
                                    final String credentialName,
                                    final Class<T> secretType,
                                    final String typeDescription) {
        final StoredSecret storedSecret;
        try {
            // Throws if the current user does not have Use permission on the credential.
            storedSecret = storedSecrets.get(credentialName);
        } catch (final PermissionException e) {
            throw cannotUse(credentialName);
        }
        if (storedSecret == null) {
            throw cannotUse(credentialName);
        }
        if (secretType.isInstance(storedSecret.secret())) {
            return secretType.cast(storedSecret.secret());
        }
        throw new IllegalArgumentException("Credential '" + credentialName + "' is not " + typeDescription);
    }

    private static PermissionException cannotUse(final String credentialName) {
        return new PermissionException(null, "Unable to use credential '" + credentialName
                                             + "'. It does not exist, or you do not have permission to use it.");
    }
}
