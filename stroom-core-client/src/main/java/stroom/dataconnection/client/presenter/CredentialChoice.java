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

package stroom.dataconnection.client.presenter;

import stroom.credentials.shared.Credential;
import stroom.util.shared.NullSafe;

import java.util.Objects;

/// The credential a document names, as the user edits it.
///
/// The credential picker can only show a credential the user can see, and fills in after a lookup.
/// So the name the document was read with is kept until the user picks another credential, or
/// none. A user without Use permission on the credential, or one who saves before the lookup
/// returns, does not remove the credential from the document.
public class CredentialChoice {

    private String storedName;
    private boolean picked;
    private Credential pickedCredential;

    /// Starts again from the name a document was read with.
    ///
    /// @param storedName The credential name in the document, or `null` for none.
    public void reset(final String storedName) {
        this.storedName = NullSafe.isBlankString(storedName)
                ? null
                : storedName;
        this.picked = false;
        this.pickedCredential = null;
    }

    /// Records the user's pick.
    ///
    /// @param credential The credential picked, or `null` for none.
    public void pick(final Credential credential) {
        this.picked = true;
        this.pickedCredential = credential;
    }

    /// @return Whether the user has picked a credential, or none, since the document was read.
    public boolean isPicked() {
        return picked;
    }

    /// Says whether a lookup of a credential may still show its result in the picker: the user
    /// has not picked one since, and the document has not been read again naming another.
    ///
    /// @param credentialName The name that was looked up.
    /// @return Whether the lookup is still wanted.
    public boolean awaits(final String credentialName) {
        return !picked && Objects.equals(storedName, credentialName);
    }

    /// @return The name to write to the document: the user's pick, or else the name it was read
    /// with. `null` for none.
    public String getName() {
        return picked
                ? NullSafe.get(pickedCredential, Credential::getName)
                : storedName;
    }

    /// @return The note to show when the stored credential could not be shown in the picker.
    public String unresolvedNote() {
        return "Credential '" + storedName + "' was not found, or you do not have Use permission on it. "
               + "It is kept unless you choose another.";
    }
}
