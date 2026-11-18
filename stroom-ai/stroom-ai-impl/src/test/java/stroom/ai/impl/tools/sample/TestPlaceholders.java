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
    void aNumberOfEightDigitsInAFieldOfNumbersIsNotAStandIn() {
        // Seen in testing: a file size of 14681927 (twice) in x-rs-file-size was put to the user as a stand-in, read
        // as a date (yyyyMMdd) among numbers.
        assertThat(find("x-rs-file-size", "5120", "14681927", "830", "14681927", "20231015", "20231015", "77"))
                .isEmpty();
        // A stand-in among them is still found.
        assertThat(find("x-rs-file-size", "5120", "-", "830", "-", "14681927", "77"))
                .containsExactly(new Placeholders.Found("-", List.of("x-rs-file-size"), 2, "numbers"));
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

    @Test
    void aMisfitHoldingADigitAmongNumbersIsData() {
        // Seen in testing: browser versions and post codes, others numbers, asked as stand-ins for none.
        assertThat(find("browser-version", "146.0", "120.0", "146.0.0.0", "146.0.0.0", "7.0;", "7.0;", "11.0)",
                "11.0)", "121.0", "118.0", "119.0", "117.0", "116.0", "115.0", "114.0")).isEmpty();
        assertThat(find("zipcode", "2000", "3000", "D02", "D02", "102-0082", "102-0082", "4000", "5000", "6000",
                "7000")).isEmpty();
        // A misfit without a digit is still asked among numbers.
        assertThat(find("bytes", "15", "n.a", "20", "n.a", "35"))
                .containsExactly(new Placeholders.Found("n.a", List.of("bytes"), 2, "numbers"));
    }

    @Test
    void aMisfitIsNotAskedWhereTheDestinationTakesText() {
        // Seen in testing: 'orgname' among numbers in a field written as text.
        final Map<String, List<String>> values = new LinkedHashMap<>();
        values.put("app-instance-id", List.of("12345", "67890", "orgname", "orgname", "11111"));
        values.put("s-ip", List.of("10.0.0.1", "-", "10.0.0.2", "-"));

        assertThat(Placeholders.find(values, Set.of(), Set.of("app-instance-id", "s-ip")))
                .containsExactly(new Placeholders.Found("-", List.of("s-ip"), 2, "IP addresses"));
        // Where the destination isn't known to take text, the misfit is asked.
        assertThat(Placeholders.find(values, Set.of()))
                .extracting(Placeholders.Found::value)
                .containsExactlyInAnyOrder("orgname", "-");
    }

    private static List<Placeholders.Found> find(final String field, final String... values) {
        return Placeholders.find(Map.of(field, Arrays.asList(values)), Set.of());
    }
}
