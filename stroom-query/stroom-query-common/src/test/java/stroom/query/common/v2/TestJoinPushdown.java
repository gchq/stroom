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
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.JoinKey;
import stroom.query.api.JoinType;
import stroom.query.api.Query;
import stroom.query.api.QueryJoin;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestJoinPushdown {

    private static final DocRef EVENTS = DocRef.builder().type("Index").uuid("events").name("events").build();
    private static final DocRef HR = DocRef.builder().type("Index").uuid("hr").name("hr").build();
    private static final ExpressionOperator WHERE = ExpressionOperator.builder()
            .addTerm(ExpressionTerm.builder().field("Host").equals().value("h1").build())
            .build();

    private final List<QueryField> fields = new ArrayList<>();
    private SearchProvider searchProvider;
    private JoinPushdown joinPushdown;

    @BeforeEach
    void setUp() {
        searchProvider = Mockito.mock(SearchProvider.class);
        Mockito.when(searchProvider.getFieldInfo(Mockito.any()))
                .thenAnswer(invocation -> ResultPage.createUnboundedList(fields));
        joinPushdown = new JoinPushdown(dataSource -> EVENTS.equals(dataSource)
                ? Optional.of(searchProvider)
                : Optional.empty());
        fields.add(QueryField.createKeyword("UserId", true));
        fields.add(QueryField.builder()
                .fldName("Count")
                .fldType(FieldType.LONG)
                .conditionSet(ConditionSet.DEFAULT_ID)
                .queryable(true)
                .build());
        fields.add(QueryField.createLong("NoIn", true));
        fields.add(QueryField.builder()
                .fldName("Small")
                .fldType(FieldType.INTEGER)
                .conditionSet(ConditionSet.DEFAULT_ID)
                .queryable(true)
                .build());
        fields.add(QueryField.createId("StreamId", true));
        fields.add(QueryField.createKeyword("NotIndexed", false));
        fields.add(QueryField.createDate("EventTime", true));
    }

    @Test
    void keepJoinAddsAnInTerm() {
        final Query query = query(JoinType.KEEP, "${UserId}");

        final Query pushedDown = joinPushdown.apply(query, List.of(keys("a", "b")), Map.of());

        assertThat(pushedDown.getExpression()).isEqualTo(ExpressionOperator.builder()
                .addOperator(WHERE)
                .addOperator(ExpressionOperator.builder().addTerm(in("UserId", "a,b")).build())
                .build());
    }

    @Test
    void innerJoinAddsAnInTerm() {
        final Query pushedDown = joinPushdown.apply(query(JoinType.INNER, "${UserId}"), List.of(keys("a")), Map.of());

        assertThat(pushedTerms(pushedDown)).containsExactly(in("UserId", "a"));
    }

    @Test
    void queryWithNoWhereGetsOnlyTheInTerm() {
        final Query query = query(JoinType.KEEP, "${UserId}").copy().expression(null).build();

        final Query pushedDown = joinPushdown.apply(query, List.of(keys("a")), Map.of());

        assertThat(pushedDown.getExpression()).isEqualTo(ExpressionOperator.builder()
                .addOperator(ExpressionOperator.builder().addTerm(in("UserId", "a")).build())
                .build());
    }

    @Test
    void joinedSourceIsNarrowedToTheAnchorsKeys() {
        final Query joined = Query.builder().dataSource(EVENTS).expression(WHERE).build();
        final QueryJoin join = query(JoinType.LEFT, "${UserId}").getJoins().getFirst();

        final Query narrowed = joinPushdown.narrowJoinedSource(
                withRightKey(join, "${hr.UserId}"), joined, collected("a", "b"), Map.of(), JoinPushdown.MAX_KEYS);

        assertThat(pushedTerms(narrowed)).containsExactly(in("UserId", "a,b"));
    }

    @Test
    void joinedSourceIsNotNarrowedWhenItCantBe() {
        final Query joined = Query.builder().dataSource(EVENTS).expression(WHERE).build();
        final QueryJoin join = query(JoinType.INNER, "${UserId}").getJoins().getFirst();

        // An expression, a parameter, a field without the join's alias, a key that isn't plain and
        // more keys than allowed.
        assertThat(joinPushdown.narrowJoinedSource(
                withRightKey(join, "lowerCase(${hr.UserId})"), joined, collected("a"), Map.of(), 10))
                .isSameAs(joined);
        assertThat(joinPushdown.narrowJoinedSource(
                withRightKey(join, "${hr.UserId}"), joined, collected("a"), Map.of("hr.UserId", "x"), 10))
                .isSameAs(joined);
        assertThat(joinPushdown.narrowJoinedSource(
                withRightKey(join, "${UserId}"), joined, collected("a"), Map.of(), 10))
                .isSameAs(joined);
        assertThat(joinPushdown.narrowJoinedSource(
                withRightKey(join, "${hr.UserId}"), joined, collected("a b"), Map.of(), 10))
                .isSameAs(joined);
        assertThat(joinPushdown.narrowJoinedSource(
                withRightKey(join, "${hr.UserId}"), joined, collected("a", "b"), Map.of(), 1))
                .isSameAs(joined);
        assertThat(joinPushdown.narrowJoinedSource(
                withRightKey(join, "${hr.UserId}"), joined, collected(), Map.of(), 10))
                .isSameAs(joined);
    }

    @Test
    void queryWithOnlyAnAliasIsLeftAlone() {
        final Query query = Query.builder().dataSource(EVENTS).alias("e").expression(WHERE).build();

        assertThat(joinPushdown.apply(query, List.of(), Map.of())).isSameAs(query);
    }

    @Test
    void leftJoinIsNotPushedDown() {
        final Query query = query(JoinType.LEFT, "${UserId}");

        assertThat(joinPushdown.apply(query, List.of(keys("a")), Map.of())).isSameAs(query);
    }

    @Test
    void dropJoinOnAnIdFieldAddsANotInTerm() {
        final Query pushedDown = joinPushdown.apply(
                query(JoinType.DROP, "${StreamId}"), List.of(keys("1", "22")), Map.of());

        final ExpressionOperator added = (ExpressionOperator) pushedDown.getExpression().getChildren().getLast();
        assertThat(added.op()).isEqualTo(Op.NOT);
        assertThat(added.getChildren()).containsExactly(in("StreamId", "1,22"));
    }

    @Test
    void dropJoinOnATextFieldIsNotPushedDown() {
        // A data source that ignores case would drop rows whose case differs, which the join keeps.
        final Query query = query(JoinType.DROP, "${UserId}");

        assertThat(joinPushdown.apply(query, List.of(keys("a")), Map.of())).isSameAs(query);
    }

    @Test
    void joinWithTooManyKeysIsNotPushedDown() {
        final JoinPushdown.Keys keys = keysOfAJoin();
        for (int i = 0; i <= JoinPushdown.MAX_KEYS; i++) {
            keys.add(key("u" + i));
        }
        final Query query = query(JoinType.KEEP, "${UserId}");

        assertThat(keys.hasValues()).isFalse();
        assertThat(joinPushdown.apply(query, List.of(keys), Map.of())).isSameAs(query);
    }

    @Test
    void joinWithTheMostKeysAllowedIsPushedDown() {
        final JoinPushdown.Keys keys = keysOfAJoin();
        for (int i = 0; i < JoinPushdown.MAX_KEYS; i++) {
            keys.add(key("u" + i));
        }
        // Repeated keys count once.
        keys.add(key("u0"));

        assertThat(keys.hasValues()).isTrue();
        assertThat(pushedTerms(joinPushdown.apply(query(JoinType.KEEP, "${UserId}"), List.of(keys), Map.of())))
                .hasSize(1);
    }

    @Test
    void joinWithNoKeysIsNotPushedDown() {
        final Query query = query(JoinType.KEEP, "${UserId}");

        assertThat(joinPushdown.apply(query, List.of(keys()), Map.of())).isSameAs(query);
    }

    @Test
    void keyThatIsNotPlainStopsThePushDown() {
        final Query query = query(JoinType.KEEP, "${UserId}");

        for (final String value : List.of("a b", "a,b", "a*", "a?", "\"a\"", "a\\b")) {
            assertThat(joinPushdown.apply(query, List.of(keys("ok", value)), Map.of()))
                    .as(value)
                    .isSameAs(query);
        }
    }

    @Test
    void numberFieldNeedsWholeNumberKeys() {
        final Query query = query(JoinType.KEEP, "${Count}");

        assertThat(pushedTerms(joinPushdown.apply(query, List.of(keys("1", "-20")), Map.of())))
                .containsExactly(in("Count", "1,-20"));
        // Not as a long field's value is written, or more digits than a double holds exactly.
        for (final String value : List.of("1.5", "01", "-0", "abc", "1234567890123456")) {
            assertThat(joinPushdown.apply(query, List.of(keys("1", value)), Map.of()))
                    .as(value)
                    .isSameAs(query);
        }
    }

    @Test
    void integerFieldNeedsKeysThatAreIntegers() {
        final Query query = query(JoinType.KEEP, "${Small}");

        assertThat(pushedTerms(joinPushdown.apply(query, List.of(keys("2147483647", "-2147483648")), Map.of())))
                .containsExactly(in("Small", "2147483647,-2147483648"));
        assertThat(joinPushdown.apply(query, List.of(keys("2147483648")), Map.of())).isSameAs(query);
    }

    @Test
    void fieldsAreFoundOnceForSeveralJoins() {
        final Query query = Query.builder()
                .dataSource(EVENTS)
                .joins(List.of(
                        new QueryJoin(JoinType.KEEP, "hr", Query.builder().dataSource(HR).build(), null,
                                List.of(new JoinKey("${UserId}", "${hr.Username}")), null),
                        new QueryJoin(JoinType.KEEP, "hr2", Query.builder().dataSource(HR).build(), null,
                                List.of(new JoinKey("${Count}", "${hr2.Count}")), null)))
                .build();

        assertThat(pushedTerms(joinPushdown.apply(query, List.of(keys("a"), keys("1")), Map.of())))
                .containsExactly(in("UserId", "a"), in("Count", "1"));
        Mockito.verify(searchProvider, Mockito.times(1)).getFieldInfo(Mockito.any());
    }

    @Test
    void keysOfJoinsThatCantBePushedDownAreNotCollected() {
        final JoinPushdown.Keys left = JoinPushdown.Keys.forJoin(
                query(JoinType.LEFT, "${UserId}").getJoins().getFirst());
        final JoinPushdown.Keys twoKeys = JoinPushdown.Keys.forJoin(new QueryJoin(JoinType.KEEP, "hr", null, null,
                List.of(new JoinKey("${UserId}", "${hr.Username}"), new JoinKey("${Host}", "${hr.Host}")), null));
        left.add(key("a"));
        twoKeys.add(key("a"));

        assertThat(left.hasValues()).isFalse();
        assertThat(twoKeys.hasValues()).isFalse();
    }

    @Test
    void fieldTheDataSourceCannotSearchIsNotPushedDown() {
        final Query notIndexed = query(JoinType.KEEP, "${NotIndexed}");
        final Query date = query(JoinType.KEEP, "${EventTime}");
        final Query noIn = query(JoinType.KEEP, "${NoIn}");
        final Query unknown = query(JoinType.KEEP, "${Missing}");

        assertThat(joinPushdown.apply(notIndexed, List.of(keys("a")), Map.of())).isSameAs(notIndexed);
        assertThat(joinPushdown.apply(date, List.of(keys("a")), Map.of())).isSameAs(date);
        assertThat(joinPushdown.apply(noIn, List.of(keys("1")), Map.of())).isSameAs(noIn);
        assertThat(joinPushdown.apply(unknown, List.of(keys("a")), Map.of())).isSameAs(unknown);
    }

    @Test
    void keyThatIsNotJustAFieldIsNotPushedDown() {
        final Query expression = query(JoinType.KEEP, "lowerCase(${UserId})");
        final Query param = query(JoinType.KEEP, "${UserId}");

        assertThat(joinPushdown.apply(expression, List.of(keys("a")), Map.of())).isSameAs(expression);
        assertThat(joinPushdown.apply(param, List.of(keys("a")), Map.of("UserId", "x"))).isSameAs(param);
    }

    @Test
    void joinOnSeveralKeysIsNotPushedDown() {
        final Query query = Query.builder()
                .dataSource(EVENTS)
                .joins(List.of(new QueryJoin(JoinType.KEEP, "hr", Query.builder().dataSource(HR).build(), null,
                        List.of(new JoinKey("${UserId}", "${hr.Username}"),
                                new JoinKey("${Host}", "${hr.Host}")),
                        null)))
                .build();

        assertThat(joinPushdown.apply(query, List.of(keys("a")), Map.of())).isSameAs(query);
    }

    @Test
    void firstSourceAliasIsTakenOffTheField() {
        final Query query = query(JoinType.KEEP, "${e.UserId}").copy().alias("e").build();

        assertThat(pushedTerms(joinPushdown.apply(query, List.of(keys("a")), Map.of())))
                .containsExactly(in("UserId", "a"));
    }

    @Test
    void keyOnAnEarlierJoinsFieldIsNotPushedDown() {
        final Query query = Query.builder()
                .dataSource(EVENTS)
                .joins(List.of(
                        new QueryJoin(JoinType.INNER, "hr", Query.builder().dataSource(HR).build(), null,
                                List.of(new JoinKey("${UserId}", "${hr.Username}")), null),
                        new QueryJoin(JoinType.KEEP, "dept", Query.builder().dataSource(HR).build(), null,
                                List.of(new JoinKey("${hr.Department}", "${dept.Name}")), null)))
                .build();

        assertThat(pushedTerms(joinPushdown.apply(query, List.of(keys("a"), keys("Sales")), Map.of())))
                .containsExactly(in("UserId", "a"));
    }

    @Test
    void failureToFindTheFieldsLeavesTheQueryAlone() {
        Mockito.when(searchProvider.getFieldInfo(Mockito.any())).thenThrow(new RuntimeException("down"));
        final Query query = query(JoinType.KEEP, "${UserId}");

        assertThat(joinPushdown.apply(query, List.of(keys("a")), Map.of())).isSameAs(query);
    }

    private static Query query(final JoinType type, final String leftKey) {
        return Query.builder()
                .dataSource(EVENTS)
                .expression(WHERE)
                .joins(List.of(new QueryJoin(type, "hr", Query.builder().dataSource(HR).build(), null,
                        List.of(new JoinKey(leftKey, "${hr.Username}")), null)))
                .build();
    }

    // The terms added to the query, from operators after its own where.
    private static List<ExpressionTerm> pushedTerms(final Query query) {
        return query.getExpression()
                .getChildren()
                .stream()
                .filter(item -> item != WHERE && !item.equals(WHERE))
                .flatMap(item -> ((ExpressionOperator) item).getChildren().stream())
                .map(ExpressionTerm.class::cast)
                .toList();
    }

    private static ExpressionTerm in(final String field, final String value) {
        return ExpressionTerm.builder().field(field).condition(Condition.IN).value(value).build();
    }

    private static QueryJoin withRightKey(final QueryJoin join, final String rightKey) {
        return new QueryJoin(join.getType(), join.getAlias(), join.getQuery(), null,
                List.of(new JoinKey("${UserId}", rightKey)), null);
    }

    // The keys of anchors, collected whatever the kind of join.
    private static JoinPushdown.Keys collected(final String... values) {
        final JoinPushdown.Keys keys = JoinPushdown.Keys.collecting();
        for (final String value : values) {
            keys.add(key(value));
        }
        return keys;
    }

    // An empty collector of the keys of a join that might be pushed down.
    private static JoinPushdown.Keys keysOfAJoin() {
        return JoinPushdown.Keys.forJoin(query(JoinType.KEEP, "${UserId}").getJoins().getFirst());
    }

    private static JoinPushdown.Keys keys(final String... values) {
        final JoinPushdown.Keys keys = keysOfAJoin();
        for (final String value : values) {
            keys.add(key(value));
        }
        // A row with no key is ignored.
        keys.add(null);
        return keys;
    }

    // A join key of one value, as JoinKeys makes it.
    private static String key(final String value) {
        return value.length() + ":" + value;
    }
}
