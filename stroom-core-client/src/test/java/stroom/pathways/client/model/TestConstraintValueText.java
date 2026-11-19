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

package stroom.pathways.client.model;

import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.pathway.ConstraintValue;
import stroom.pathways.shared.pathway.ConstraintValueType;
import stroom.pathways.shared.pathway.IntegerRange;
import stroom.pathways.shared.pathway.LongRange;
import stroom.pathways.shared.pathway.NanoTimeRange;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestConstraintValueText {

    @Test
    void testNullIsAnyValue() {
        // A new constraint has no value yet
        assertThat(ConstraintValueText.typeOf(null)).isEqualTo(ConstraintValueType.ANY);
        assertThat(ConstraintValueText.toText(null)).isEmpty();
    }

    @Test
    void testIntegerRangeShownAsItIsRead() {
        // A range was shown as "1 -> 10" (its toString), which OK then couldn't read
        assertThat(ConstraintValueText.toText(new IntegerRange(1, 10))).isEqualTo("1,10");
        assertThat(ConstraintValueText.toText(new LongRange(2L, 20L))).isEqualTo("2,20");
    }

    @Test
    void testDurationRangeInNanoseconds() {
        final ConstraintValue value = new NanoTimeRange(NanoTime.ofNanos(5), NanoTime.ofNanos(2_000_000_001L));
        assertThat(ConstraintValueText.toText(value)).isEqualTo("5,2000000001");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ANY|",
            "ANY_BOOLEAN|",
            "STRING|abc",
            "REGEX|a.*",
            "BOOLEAN|true",
            "INTEGER|42",
            "LONG|42",
            "DOUBLE|4.5",
            "INTEGER_RANGE|1,10",
            "LONG_RANGE|1,10",
            "DOUBLE_RANGE|1.5,2.5",
            "DURATION_VALUE|1000",
            "DURATION_RANGE|5,2000000001",
            "STRING_SET|a",
            "INTEGER_SET|7",
            "LONG_SET|7",
            "DOUBLE_SET|7.5"})
    void testRoundTrip(final ConstraintValueType type, final String text) {
        final String expected = text == null
                ? ""
                : text;
        final ConstraintValue value = ConstraintValueText.fromText(type, expected);
        assertThat(value.valueType()).isEqualTo(type);
        assertThat(ConstraintValueText.toText(value)).isEqualTo(expected);
    }

    @Test
    void testSpacesAroundCommasIgnored() {
        assertThat(ConstraintValueText.toText(
                ConstraintValueText.fromText(ConstraintValueType.INTEGER_RANGE, " 1 , 10 ")))
                .isEqualTo("1,10");
    }

    @Test
    void testTextKeptAsTyped() {
        // Spaces can matter in text and patterns, so they aren't trimmed (they were, for a while)
        assertThat(ConstraintValueText.toText(ConstraintValueText.fromText(ConstraintValueType.STRING, " a ")))
                .isEqualTo(" a ");
        assertThat(ConstraintValueText.toText(ConstraintValueText.fromText(ConstraintValueType.REGEX, "a\\s+ ")))
                .isEqualTo("a\\s+ ");
        assertThat(ConstraintValueText.toText(ConstraintValueText.fromText(ConstraintValueType.STRING_SET, " a")))
                .isEqualTo(" a");
    }

    @Test
    void testBadNumberRefused() {
        assertThatThrownBy(() -> ConstraintValueText.fromText(ConstraintValueType.INTEGER, "abc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testRangeNeedsTwoValues() {
        assertThatThrownBy(() -> ConstraintValueText.fromText(ConstraintValueType.LONG_RANGE, "1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two values");
    }

    @Test
    void testTypeNeeded() {
        assertThatThrownBy(() -> ConstraintValueText.fromText(null, "1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
