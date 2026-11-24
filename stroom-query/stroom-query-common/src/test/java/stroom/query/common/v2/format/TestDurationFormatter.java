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

package stroom.query.common.v2.format;

import stroom.query.api.Column;
import stroom.query.api.DurationFormatSettings;
import stroom.query.api.DurationFormatSettings.Style;
import stroom.query.api.Format;
import stroom.query.api.Format.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValDuration;
import stroom.query.language.functions.ValErr;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValPeriod;
import stroom.query.language.functions.ValString;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestDurationFormatter {

    private static final Val DURATION = ValDuration.create(178_205_000L);

    @Test
    void styles() {
        assertThat(format(null, DURATION)).isEqualTo("2d1h30m5s");
        assertThat(format(new DurationFormatSettings(null, null), DURATION)).isEqualTo("2d1h30m5s");
        assertThat(format(new DurationFormatSettings(Style.EXACT, "hh"), DURATION)).isEqualTo("2d1h30m5s");
        assertThat(format(new DurationFormatSettings(Style.ISO, "hh"), DURATION)).isEqualTo("PT49H30M5S");
        assertThat(format(pattern("d.hh:mm:ss"), DURATION)).isEqualTo("2.01:30:05");
        assertThat(format(pattern("hh:mm"), DURATION)).isEqualTo("49:30");
    }

    @Test
    void valuesThatCanBeDurations() {
        for (final Val val : List.of(
                ValLong.create(178_205_000L),
                ValDouble.create(178_205_000.9),
                ValString.create("178205000"),
                ValString.create("2d 1h 30m 5s"),
                ValString.create("PT49H30M5S"))) {
            assertThat(format(pattern("hh:mm"), val)).as(val.toString()).isEqualTo("49:30");
            assertThat(format(null, val)).as(val.toString()).isEqualTo("2d1h30m5s");
        }
        assertThat(format(pattern("hh:mm"), ValPeriod.create(0, 0, 2))).isEqualTo("48:00");
        assertThat(format(pattern("hh:mm"), ValLong.create(-5_400_000L))).isEqualTo("-01:30");
    }

    @Test
    void otherValuesAreShownAsTheyAre() {
        for (final Val val : List.of(
                ValString.create("soon"),
                ValString.create(""),
                ValString.create("1M"),
                ValPeriod.create(0, 1, 0),
                ValDate.create(0),
                ValBoolean.create(true),
                ValDouble.create(Double.NaN),
                ValErr.create("bad"))) {
            assertThat(format(pattern("hh:mm"), val)).isEqualTo(val.toString());
            assertThat(format(null, val)).isEqualTo(val.toString());
        }
        assertThat(format(pattern("hh:mm"), ValNull.INSTANCE)).isNull();
        assertThat(format(pattern("hh:mm"), null)).isNull();
    }

    // It can't come from the dialog.
    @Test
    void patternThatCantBeUsed() {
        assertThat(format(pattern("HH:mm"), DURATION)).isEqualTo("2d1h30m5s");
        assertThat(format(pattern(null), DURATION)).isEqualTo("2d1h30m5s");
    }

    @Test
    void columnFormats() {
        final FormatterFactory factory = new FormatterFactory(null);
        assertThat(factory.create(Column.builder().format(Format.DURATION).build()).format(ValLong.create(1_500L)))
                .isEqualTo("1s500ms");
        assertThat(factory.create(Column.builder()
                .format(new Format(Type.DURATION, pattern("s.f"), null))
                .build()).format(ValLong.create(1_500L)))
                .isEqualTo("1.5");
        assertThat(factory.create(Column.builder().format(Format.GENERAL).build()).format(ValLong.create(1_500L)))
                .isEqualTo("1500");
    }

    private static DurationFormatSettings pattern(final String pattern) {
        return new DurationFormatSettings(Style.PATTERN, pattern);
    }

    private static String format(final DurationFormatSettings settings, final Val val) {
        return DurationFormatter.create(settings).format(val);
    }
}
