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
import stroom.query.api.datasource.FieldType;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.Values;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestValuesFunctionFactory {

    @Test
    void fieldTypeOfEachFormat() {
        assertThat(getFieldType(null)).isEqualTo(FieldType.TEXT);
        assertThat(getFieldType(Format.GENERAL)).isEqualTo(FieldType.TEXT);
        assertThat(getFieldType(Format.TEXT)).isEqualTo(FieldType.TEXT);
        assertThat(getFieldType(Format.NUMBER)).isEqualTo(FieldType.LONG);
        assertThat(getFieldType(Format.DURATION)).isEqualTo(FieldType.LONG);
        assertThat(getFieldType(Format.DATE_TIME)).isEqualTo(FieldType.DATE);
    }

    private static FieldType getFieldType(final Format format) {
        final Column column = Column.builder().format(format).build();
        return new ValuesFunctionFactory(column, 0).getFieldType();
    }

    @Test
    void textOfDates() {
        assertThat(readDate("2024-02-29T10:00:00Z")).isEqualTo(1_709_200_800_000L);
        // Text that is not a date must not fail the rows that have one.
        for (final String text : new String[]{"2024-02-30T10:00:00Z", "N/A", "", "1.2", "2024-02-29T10:00:00"}) {
            assertThat(readDate(text)).as(text).isNull();
        }
    }

    private static Long readDate(final String text) {
        final Column column = Column.builder().format(Format.DATE_TIME).build();
        return new ValuesFunctionFactory(column, 0).createDateExtractor()
                .apply(Values.of(ValString.create(text)));
    }
}
