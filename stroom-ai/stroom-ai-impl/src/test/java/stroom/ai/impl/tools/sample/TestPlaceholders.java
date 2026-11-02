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

package stroom.ai.impl.tools.sample;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestPlaceholders {

    @Test
    void aDashForAMissingAddressIsFoundWithWhatTheOthersAre() {
        // Seen in testing: W3C logs' s-ip.
        final List<Placeholders.Found> found = find("s-ip", "10.0.0.1", "-", "10.0.0.2", "-", "10.0.0.3");

        assertThat(found).containsExactly(new Placeholders.Found("-", List.of("s-ip"), 2, "IP addresses"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-", "--", "NA", "N/A", "n/a", "null", "NULL", "(null)", "None", "nil", "?", "unknown"})
    void theUsualStandInsAreFoundInAnyField(final String value) {
        assertThat(find("user", "alice", value, "bob", value))
                .singleElement()
                .satisfies(found -> {
                    assertThat(found.value()).isEqualTo(value);
                    // Text fields have no kind for it to misfit.
                    assertThat(found.kind()).isNull();
                });
    }

    @Test
    void aShortValueThatIsntOfTheFieldsKindIsFound() {
        // Not one of the usual stand-ins, but recurring among numbers.
        assertThat(find("time-taken", "15", "x", "203", "x", "7", "12"))
                .containsExactly(new Placeholders.Found("x", List.of("time-taken"), 2, "numbers"));
        // Among times, a 0 for none.
        assertThat(find("end", "2026-10-28T10:00:00Z", "0", "2026-10-28T10:05:00Z", "0", "2026-10-28T10:07:00Z",
                "2026-10-28T10:09:00Z", "2026-10-28T10:11:00Z", "2026-10-28T10:12:00Z"))
                .extracting(Placeholders.Found::value, Placeholders.Found::kind)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("0", "times"));
    }

    @Test
    void ordinaryValuesAreNotFound() {
        // Recurring text in a text field.
        assertThat(find("user", "admin", "admin", "bob")).isEmpty();
        // A stand-in seen once.
        assertThat(find("s-ip", "10.0.0.1", "-", "10.0.0.2")).isEmpty();
        // A long value among numbers is something else, not a stand-in.
        assertThat(find("bytes", "15", "connection refused by peer", "203", "connection refused by peer"))
                .isEmpty();
        // Blank values are already empty.
        assertThat(find("s-ip", "10.0.0.1", "", "  ", null, null)).isEmpty();
        // A field of mixed values has no kind for a value to misfit.
        assertThat(find("detail", "10.0.0.1", "x", "abc", "x", "17", "def")).isEmpty();
    }

    @Test
    void valuesAlreadyDecidedAreNotFoundAgain() {
        final Map<String, List<String>> values = Map.of("s-ip", List.of("10.0.0.1", "-", "-"));

        assertThat(Placeholders.find(values, Set.of("-"))).isEmpty();
    }

    @Test
    void aValueInSeveralFieldsIsFoundOnceWithThemAll() {
        final Map<String, List<String>> values = new LinkedHashMap<>();
        values.put("s-ip", List.of("10.0.0.1", "-", "10.0.0.2", "-"));
        values.put("cs-username", List.of("alice", "-", "-", "-"));
        values.put("cs-host", List.of("www", "N/A", "N/A"));

        assertThat(Placeholders.find(values, Set.of())).containsExactly(
                new Placeholders.Found("-", List.of("s-ip", "cs-username"), 5, "IP addresses"),
                new Placeholders.Found("N/A", List.of("cs-host"), 2, null));
    }

    private static List<Placeholders.Found> find(final String field, final String... values) {
        return Placeholders.find(Map.of(field, Arrays.asList(values)), Set.of());
    }
}
