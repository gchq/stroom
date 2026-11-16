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

package stroom.dataconnection.impl;

import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.dataconnection.shared.JdbcConnectionSettings;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.Query;
import stroom.query.api.TimeRange;
import stroom.query.api.datasource.FieldType;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Types;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestJdbcConnector {

    private static final TimeRange TIME_RANGE = new TimeRange(null, "2026-11-01T00:00:00.000Z",
            "2026-11-10T00:00:00.000Z");
    private static final ExpressionOperator WHERE = ExpressionOperator.builder()
            .addTerm("name", Condition.EQUALS, "Jones")
            .build();

    private final JdbcQueryRunner runner = Mockito.mock(JdbcQueryRunner.class);
    private final SqlFieldCache fieldCache = Mockito.mock(SqlFieldCache.class);
    private final JdbcConnector connector = new JdbcConnector(
            runner,
            Mockito.mock(JdbcConnectionResolver.class),
            null,
            fieldCache);

    private static DataConnectionDoc doc(final String timeField) {
        return DataConnectionDoc.builder()
                .uuid("uuid-1")
                .name("Events")
                .jdbc(JdbcConnectionSettings.builder()
                        .url("jdbc:mysql://reporting-db:3306/events")
                        .build())
                .timeField(timeField)
                .build();
    }

    private static Query query(final String sql, final TimeRange timeRange) {
        return Query.builder()
                .nativeQuery(sql)
                .timeRange(timeRange)
                .expression(WHERE)
                .build();
    }

    @Test
    void timeRangeIgnoredWithoutPlaceholderOrTimeColumnIsWarned() {
        final PreparedQuery prepared = connector.prepare(
                doc(null),
                query("select * from events", TIME_RANGE),
                DateTimeSettings.builder().build());

        assertThat(prepared.warnings()).containsExactly(JdbcConnector.TIME_RANGE_IGNORED);
        assertThat(prepared.expression()).isSameAs(WHERE);
    }

    @Test
    void timeRangeIsFilteredOnTheTimeColumn() {
        final PreparedQuery prepared = connector.prepare(
                doc("event_time"),
                query("select * from events", TIME_RANGE),
                DateTimeSettings.builder().build());

        assertThat(prepared.warnings()).isEmpty();
        assertThat(prepared.expression().op()).isEqualTo(Op.AND);
        assertThat(prepared.expression().getChildren()).hasSize(3);
    }

    @Test
    void timeRangeTakenByTheSqlIsNeitherWarnedNorFiltered() {
        final PreparedQuery prepared = connector.prepare(
                doc("event_time"),
                query("select * from events where event_time >= :from", TIME_RANGE),
                DateTimeSettings.builder().build());

        assertThat(prepared.warnings()).isEmpty();
        assertThat(prepared.expression()).isSameAs(WHERE);
    }

    @Test
    @SuppressWarnings("unchecked")
    void runCachesTheColumnsAndChecksTheTimeColumn() throws Exception {
        // The SQL does not return the time column the time range is filtered on.
        final List<SqlColumn> columns = List.of(new SqlColumn("name", FieldType.TEXT, Types.VARCHAR));
        Mockito.when(runner.run(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> {
                    ((Function<List<SqlColumn>, SqlRowConsumer>) invocation.getArgument(3)).apply(columns);
                    return null;
                });
        final String sql = "select name from events";
        final PreparedQuery prepared = connector.prepare(
                doc("event_time"),
                query(sql, TIME_RANGE),
                DateTimeSettings.builder().build());

        assertThatThrownBy(() -> prepared.run().run(ignored -> null, new QueryCancellation(), message -> {
        }))
                .hasMessageContaining("does not return the time column 'event_time'");
        Mockito.verify(fieldCache).put(Mockito.any(), Mockito.eq(sql), Mockito.eq(columns));
    }

    @Test
    void documentWithoutUrlIsRefused() {
        final DataConnectionDoc doc = DataConnectionDoc.builder()
                .uuid("uuid-1")
                .name("Events")
                .build();

        assertThatThrownBy(() -> connector.prepare(doc, query("select 1", null), DateTimeSettings.builder().build()))
                .hasMessageContaining("has no JDBC URL");
        Mockito.verifyNoInteractions(runner);
    }

    @Test
    void minimumRefreshIsNoneUnlessSet() {
        assertThat(connector.getMinRefreshMinutes(doc(null))).isZero();
        assertThat(connector.getMinRefreshMinutes(DataConnectionDoc.builder()
                .uuid("uuid-1")
                .jdbc(JdbcConnectionSettings.builder()
                        .minRefreshMinutes(3)
                        .build())
                .build())).isEqualTo(3);
    }
}
