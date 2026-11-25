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

package stroom.query.common.v2;

import stroom.query.api.Column;
import stroom.query.api.ColumnFilter;
import stroom.query.api.ColumnValueSelection;
import stroom.query.api.Format;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValDuration;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValPeriod;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.Values;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestRowValueFilter {

    private static final List<Val> VALUES = List.of(
            ValDuration.create(3_600_000L),
            ValDuration.create(178_205_000L),
            ValLong.create(5_000L),
            ValString.create("1.5h 30m"),
            ValString.create("-1.5h"),
            ValPeriod.create(0, 0, 1),
            ValString.create("1M"),
            ValString.create("soon"),
            ValDate.create(0),
            ValNull.INSTANCE);

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "=1h|1h",
            "=60m|1h",
            "=3600000|1h",
            "=PT1H|1h",
            "=2d1h30m5s|2d1h30m5s",
            // Exactly the text.
            "==1h|1h",
            "==60m|",
            "!==1h|2d1h30m5s;5000;1.5h 30m;-1.5h;P1D;1M;soon;1970-01-01T00:00:00.000Z;null",
            "=2h|1.5h 30m",
            "=24h|P1D",
            ">1h|2d1h30m5s;1.5h 30m;P1D",
            ">=1h|1h;2d1h30m5s;1.5h 30m;P1D",
            "<1h|5000;-1.5h",
            "<=5s|5000;-1.5h",
            "<0|-1.5h",
            // What is not a duration is text.
            "=soon|soon",
            "=so*|soon",
            "=1M|1M",
            // Text is looked for in the value, as it is in any other column.
            "1h|1h;2d1h30m5s",
            "500|5000",
            "5s|2d1h30m5s",
            "oon|soon"})
    void filterOfDurations(final String filter, final String expected) {
        final Column column = Column.builder()
                .id("c")
                .name("c")
                .format(Format.DURATION)
                .columnFilter(new ColumnFilter(filter, true))
                .build();
        assertMatches(RowValueFilter.create(List.of(column), null, new ExpressionPredicateFactory()), expected);
    }

    // The list has an entry for each text, so each tick must only be for that one.
    @Test
    void tickOfEachValueOfADurationColumn() {
        final List<Val> values = Stream.concat(VALUES.stream(), Stream.of(
                        ValString.create("60m"),
                        ValString.create("PT1H"),
                        ValString.create(" soon "),
                        ValString.create("Soon"),
                        ValString.create(" "),
                        ValString.create("*"),
                        ValString.create("so?n"),
                        ValString.create("\\*"),
                        ValString.create("\\"),
                        ValString.create("a\\b"),
                        ValDouble.create(1.2),
                        ValDouble.create(1.7)))
                .filter(val -> val.toString() != null)
                .toList();
        for (final Val ticked : values) {
            final Predicate<Values> only = tick(Format.DURATION, ticked.toString(), false);
            final Predicate<Values> allBut = tick(Format.DURATION, ticked.toString(), true);
            for (final Val val : values) {
                assertThat(only.test(Values.of(val))).as(ticked + " ticked, " + val).isEqualTo(val == ticked);
                assertThat(allBut.test(Values.of(val))).as(ticked + " unticked, " + val).isEqualTo(val != ticked);
            }
            assertThat(only.test(Values.of(ValNull.INSTANCE))).isFalse();
            assertThat(allBut.test(Values.of(ValNull.INSTANCE))).isTrue();
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "=1970-01-01T00:00:00.000Z|1970-01-01T00:00:00.000Z",
            ">1969-01-01T00:00:00.000Z|5000;1970-01-01T00:00:00.000Z",
            // Text that is not a date is not equal to one.
            "!=1970-01-01T00:00:00.000Z|1h;2d1h30m5s;5000;1.5h 30m;-1.5h;P1D;1M;soon;null"})
    void filterOfDates(final String filter, final String expected) {
        for (final Format format : List.of(Format.GENERAL, Format.TEXT, Format.DATE_TIME)) {
            final Column column = Column.builder()
                    .id("c")
                    .name("c")
                    .format(format)
                    .columnFilter(new ColumnFilter(filter, true))
                    .build();
            assertMatches(RowValueFilter.create(
                    List.of(column), null, new ExpressionPredicateFactory()), expected);
        }
    }

    // They are as they were.
    @Test
    void tickOfOtherFormats() {
        for (final Format format : List.of(Format.GENERAL, Format.TEXT)) {
            final Predicate<Values> predicate = tick(format, "soon", false);
            assertThat(predicate.test(Values.of(ValString.create("soon")))).isTrue();
            assertThat(predicate.test(Values.of(ValString.create("Soon")))).isTrue();
            assertThat(tick(format, "1h", false).test(Values.of(ValString.create("60m")))).isFalse();
        }
        final Predicate<Values> predicate = tick(Format.NUMBER, "1.0", false);
        assertThat(predicate.test(Values.of(ValDouble.create(1)))).isTrue();
        assertThat(predicate.test(Values.of(ValString.create("1")))).isTrue();
    }

    private static Predicate<Values> tick(final Format format, final String value, final boolean invert) {
        final Column column = Column.builder()
                .id("c")
                .name("c")
                .format(format)
                .columnValueSelection(new ColumnValueSelection(Set.of(value), invert))
                .build();
        return RowValueFilter.create(List.of(column), null, new ExpressionPredicateFactory()).orElseThrow();
    }

    private static void assertMatches(final Optional<Predicate<Values>> predicate, final String expected) {
        assertThat(VALUES.stream()
                .filter(val -> predicate.orElseThrow().test(Values.of(val)))
                .map(String::valueOf)
                .collect(Collectors.joining(";")))
                .isEqualTo(expected == null
                        ? ""
                        : expected);
    }
}
