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
import stroom.query.language.functions.ValBoolean;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValDuration;
import stroom.query.language.functions.ValErr;
import stroom.query.language.functions.ValFloat;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValPeriod;
import stroom.query.language.functions.ValString;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestFlatResultCreator {

    @Test
    void durationsAreMilliseconds() {
        final Column column = Column.builder().format(Format.DURATION).build();
        assertThat(FlatResultCreator.convert(column, ValDuration.create(1_500L))).isEqualTo(1_500D);
        assertThat(FlatResultCreator.convert(column, ValLong.create(1_500L))).isEqualTo(1_500D);
        assertThat(FlatResultCreator.convert(column, ValString.create("-1.5s"))).isEqualTo(-1_500D);
        assertThat(FlatResultCreator.convert(column, ValPeriod.create(0, 0, 1))).isEqualTo(86_400_000D);
        assertThat(FlatResultCreator.convert(column, ValString.create("soon"))).isNull();
        assertThat(FlatResultCreator.convert(column, ValNull.INSTANCE)).isNull();
        assertThat(FlatResultCreator.convert(column, ValErr.create("bad"))).isNull();
    }

    // Those that are different must stay so, or their rows would be added together.
    @Test
    void durationGroups() {
        final Column column = Column.builder().format(Format.DURATION).build();
        assertThat(FlatResultCreator.convertGroup(column, ValDuration.create(1_500L))).isEqualTo(1_500D);
        assertThat(FlatResultCreator.convertGroup(column, ValLong.create(1_500L))).isEqualTo(1_500D);
        assertThat(FlatResultCreator.convertGroup(column, ValInteger.create(1_500))).isEqualTo(1_500D);
        assertThat(FlatResultCreator.convertGroup(column, ValDouble.create(1_500))).isEqualTo(1_500D);
        assertThat(FlatResultCreator.convertGroup(column, ValDouble.create(1.2))).isEqualTo(1.2);
        assertThat(FlatResultCreator.convertGroup(column, ValDouble.create(1e300))).isEqualTo(1e300);
        assertThat(FlatResultCreator.convertGroup(column, ValFloat.create(1.5F))).isEqualTo(1.5F);
        // Not a number, which would be a long duration.
        assertThat(FlatResultCreator.convertGroup(column, ValDate.create(5))).isEqualTo("1970-01-01T00:00:00.005Z");
        assertThat(FlatResultCreator.convertGroup(column, ValBoolean.create(true))).isEqualTo("true");
        assertThat(FlatResultCreator.convertGroup(column, ValErr.create("bad"))).isEqualTo("ERR: bad");
        for (final String text : List.of("1h", "60m", "soon", "later", "1500")) {
            assertThat(FlatResultCreator.convertGroup(column, ValString.create(text))).isEqualTo(text);
        }
        assertThat(FlatResultCreator.convertGroup(column, ValPeriod.create(0, 0, 1))).isEqualTo("P1D");
        assertThat(FlatResultCreator.convertGroup(column, ValNull.INSTANCE)).isNull();
    }

    @Test
    void groupsOfOtherFormats() {
        assertThat(FlatResultCreator.convertGroup(
                Column.builder().format(Format.NUMBER).build(), ValString.create("60m"))).isEqualTo(3_600_000D);
        assertThat(FlatResultCreator.convertGroup(
                Column.builder().format(Format.TEXT).build(), ValLong.create(5))).isEqualTo("5");
        assertThat(FlatResultCreator.convertGroup(null, ValLong.create(5))).isEqualTo(5L);
    }

    @Test
    void otherFormats() {
        assertThat(FlatResultCreator.convert(
                Column.builder().format(Format.NUMBER).build(), ValDouble.create(1.5))).isEqualTo(1.5);
        assertThat(FlatResultCreator.convert(
                Column.builder().format(Format.TEXT).build(), ValDuration.create(1_500L))).isEqualTo("1s500ms");
        assertThat(FlatResultCreator.convert(
                Column.builder().format(Format.GENERAL).build(), ValDuration.create(1_500L))).isEqualTo(1_500L);
        assertThat(FlatResultCreator.convert(
                Column.builder().build(), ValString.create("1.5s"))).isEqualTo("1.5s");
    }
}
