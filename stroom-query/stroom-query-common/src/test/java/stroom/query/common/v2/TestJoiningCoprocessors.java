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

import stroom.docref.DocRef;
import stroom.query.api.JoinKey;
import stroom.query.api.JoinType;
import stroom.query.api.JoinWindow;
import stroom.query.api.Query;
import stroom.query.api.QueryJoin;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.shared.Severity;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Joins applied to rows, checked against the same join worked out by hand.
class TestJoiningCoprocessors {

    private static final DocRef EVENTS = new DocRef("Index", "events", "events");
    private static final DocRef HR = new DocRef("DataConnection", "hr", "hr");
    private static final DocRef DEPTS = new DocRef("DataConnection", "depts", "depts");

    // Rows of the first source: UserId, Host. Some keys match several rows, some none, some are blank.
    private static final List<String[]> EVENTS_ROWS = List.of(
            new String[]{"a", "h1"},
            new String[]{"b", "h2"},
            new String[]{"c", "h3"},
            new String[]{"", "h4"},
            new String[]{null, "h5"},
            new String[]{"a", "h6"});
    // Rows of the joined source: Username, Department.
    private static final List<String[]> HR_ROWS = List.of(
            new String[]{"a", "Sales"},
            new String[]{"a", "Support"},
            new String[]{"b", "IT"},
            new String[]{"", "Blank"},
            new String[]{null, "Null"},
            new String[]{"d", "Unused"});

    @TempDir
    private Path tempDir;
    private final List<JoinTable> tables = new ArrayList<>();

    @AfterEach
    void closeTables() {
        tables.forEach(JoinTable::close);
    }

    @ParameterizedTest
    @EnumSource(JoinType.class)
    void eachKindOfJoinMatchesAJoinWorkedOutByHand(final JoinType type) {
        final Query query = query(null, join(type, "hr", HR, "${UserId}", "${hr.Username}"));
        final CapturingCoprocessors delegate = new CapturingCoprocessors("UserId", "Host", "hr.Department");
        final JoiningCoprocessors coprocessors = create(query, delegate, List.of(HR_ROWS));

        EVENTS_ROWS.forEach(row -> coprocessors.accept(eventRow(delegate, row)));

        assertThat(delegate.rows("UserId", "Host", "hr.Department")).isEqualTo(joinByHand(type));
    }

    @Test
    void joinedFieldsTheQueryDoesNotNameAreNotHeld() {
        final Query query = query(null, join(JoinType.INNER, "hr", HR, "${UserId}", "${hr.Username}"));
        final CapturingCoprocessors delegate = new CapturingCoprocessors("UserId", "hr.Department");

        final List<JoinPlan> plans = JoinPlan.createAll(
                query, delegate.getFieldIndex(), new ExpressionContext(), Map.of());

        // The key's field first, then the fields the query names.
        assertThat(plans.getFirst().getStoredFields()).containsExactly("Username", "Department");
    }

    @Test
    void chainedJoinUsesTheEarlierJoinsFields() {
        final Query query = query(null,
                join(JoinType.LEFT, "hr", HR, "${UserId}", "${hr.Username}"),
                join(JoinType.INNER, "dept", DEPTS, "${hr.Department}", "${dept.Name}"));
        final CapturingCoprocessors delegate = new CapturingCoprocessors("UserId", "dept.CostCentre");
        final List<String[]> deptRows = List.of(
                new String[]{"Sales", "CC1"},
                new String[]{"IT", "CC2"});
        final JoiningCoprocessors coprocessors = create(query, delegate, List.of(HR_ROWS, deptRows));

        EVENTS_ROWS.forEach(row -> coprocessors.accept(eventRow(delegate, row)));

        assertThat(delegate.rows("UserId", "hr.Department", "dept.CostCentre")).containsExactly(
                List.of("a", "Sales", "CC1"),
                List.of("b", "IT", "CC2"),
                List.of("a", "Sales", "CC1"));
    }

    @Test
    void firstSourceAliasFieldsTakeTheValuesOfTheFieldsTheyName() {
        final Query query = query("e", join(JoinType.INNER, "hr", HR, "${e.UserId}", "${hr.Username}"));
        final CapturingCoprocessors delegate = new CapturingCoprocessors("e.Host", "hr.Department");
        final JoiningCoprocessors coprocessors = create(query, delegate, List.of(HR_ROWS));

        // The first source's search is asked for the fields themselves.
        assertThat(delegate.getFieldIndex().getFields()).contains("UserId", "Host");
        EVENTS_ROWS.forEach(row -> coprocessors.accept(eventRow(delegate, row)));

        assertThat(delegate.rows("e.Host", "hr.Department")).containsExactly(
                List.of("h1", "Sales"),
                List.of("h1", "Support"),
                List.of("h2", "IT"),
                List.of("h6", "Sales"),
                List.of("h6", "Support"));
    }

    @Test
    void rowsFromAnExtractingSearchAreJoinedToo() {
        final Query query = query(null, join(JoinType.KEEP, "hr", HR, "${UserId}", "${hr.Username}"));
        final CapturingCoprocessors delegate = new CapturingCoprocessors("UserId", "Host");
        final JoiningCoprocessors coprocessors = create(query, delegate, List.of(HR_ROWS));

        coprocessors.forEachExtractionCoprocessor((docRef, set) ->
                EVENTS_ROWS.forEach(row -> set.forEach(coprocessor -> coprocessor.accept(eventRow(delegate, row)))));

        assertThat(delegate.rows("UserId", "Host")).containsExactly(
                List.of("a", "h1"),
                List.of("b", "h2"),
                List.of("a", "h6"));
    }

    @Test
    void rowWithVeryManyMatchesGivesAWarningOnce() {
        final Query query = query(null, join(JoinType.INNER, "hr", HR, "${UserId}", "${hr.Username}"));
        final CapturingCoprocessors delegate = new CapturingCoprocessors("UserId", "hr.Department");
        final List<String[]> manyRows = new ArrayList<>();
        for (int i = 0; i <= JoinProbe.MATCH_WARNING_COUNT; i++) {
            manyRows.add(new String[]{"a", "D" + i});
        }
        final JoiningCoprocessors coprocessors = create(query, delegate, List.of(manyRows));

        coprocessors.accept(eventRow(delegate, new String[]{"a", "h1"}));
        coprocessors.accept(eventRow(delegate, new String[]{"a", "h2"}));

        assertThat(delegate.rows("UserId")).hasSize(2 * (JoinProbe.MATCH_WARNING_COUNT + 1));
        assertThat(delegate.errorConsumer.getErrorMessages())
                .singleElement()
                .satisfies(message -> {
                    assertThat(message.getSeverity()).isEqualTo(Severity.WARNING);
                    assertThat(message.getMessage()).contains("matched 1,001 rows of 'hr'");
                });
    }

    @Test
    void joinNamingTooManyFieldsIsRefusedWhenPlanned() {
        final Query query = query(null, join(JoinType.INNER, "hr", HR, "${UserId}", "${hr.Username}"));
        final FieldIndex fieldIndex = new FieldIndex();
        for (int i = 0; i < 300; i++) {
            fieldIndex.create("hr.Field" + i);
        }

        assertThatThrownBy(() -> JoinPlan.createAll(query, fieldIndex, new ExpressionContext(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 255 fields of 'hr'");
    }

    @Test
    void windowedJoinIsRefusedForNow() {
        final QueryJoin windowed = new QueryJoin(JoinType.INNER, "x", Query.builder().dataSource(EVENTS).build(),
                null, List.of(new JoinKey("${UserId}", "${x.UserId}")), new JoinWindow("10m", true, "EventTime"));

        assertThatThrownBy(() -> JoinPlan.createAll(
                query(null, windowed), new FieldIndex(), new ExpressionContext(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("time window");
    }

    // The join of EVENTS_ROWS to HR_ROWS on UserId = Username, as rows of UserId, Host, Department.
    private static List<List<String>> joinByHand(final JoinType type) {
        final List<List<String>> rows = new ArrayList<>();
        for (final String[] event : EVENTS_ROWS) {
            final List<String> departments = new ArrayList<>();
            for (final String[] hr : HR_ROWS) {
                if (isKey(event[0]) && event[0].equals(hr[0])) {
                    departments.add(hr[1]);
                }
            }
            switch (type) {
                case INNER -> departments.forEach(department -> rows.add(asList(event[0], event[1], department)));
                case LEFT -> {
                    departments.forEach(department -> rows.add(asList(event[0], event[1], department)));
                    if (departments.isEmpty()) {
                        rows.add(asList(event[0], event[1], null));
                    }
                }
                case KEEP -> {
                    if (!departments.isEmpty()) {
                        rows.add(asList(event[0], event[1], null));
                    }
                }
                case DROP -> {
                    if (departments.isEmpty()) {
                        rows.add(asList(event[0], event[1], null));
                    }
                }
            }
        }
        return rows;
    }

    private static boolean isKey(final String value) {
        return value != null && !value.isBlank();
    }

    private static List<String> asList(final String... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private JoiningCoprocessors create(final Query query,
                                       final CapturingCoprocessors delegate,
                                       final List<List<String[]>> joinedRows) {
        final List<JoinPlan> plans = JoinPlan.createAll(
                query, delegate.getFieldIndex(), new ExpressionContext(), Map.of());
        final List<JoinTable> joinTables = new ArrayList<>();
        for (int i = 0; i < plans.size(); i++) {
            final JoinPlan plan = plans.get(i);
            final JoinTable table = new JoinTable(
                    plan.getJoin().getAlias(),
                    plan.getStoredFields(),
                    JoinTestHelper.createLmdbEnvBuilder(tempDir),
                    JoinLimits.DEFAULT);
            tables.add(table);
            for (final String[] source : joinedRows.get(i)) {
                // The source's rows have fields Username/Name then Department/CostCentre.
                final Val[] row = storedRow(plan.getStoredFields(), source);
                table.add(plan.getRightKey(row), row);
            }
            table.complete();
            joinTables.add(table);
        }
        return JoiningCoprocessors.create(delegate, query, plans, joinTables);
    }

    // A joined source's row for the fields its table holds. The test sources have a key field then
    // one other field.
    private static Val[] storedRow(final List<String> storedFields, final String[] source) {
        final String[] values = new String[storedFields.size()];
        for (int i = 0; i < storedFields.size(); i++) {
            final String field = storedFields.get(i);
            values[i] = "Username".equals(field) || "Name".equals(field)
                    ? source[0]
                    : source[1];
        }
        return JoinTestHelper.row(values);
    }

    // A row of the first source, with UserId and Host at their positions and nothing else.
    private static Val[] eventRow(final CapturingCoprocessors delegate, final String[] event) {
        final FieldIndex fieldIndex = delegate.getFieldIndex();
        final Val[] row = new Val[fieldIndex.size()];
        final Val[] values = JoinTestHelper.row(event);
        final Integer userId = fieldIndex.getPos("UserId");
        final Integer host = fieldIndex.getPos("Host");
        if (userId != null) {
            row[userId] = values[0];
        }
        if (host != null) {
            row[host] = values[1];
        }
        return row;
    }

    private static Query query(final String alias, final QueryJoin... joins) {
        return Query.builder()
                .dataSource(EVENTS)
                .alias(alias)
                .joins(List.of(joins))
                .build();
    }

    private static QueryJoin join(final JoinType type,
                                  final String alias,
                                  final DocRef dataSource,
                                  final String left,
                                  final String right) {
        return new QueryJoin(
                type,
                alias,
                Query.builder().dataSource(dataSource).build(),
                null,
                List.of(new JoinKey(left, right)),
                null);
    }


    // --------------------------------------------------------------------------------


    // Coprocessors that keep the rows they are given, as the query's coprocessors would take them.
    private static final class CapturingCoprocessors implements Coprocessors {

        private final FieldIndex fieldIndex = new FieldIndex();
        private final ErrorConsumerImpl errorConsumer = new ErrorConsumerImpl();
        private final List<Val[]> captured = new ArrayList<>();

        private CapturingCoprocessors(final String... fields) {
            for (final String field : fields) {
                fieldIndex.create(field);
            }
        }

        // The captured rows' values of the fields, as text, with null for a missing value.
        private List<List<String>> rows(final String... fields) {
            final List<List<String>> rows = new ArrayList<>();
            for (final Val[] row : captured) {
                final List<String> values = new ArrayList<>();
                for (final String field : fields) {
                    final int pos = Objects.requireNonNull(fieldIndex.getPos(field));
                    final Val val = pos < row.length
                            ? row[pos]
                            : null;
                    values.add(val == null || !val.type().isValue()
                            ? null
                            : val.toString());
                }
                rows.add(values);
            }
            return rows;
        }

        @Override
        public void accept(final Val[] values) {
            captured.add(values);
        }

        @Override
        public FieldIndex getFieldIndex() {
            return fieldIndex;
        }

        @Override
        public ErrorConsumer getErrorConsumer() {
            return errorConsumer;
        }

        @Override
        public ExpressionContext getExpressionContext() {
            return new ExpressionContext();
        }

        @Override
        public void forEachExtractionCoprocessor(final BiConsumer<DocRef, Set<Coprocessor>> consumer) {
            final Set<Coprocessor> set = new HashSet<>();
            set.add(new CapturingCoprocessor(this));
            consumer.accept(null, set);
        }

        @Override
        public void readPayloads(final Input input) {
        }

        @Override
        public void writePayloads(final Output output) {
        }

        @Override
        public Coprocessor get(final int coprocessorId) {
            return null;
        }

        @Override
        public boolean isPresent() {
            return true;
        }

        @Override
        public long getByteSize() {
            return 0;
        }
    }


    // --------------------------------------------------------------------------------


    // A coprocessor of an extraction group, keeping rows in the coprocessors it belongs to.
    private record CapturingCoprocessor(CapturingCoprocessors owner) implements Coprocessor {

        @Override
        public void accept(final Val[] values) {
            owner.accept(values);
        }

        @Override
        public ErrorConsumer getErrorConsumer() {
            return owner.getErrorConsumer();
        }

        @Override
        public void clear() {
        }

        @Override
        public void readPayload(final Input input) {
        }

        @Override
        public void writePayload(final Output output) {
        }

        @Override
        public long getByteSize() {
            return 0;
        }
    }
}
