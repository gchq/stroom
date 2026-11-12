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

import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.FieldType;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSqlRowConsumer {

    private static final List<SqlColumn> COLUMNS = List.of(
            new SqlColumn("id", FieldType.INTEGER, Types.INTEGER),
            new SqlColumn("NAME", FieldType.TEXT, Types.VARCHAR),
            new SqlColumn("joined", FieldType.DATE, Types.TIMESTAMP));

    private static final Val[][] ROWS = {
            {ValInteger.create(1), ValString.create("Smith"), ValDate.create(Instant.parse("2020-01-01T00:00:00Z"))},
            {ValInteger.create(2), ValString.create("Jones"), ValDate.create(Instant.parse("2024-06-01T00:00:00Z"))},
            {ValInteger.create(10), ValString.create("Brown"), ValDate.create(Instant.parse("2026-01-01T00:00:00Z"))}};

    private final List<Val[]> received = new ArrayList<>();

    private SqlRowConsumer create(final FieldIndex fieldIndex, final ExpressionOperator expression) {
        return SqlRowConsumer.create(
                COLUMNS,
                fieldIndex,
                expression,
                DateTimeSettings.builder().build(),
                new ExpressionPredicateFactory(),
                received::add);
    }

    private static FieldIndex fields(final String... names) {
        final FieldIndex fieldIndex = new FieldIndex();
        for (final String name : names) {
            fieldIndex.create(name);
        }
        return fieldIndex;
    }

    private static void feed(final SqlRowConsumer consumer) throws SQLException {
        for (final Val[] row : ROWS) {
            consumer.accept(columnIndex -> row[columnIndex]);
        }
    }

    @Test
    void fieldsTakeTheirColumnsInFieldOrder() throws SQLException {
        final SqlRowConsumer consumer = create(fields("joined", "id"), null);

        feed(consumer);

        assertThat(received).hasSize(3);
        assertThat(received.getFirst()).containsExactly(ROWS[0][2], ROWS[0][0]);
        assertThat(consumer.getUnmatchedFields()).isEmpty();
    }

    @Test
    void onlyTheColumnsUsedAreRead() throws SQLException {
        final List<Integer> columnsRead = new ArrayList<>();
        final SqlRowConsumer consumer = create(fields("id"), null);

        consumer.accept(columnIndex -> {
            columnsRead.add(columnIndex);
            return ROWS[0][columnIndex];
        });

        assertThat(columnsRead).containsExactly(0);
    }

    @Test
    void nameMatchesWithoutRegardToCaseWhenNotExact() throws SQLException {
        final SqlRowConsumer consumer = create(fields("name"), null);

        feed(consumer);

        assertThat(received.getFirst()).containsExactly(ValString.create("Smith"));
    }

    @Test
    void exactNameWinsOverCaseInsensitiveOnes() {
        final List<SqlColumn> columns = List.of(
                new SqlColumn("Name", FieldType.TEXT, Types.VARCHAR),
                new SqlColumn("name", FieldType.TEXT, Types.VARCHAR));

        assertThat(SqlRowConsumer.findColumn(columns, "name")).isEqualTo(1);
        assertThat(SqlRowConsumer.findColumn(columns, "NAME"))
                .as("two columns match without regard to case, so neither is chosen")
                .isEqualTo(-1);
    }

    @Test
    void fieldWithNoColumnIsEmptyAndListed() throws SQLException {
        final SqlRowConsumer consumer = create(fields("id", "nickname", "StreamId"), null);

        feed(consumer);

        assertThat(received.getFirst()).containsExactly(ROWS[0][0], ValNull.INSTANCE, ValNull.INSTANCE);
        assertThat(consumer.getUnmatchedFields())
                .as("Stroom's own linking fields are not reported")
                .containsExactly("nickname");
    }

    @Test
    void annotationFieldsAreLeftForAnnotationDecoration() throws SQLException {
        // Every table has a hidden 'annotation:Id' field, so it must not be reported as missing.
        final SqlRowConsumer consumer = create(fields("id", "annotation:Id"), null);

        feed(consumer);

        assertThat(received.getFirst()).containsExactly(ROWS[0][0], ValNull.INSTANCE);
        assertThat(consumer.getUnmatchedFields()).isEmpty();
    }

    @Test
    void textConditionFiltersRows() throws SQLException {
        final SqlRowConsumer consumer = create(fields("id"), ExpressionOperator.builder()
                .addTerm("NAME", Condition.EQUALS, "jones")
                .build());

        feed(consumer);

        // The expression's field is added to the row, after the query's own fields.
        assertThat(received).hasSize(1);
        assertThat(received.getFirst()[0]).isEqualTo(ValInteger.create(2));
    }

    @Test
    void numericConditionComparesAsNumbers() throws SQLException {
        // As text, "10" sorts before "2".
        final SqlRowConsumer consumer = create(fields("id"), ExpressionOperator.builder()
                .addTerm("id", Condition.GREATER_THAN, "2")
                .build());

        feed(consumer);

        assertThat(received).hasSize(1);
        assertThat(received.getFirst()[0]).isEqualTo(ValInteger.create(10));
    }

    @Test
    void dateConditionComparesAsDates() throws SQLException {
        final SqlRowConsumer consumer = create(fields("id"), ExpressionOperator.builder()
                .addTerm("joined", Condition.GREATER_THAN_OR_EQUAL_TO, "2024-01-01T00:00:00.000Z")
                .build());

        feed(consumer);

        assertThat(received)
                .extracting(row -> row[0])
                .containsExactly(ValInteger.create(2), ValInteger.create(10));
    }

    @Test
    void whereFieldThatIsNotAColumnIsRefused() {
        assertThatThrownBy(() -> create(fields("id"), ExpressionOperator.builder()
                .addTerm("surname", Condition.EQUALS, "Jones")
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'surname'")
                .hasMessageContaining("id, NAME, joined");
    }

    @Test
    void disabledTermIsIgnored() throws SQLException {
        final SqlRowConsumer consumer = create(fields("id"), ExpressionOperator.builder()
                .addTerm(ExpressionTerm.builder()
                        .field("surname")
                        .condition(Condition.EQUALS)
                        .value("Jones")
                        .enabled(false)
                        .build())
                .build());

        feed(consumer);

        assertThat(received).hasSize(3);
    }
}
