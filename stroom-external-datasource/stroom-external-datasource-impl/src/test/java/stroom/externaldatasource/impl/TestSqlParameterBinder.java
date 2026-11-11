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

package stroom.externaldatasource.impl;

import stroom.query.api.DateTimeSettings;
import stroom.query.api.Param;
import stroom.query.api.TimeRange;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSqlParameterBinder {

    private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-11-10T12:30:00Z");
    private static final TimeRange TIME_RANGE = new TimeRange(null, null, FROM.toString(), TO.toString());
    private static final DateTimeSettings DATE_TIME_SETTINGS = DateTimeSettings.builder()
            .referenceTime(TO.toEpochMilli())
            .build();

    private static List<Object> resolve(final String sql, final TimeRange timeRange, final List<Param> params) {
        return SqlParameterBinder.resolveValues(
                SqlStatementParser.parse(sql), timeRange, params, DATE_TIME_SETTINGS);
    }

    @Test
    void timeRangeIsBoundAsInstants() {
        final List<Object> values = resolve("select * from t where a >= :from and a < :to", TIME_RANGE, null);

        assertThat(values).containsExactly(FROM, TO);
    }

    @Test
    void relativeTimeIsWorkedOutFromTheReferenceTime() {
        final TimeRange lastDay = new TimeRange(null, null, "now() - 1d", "now()");

        final List<Object> values = resolve("select :from, :to", lastDay, null);

        assertThat(values).containsExactly(TO.minusSeconds(86_400), TO);
    }

    @Test
    void formattedTimeIsBoundAsTextInUtc() {
        final List<Object> values = resolve("select :from{yyyy-MM-dd}, :to{yyyy-MM-dd HH:mm}", TIME_RANGE, null);

        assertThat(values).containsExactly("2026-11-01", "2026-11-10 12:30");
    }

    @Test
    void epochFormatsAreBoundAsNumbers() {
        final List<Object> values = resolve("select :from{epochMillis}, :to{epochSeconds}", TIME_RANGE, null);

        assertThat(values).containsExactly(FROM.toEpochMilli(), TO.getEpochSecond());
    }

    @Test
    void otherPlaceholdersTakeQueryParameters() {
        final List<Object> values = resolve(
                "select * from t where feed = :feed and user = :user",
                null,
                List.of(new Param("feed", "TEST-EVENTS"), new Param("user", "jbloggs")));

        assertThat(values).containsExactly("TEST-EVENTS", "jbloggs");
    }

    @Test
    void parameterValueIsNeverPastedIntoTheSql() {
        final ParsedSql parsed = SqlStatementParser.parse("select * from t where name = :name");

        final List<Object> values = SqlParameterBinder.resolveValues(
                parsed, null, List.of(new Param("name", "x' or '1'='1")), DATE_TIME_SETTINGS);

        assertThat(parsed.jdbcSql()).isEqualTo("select * from t where name = ?");
        assertThat(values).containsExactly("x' or '1'='1");
    }

    @Test
    void missingParameterIsNamed() {
        assertThatThrownBy(() -> resolve("select :feed", null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(":feed")
                .hasMessageContaining("parameter named 'feed'");
    }

    @Test
    void missingTimeRangeIsNamed() {
        assertThatThrownBy(() -> resolve("select :from", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(":from")
                .hasMessageContaining("time range");
    }

    @Test
    void openEndedTimeRangeLeavesThatEndWithoutAValue() {
        final TimeRange fromOnly = new TimeRange(null, null, FROM.toString(), null);

        assertThat(resolve("select :from", fromOnly, null)).containsExactly(FROM);
        assertThatThrownBy(() -> resolve("select :to", fromOnly, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(":to");
    }

    @Test
    void formatOnAParameterIsRefused() {
        assertThatThrownBy(() -> resolve("select :feed{upper}", null, List.of(new Param("feed", "x"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only :from and :to");
    }

    @Test
    void badDatePatternIsReported() {
        assertThatThrownBy(() -> resolve("select :from{yyyy-MM-dd'}", TIME_RANGE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(":from{yyyy-MM-dd'}");
    }

    @Test
    void bindSetsEachValueByType() throws SQLException {
        final PreparedStatement statement = Mockito.mock(PreparedStatement.class);

        SqlParameterBinder.bind(statement, List.of(FROM, 42L, "text"));

        Mockito.verify(statement).setTimestamp(
                Mockito.eq(1), Mockito.eq(Timestamp.from(FROM)), Mockito.any(Calendar.class));
        Mockito.verify(statement).setLong(2, 42L);
        Mockito.verify(statement).setString(3, "text");
    }
}
