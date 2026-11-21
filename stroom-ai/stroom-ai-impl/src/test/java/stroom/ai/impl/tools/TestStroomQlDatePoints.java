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
import stroom.query.api.ExpressionTerm.Condition;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TestStroomQlDatePoints {

    // 2026-11-21T10:42:00+11:00 in Canberra: still the 20th in UTC.
    private static final long NOW = Instant.parse("2026-11-20T23:42:00Z").toEpochMilli();
    private static final ToolTimeZone CANBERRA = new ToolTimeZone(ZoneId.of("Australia/Canberra"), Source.GIVEN,
            null);

    @Test
    void dayInAWhereIsTheZonesDay() {
        assertThat(StroomQlDatePoints.inZone("from \"V\" where EventTime > day() select A", CANBERRA, NOW))
                .isEqualTo("from \"V\" where EventTime > \"2026-11-21T00:00:00+11:00[Australia/Canberra]\" "
                           + "select A");
    }

    @Test
    void durationsAfterADatePointAreKept() {
        assertThat(StroomQlDatePoints.inZone("from V where EventTime between day() - 1d and day() + 2h",
                CANBERRA, NOW))
                .isEqualTo("from V where EventTime between \"2026-11-21T00:00:00+11:00[Australia/Canberra]\" - 1d "
                           + "and \"2026-11-21T00:00:00+11:00[Australia/Canberra]\" + 2h");
    }

    @Test
    void eachDatePoint() {
        assertThat(StroomQlDatePoints.inZone("from V where A > week() and B > month() or C > year() and "
                                             + "D > hour() and E > minute()", CANBERRA, NOW))
                .contains("A > \"2026-11-16T00:00:00+11:00[Australia/Canberra]\"")
                .contains("B > \"2026-11-01T00:00:00+11:00[Australia/Canberra]\"")
                .contains("C > \"2026-01-01T00:00:00+11:00[Australia/Canberra]\"")
                .contains("D > \"2026-11-21T10:00:00+11:00[Australia/Canberra]\"")
                .contains("E > \"2026-11-21T10:42:00+11:00[Australia/Canberra]\"");
    }

    @Test
    void nowEvalStringsAndCommentsAreLeftAsTheyAre() {
        final String query = "from V where EventTime > now() - 1h and Msg = \"day()\"\n"
                             + "// where X > day()\n"
                             + "eval Today = day()\n"
                             + "select Today";

        assertThat(StroomQlDatePoints.inZone(query, CANBERRA, NOW)).isEqualTo(query);
    }

    @Test
    void filterHavingAndSubqueryConditionsAreInTheZone() {
        final String query = "from V where U in (from W where T > day() select U) "
                             + "eval D = day() group by U select U, count() as N having N > 1 and Last > day()";
        final String inZone = StroomQlDatePoints.inZone(query, CANBERRA, NOW);

        assertThat(inZone).contains("where T > \"2026-11-21T00:00:00+11:00[Australia/Canberra]\" select U)")
                .contains("eval D = day()")
                .contains("Last > \"2026-11-21T00:00:00+11:00[Australia/Canberra]\"");
    }

    @Test
    void anOffsetZoneIsWrittenAsAnOffset() {
        final ToolTimeZone plusEleven = new ToolTimeZone(ZoneOffset.ofHours(11), Source.GIVEN, null);

        assertThat(StroomQlDatePoints.inZone("from V where T > day()", plusEleven, NOW))
                .isEqualTo("from V where T > \"2026-11-21T00:00:00+11:00\"");
    }

    @Test
    void utcAndNoQueryAreLeftAsTheyAre() {
        assertThat(StroomQlDatePoints.inZone("from V where T > day()", ToolTimeZone.UTC, NOW))
                .isEqualTo("from V where T > day()");
        assertThat(StroomQlDatePoints.inZone(null, CANBERRA, NOW)).isNull();
    }

    @Test
    void aToolsTimeArgumentIsATimeInTheZone() {
        assertThat(StroomQlDatePoints.timeInZone("day() - 7d", CANBERRA, NOW))
                .isEqualTo("2026-11-14T00:00:00+11:00");
        assertThat(StroomQlDatePoints.timeInZone("2026-11-21T02:00:00", CANBERRA, NOW))
                .isEqualTo("2026-11-21T02:00:00+11:00");
        assertThat(StroomQlDatePoints.timeInZone("now()", CANBERRA, NOW))
                .isEqualTo("2026-11-21T10:42:00+11:00");
    }

    @Test
    void aToolsTimeArgumentWithAnOffsetIsLeftAsGiven() {
        assertThat(StroomQlDatePoints.timeInZone("2026-11-21T02:00:00Z", CANBERRA, NOW))
                .isEqualTo("2026-11-21T02:00:00Z");
        assertThat(StroomQlDatePoints.timeInZone("2026-11-21T02:00:00+01:00", CANBERRA, NOW))
                .isEqualTo("2026-11-21T02:00:00+01:00");
        assertThat(StroomQlDatePoints.timeInZone("not a time", CANBERRA, NOW)).isEqualTo("not a time");
        assertThat(StroomQlDatePoints.timeInZone(null, CANBERRA, NOW)).isNull();
    }

    @Test
    void aToolsTimeArgumentInUtcIsTheTimeItStandsFor() {
        assertThat(StroomQlDatePoints.timeInZone("day() - 7d", ToolTimeZone.UTC, NOW))
                .isEqualTo("2026-11-13T00:00:00Z");
        assertThat(StroomQlDatePoints.timeInZone("2026-11-21T02:00:00", ToolTimeZone.UTC, NOW))
                .isEqualTo("2026-11-21T02:00:00Z");
    }

    @Test
    void theTimesAQuerysConditionsDatePointsStoodForAreListedOnceEach() {
        assertThat(StroomQlDatePoints.used("from V where T > day() - 1d and T < day() and U > week() eval D = "
                                           + "hour() select D", CANBERRA, NOW))
                .containsExactly("day() = 2026-11-21T00:00:00+11:00", "week() = 2026-11-16T00:00:00+11:00");
        assertThat(StroomQlDatePoints.used("from V where T > day()", ToolTimeZone.UTC, NOW))
                .containsExactly("day() = 2026-11-20T00:00:00Z");
        assertThat(StroomQlDatePoints.used("from V where T > now() select T", CANBERRA, NOW)).isEmpty();
        assertThat(StroomQlDatePoints.used("from V where \"day()\" = T", CANBERRA, NOW)).isEmpty();
        assertThat(StroomQlDatePoints.used(null, CANBERRA, NOW)).isEmpty();
    }

    @Test
    void aConditionsTimeIsInTheZoneEachOfABetweensOrAnInsValuesToo() {
        assertThat(StroomQlDatePoints.conditionInZone("2026-11-21T02:00:00", Condition.GREATER_THAN, CANBERRA, NOW))
                .isEqualTo("2026-11-21T02:00:00+11:00");
        assertThat(StroomQlDatePoints.conditionInZone("day() - 1d, 2026-11-21T02:00:00Z", Condition.BETWEEN,
                CANBERRA, NOW)).isEqualTo("2026-11-20T00:00:00+11:00,2026-11-21T02:00:00Z");
        assertThat(StroomQlDatePoints.conditionInZone("2026-11-21T02:00:00,2026-11-21T03:00:00", Condition.IN,
                CANBERRA, NOW)).isEqualTo("2026-11-21T02:00:00+11:00,2026-11-21T03:00:00+11:00");
        // Not a time: as given.
        assertThat(StroomQlDatePoints.conditionInZone("alice", Condition.EQUALS, CANBERRA, NOW)).isEqualTo("alice");
        assertThat(StroomQlDatePoints.conditionInZone("", Condition.IS_NULL, CANBERRA, NOW)).isEmpty();
    }

    @Test
    void stroomsUtcTimesAreShownInTheZone() {
        assertThat(CANBERRA.inZone("2026-11-20T14:00:00.000Z")).isEqualTo("2026-11-21T01:00:00.000+11:00");
        assertThat(CANBERRA.inZone("alice")).isEqualTo("alice");
        assertThat(CANBERRA.inZone("2026-11-20T14:00:00Z")).isEqualTo("2026-11-20T14:00:00Z");
        assertThat(ToolTimeZone.UTC.inZone("2026-11-20T14:00:00.000Z")).isEqualTo("2026-11-20T14:00:00.000Z");
    }
}
