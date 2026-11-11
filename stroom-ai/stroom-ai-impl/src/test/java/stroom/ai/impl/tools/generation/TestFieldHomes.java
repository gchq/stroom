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

package stroom.ai.impl.tools.generation;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestFieldHomes {

    @Test
    void accountsAndIdsReadAsIdentifiers() {
        assertThat(FieldHomes.readsAsIdentifiers(List.of("alice", "CORP\\bob", "S-1-5-21-1004", "carol@example.com",
                "dave"))).isTrue();
    }

    @Test
    void subjectLinesDoNotReadAsIdentifiers() {
        // Seen in testing: a mail log's subject drafted as EventSource/User/Id. A one-word subject among them
        // doesn't make them identifiers.
        assertThat(FieldHomes.readsAsIdentifiers(List.of("Q3 report", "Invoice 4471", "Lunch?", "Q3 report")))
                .isFalse();
        // A certificate's subject is text too.
        assertThat(FieldHomes.readsAsIdentifiers(List.of("CN=www.example.com, O=Example", "CN=api, O=Example")))
                .isFalse();
    }

    @Test
    void noValuesDoNotReadAsIdentifiers() {
        assertThat(FieldHomes.readsAsIdentifiers(null)).isFalse();
        assertThat(FieldHomes.readsAsIdentifiers(List.of())).isFalse();
        assertThat(FieldHomes.readsAsIdentifiers(Arrays.asList(null, " ", ""))).isFalse();
    }

    @Test
    void blankValuesAreLeftOutOfTheShare() {
        assertThat(FieldHomes.readsAsIdentifiers(Arrays.asList("alice", null, "", "bob", "carol", "dave",
                "erin smith"))).isTrue();
    }

    @Test
    void subjectIsNotAUserByItsNameAlone() {
        // So a Data entry named subject isn't said to belong in EventSource/User/Id either.
        final FieldHomes.Home user = FieldHomes.HOMES.stream()
                .filter(home -> FieldHomes.USER_ID.equals(home.path()))
                .findFirst()
                .orElseThrow();
        assertThat(FieldHomes.matches(user.regex(), "subject")).isFalse();
        assertThat(FieldHomes.matches(user.regex(), "user")).isTrue();
        assertThat(FieldHomes.matches(FieldHomes.USER_BY_VALUE, "Subject")).isTrue();
        assertThat(FieldHomes.matches(FieldHomes.USER_BY_VALUE, "subject_user")).isFalse();
    }
}
