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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestConnectionRules {

    @Test
    void valueMatchingAnyPatternIsAllowed() {
        final List<String> problems = new ArrayList<>();

        assertThat(ConnectionRules.matchesAny("jdbc:mysql://db:3306/x",
                List.of("jdbc:postgresql:.*", "jdbc:mysql://db:.*"), "URL pattern", problems)).isTrue();
        assertThat(problems).isEmpty();
    }

    @Test
    void valueMatchingNoPatternOrNoneSetIsNotAllowed() {
        final List<String> problems = new ArrayList<>();

        assertThat(ConnectionRules.matchesAny("x", List.of("y"), "URL pattern", problems)).isFalse();
        assertThat(ConnectionRules.matchesAny("x", null, "URL pattern", problems)).isFalse();
        assertThat(ConnectionRules.matchesAny("x", Arrays.asList(" ", null), "URL pattern", problems)).isFalse();
        assertThat(problems).isEmpty();
    }

    @Test
    void invalidPatternIsAProblemAndTheRestAreStillTried() {
        final List<String> problems = new ArrayList<>();

        assertThat(ConnectionRules.matchesAny("x", List.of("(", "x"), "LDAP URL pattern", problems)).isTrue();
        assertThat(problems).singleElement().asString()
                .startsWith("The allowed LDAP URL pattern '(' is not a valid regular expression");
    }

    @Test
    void unsetOrNegativeSettingsAreReadAsNonNegative() {
        assertThat(ConnectionRules.nonNegative(null, 5)).isEqualTo(5);
        assertThat(ConnectionRules.nonNegative(3, 5)).isEqualTo(3);
        assertThat(ConnectionRules.nonNegative(-3, 5)).isZero();
        assertThat(ConnectionRules.nonNegative(null, -1)).isZero();
    }

    @Test
    void problemsAreThrownTogether() {
        ConnectionRules.throwIfAny(List.of());

        assertThatThrownBy(() -> ConnectionRules.throwIfAny(List.of("One.", "Two.")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("One.\nTwo.");
    }
}
