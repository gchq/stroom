/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.security.identity.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestAccount {

    @Test
    void missingNamePartIsNotPaddedWithASpace() {
        // This becomes the stroom user\'s stored full name, so " Smith" is what later comparisons then
        // have to match against.
        assertThat(nameOf("John", null)).isEqualTo("John");
        assertThat(nameOf(null, "Smith")).isEqualTo("Smith");
        assertThat(nameOf("John", "Smith")).isEqualTo("John Smith");
    }

    @Test
    void accountWithNoNameAtAllHasNoFullName() {
        // Distinct from an empty name: nothing is known, so nothing is claimed.
        assertThat(nameOf(null, null)).isNull();
    }

    private static String nameOf(final String firstName, final String lastName) {
        final Account account = new Account();
        account.setFirstName(firstName);
        account.setLastName(lastName);
        return account.getFullName();
    }
}
