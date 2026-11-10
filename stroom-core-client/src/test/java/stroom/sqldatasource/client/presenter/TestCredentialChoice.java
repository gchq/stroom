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

package stroom.sqldatasource.client.presenter;

import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestCredentialChoice {

    @Test
    void testKeepsStoredNameWhenNothingPicked() {
        // As when the user may not use the credential, or saves before the lookup returns.
        final CredentialChoice choice = new CredentialChoice();
        choice.reset("reporting-db");

        assertThat(choice.isPicked()).isFalse();
        assertThat(choice.getName()).isEqualTo("reporting-db");
    }

    @Test
    void testPickReplacesStoredName() {
        final CredentialChoice choice = new CredentialChoice();
        choice.reset("reporting-db");
        choice.pick(credential("other-db"));

        assertThat(choice.isPicked()).isTrue();
        assertThat(choice.getName()).isEqualTo("other-db");
    }

    @Test
    void testPickingNoneRemovesStoredName() {
        final CredentialChoice choice = new CredentialChoice();
        choice.reset("reporting-db");
        choice.pick(null);

        assertThat(choice.isPicked()).isTrue();
        assertThat(choice.getName()).isNull();
    }

    @Test
    void testBlankStoredNameIsNone() {
        final CredentialChoice choice = new CredentialChoice();
        choice.reset("  ");

        assertThat(choice.getName()).isNull();
    }

    @Test
    void testResetForgetsEarlierPick() {
        final CredentialChoice choice = new CredentialChoice();
        choice.reset("reporting-db");
        choice.pick(credential("other-db"));
        choice.reset("next-db");

        assertThat(choice.isPicked()).isFalse();
        assertThat(choice.getName()).isEqualTo("next-db");
    }

    @Test
    void testAwaitsLookupOfStoredName() {
        final CredentialChoice choice = new CredentialChoice();
        choice.reset("reporting-db");

        assertThat(choice.awaits("reporting-db")).isTrue();
    }

    @Test
    void testDoesNotAwaitLookupAfterPick() {
        final CredentialChoice choice = new CredentialChoice();
        choice.reset("reporting-db");
        choice.pick(credential("other-db"));

        assertThat(choice.awaits("reporting-db")).isFalse();
    }

    @Test
    void testDoesNotAwaitStaleLookupAfterReread() {
        // A lookup from before the document was read again, naming another credential.
        final CredentialChoice choice = new CredentialChoice();
        choice.reset("reporting-db");
        choice.reset("next-db");

        assertThat(choice.awaits("reporting-db")).isFalse();
        assertThat(choice.awaits("next-db")).isTrue();
    }

    @Test
    void testUnresolvedNoteNamesCredential() {
        final CredentialChoice choice = new CredentialChoice();
        choice.reset("reporting-db");

        assertThat(choice.unresolvedNote())
                .contains("'reporting-db'")
                .contains("kept");
    }

    private static Credential credential(final String name) {
        return new Credential("uuid-" + name,
                name,
                null,
                null,
                null,
                null,
                CredentialType.USERNAME_PASSWORD,
                null,
                null);
    }
}
