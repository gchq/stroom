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

package stroom.ai.impl.tools;

import stroom.ai.impl.tools.ToolTimeZone.Source;
import stroom.ai.impl.workspace.TableExpression;
import stroom.query.api.DateTimeSettings;
import stroom.util.date.DateUtil;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestDateFunctionZones {

    private static final ToolTimeZone CANBERRA = new ToolTimeZone(ZoneId.of("Australia/Canberra"), Source.GIVEN,
            null);

    @Test
    void aCallWithoutAZoneGetsTheCallsZone() {
        assertThat(DateFunctionZones.withZone("from V eval H = formatDate(EventTime, 'HH') select H", CANBERRA))
                .isEqualTo("from V eval H = formatDate(EventTime, 'HH', 'Australia/Canberra') select H");
        assertThat(DateFunctionZones.withZone("parseDate(${t}, \"yyyy-MM-dd HH:mm\")", CANBERRA))
                .isEqualTo("parseDate(${t}, \"yyyy-MM-dd HH:mm\", 'Australia/Canberra')");
        // Any case, and spaces before the bracket; nested calls each get it.
        assertThat(DateFunctionZones.withZone("FORMATDATE (parseDate(${t}, 'H'), concat('a', ','))", CANBERRA))
                .isEqualTo("FORMATDATE (parseDate(${t}, 'H', 'Australia/Canberra'), concat('a', ','), "
                           + "'Australia/Canberra')");
    }

    @Test
    void aCallWithOnlyADateGetsStroomsDefaultPatternAndTheZone() {
        assertThat(DateFunctionZones.withZone("formatDate(EventTime)", CANBERRA))
                .isEqualTo("formatDate(EventTime, \"" + DateUtil.DEFAULT_PATTERN + "\", 'Australia/Canberra')");
    }

    @Test
    void aCallNamingAZoneStringsCommentsAndUtcAreLeftAsTheyAre() {
        final String named = "formatDate(EventTime, 'HH', 'Europe/London')";
        assertThat(DateFunctionZones.withZone(named, CANBERRA)).isEqualTo(named);
        final String quoted = "from V where Message = 'formatDate(x, y)' // formatDate(x, y)\n"
                              + "/* parseDate(a, b) */ select Message";
        assertThat(DateFunctionZones.withZone(quoted, CANBERRA)).isEqualTo(quoted);
        assertThat(DateFunctionZones.withZone("myformatDate(x, y)", CANBERRA)).isEqualTo("myformatDate(x, y)");
        assertThat(DateFunctionZones.withZone("formatDate(x, 'HH')", ToolTimeZone.UTC))
                .isEqualTo("formatDate(x, 'HH')");
        assertThat(DateFunctionZones.withZone("formatDate()", CANBERRA)).isEqualTo("formatDate()");
        assertThat(DateFunctionZones.withZone("formatDate(x, 'HH'", CANBERRA)).isEqualTo("formatDate(x, 'HH'");
        assertThat(DateFunctionZones.withZone(null, CANBERRA)).isNull();
    }

    @Test
    void theTablesExpressionsGiveTheZonesTimes() {
        // 01:30Z is 12:30 in Canberra (+11:00 in January).
        final List<String> header = List.of("t");
        final List<String> row = List.of("2026-01-01T01:30:00.000Z");
        assertThat(TableExpression.compile(DateFunctionZones.withZone("formatDate(${t}, 'HH:mm')", CANBERRA), header,
                DateTimeSettings.builder().build()).evaluate(row)).isEqualTo("12:30");
        assertThat(TableExpression.compile(DateFunctionZones.withZone("formatDate(${t})", CANBERRA), header,
                DateTimeSettings.builder().build()).evaluate(row)).startsWith("2026-01-01T12:30:00");
    }
}
