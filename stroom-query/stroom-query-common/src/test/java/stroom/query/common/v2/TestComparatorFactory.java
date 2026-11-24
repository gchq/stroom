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
import stroom.query.api.Format;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDuration;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValPeriod;
import stroom.query.language.functions.ValString;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestComparatorFactory {

    private static final List<Val> PERIODS = List.of(
            ValPeriod.create(0, 0, 1),
            ValPeriod.create(0, 1, 0),
            ValPeriod.create(0, 2, 0),
            ValPeriod.create(0, 10, 0),
            ValPeriod.create(1, 0, 0),
            ValPeriod.create(0, 0, 400));

    @Test
    void periodsSortByLength() {
        assertSorted(Column.builder().build(), PERIODS);
        assertSorted(Column.builder().format(Format.GENERAL).build(), PERIODS);
        assertSorted(Column.builder().format(Format.NUMBER).build(), PERIODS);
    }

    @Test
    void periodsSortAmongOtherLengths() {
        assertSorted(Column.builder().build(), List.of(
                ValLong.create(5),
                ValDuration.create(3_600_000L),
                ValPeriod.create(0, 0, 1),
                ValDuration.create(90_000_000L),
                ValPeriod.create(0, 1, 0),
                ValString.create("apple"),
                ValString.create("Banana")));
    }

    // As they do in a general column. Reading text as a duration is too slow to do for each comparison.
    @Test
    void durationColumn() {
        final Column column = Column.builder().format(Format.DURATION).build();
        assertSorted(column, List.of(
                ValDuration.create(-5),
                ValLong.create(1_000L),
                ValString.create("5s"),
                ValDuration.create(9_000L),
                ValDuration.create(10_000L),
                ValString.create("PT1H"),
                ValPeriod.create(0, 0, 1),
                ValString.create("apple"),
                ValString.create("Banana")));
        assertThat(ComparatorFactory.create(column)).isSameAs(ComparatorFactory.create(Column.builder().build()));
    }

    @Test
    void otherValuesSortAsBefore() {
        assertSorted(Column.builder().build(), List.of(
                ValLong.create(3),
                ValString.create("20"),
                ValLong.create(100),
                ValString.create("foo"),
                ValNull.INSTANCE));
        assertSorted(Column.builder().format(Format.TEXT).build(), List.of(
                ValLong.create(100),
                ValLong.create(20),
                ValLong.create(3)));
    }

    private static void assertSorted(final Column column, final List<Val> expected) {
        final List<Val> values = new ArrayList<>(expected.reversed());
        values.sort(ComparatorFactory.create(column));
        assertThat(values).isEqualTo(expected);
    }
}
