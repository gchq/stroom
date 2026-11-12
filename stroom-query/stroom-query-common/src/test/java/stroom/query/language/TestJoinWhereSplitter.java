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

package stroom.query.language;

import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionUtil;
import stroom.query.language.JoinWhereSplitter.JoinedSource;
import stroom.query.language.JoinWhereSplitter.Split;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestJoinWhereSplitter {

    private static final List<JoinedSource> HR_INNER = List.of(new JoinedSource("hr", true));
    private static final List<JoinedSource> HR_LEFT = List.of(new JoinedSource("hr", false));

    @Test
    void noWhereGivesNoConditions() {
        final Split split = JoinWhereSplitter.split(null, "e", false, HR_INNER);

        assertThat(split.first()).isNull();
        assertThat(split.joined()).isEmpty();
        assertThat(split.postJoin()).isNull();
    }

    @Test
    void nestedAndsAreSplitIntoTheirParts() {
        // 'a and b and c' as the parser builds it: AND {AND {a, b}, c}.
        final ExpressionOperator where = and(
                and(term("e.Type"), term("hr.Department")),
                term("Host"));

        final Split split = JoinWhereSplitter.split(where, "e", false, HR_INNER);

        assertThat(fields(split.first())).containsExactly("Type", "Host");
        assertThat(fields(split.joined().get("hr"))).containsExactly("Department");
        assertThat(split.postJoin()).isNull();
    }

    @Test
    void joinedConditionStaysOnJoinedRowsWhenPushDownIsNotAllowed() {
        final Split split = JoinWhereSplitter.split(and(term("Host"), term("hr.Department")), null, false, HR_LEFT);

        assertThat(fields(split.first())).containsExactly("Host");
        assertThat(split.joined()).isEmpty();
        assertThat(fields(split.postJoin())).containsExactly("hr.Department");
    }

    @Test
    void orAcrossSourcesRunsOnJoinedRows() {
        final ExpressionOperator or = ExpressionOperator.builder()
                .op(Op.OR)
                .addTerm(term("Host"))
                .addTerm(term("hr.Department"))
                .build();

        final Split split = JoinWhereSplitter.split(or, null, false, HR_INNER);

        assertThat(split.first()).isNull();
        assertThat(fields(split.postJoin())).containsExactly("Host", "hr.Department");
    }

    @Test
    void everythingRunsOnJoinedRowsForABracketedFirstSource() {
        final Split split = JoinWhereSplitter.split(and(term("Host"), term("hr.Department")), "x", true, HR_INNER);

        assertThat(split.first()).isNull();
        assertThat(split.joined()).isEmpty();
        assertThat(fields(split.postJoin())).containsExactly("Host", "hr.Department");
    }

    @Test
    void longestAliasOwnsTheField() {
        // An alias may contain a dot, as a data source name used as the alias can.
        final List<JoinedSource> joined = List.of(
                new JoinedSource("hr", true),
                new JoinedSource("hr.users", true));

        final Split split = JoinWhereSplitter.split(
                and(term("hr.users.Name"), term("hr.Department")), null, false, joined);

        assertThat(fields(split.joined().get("hr.users"))).containsExactly("Name");
        assertThat(fields(split.joined().get("hr"))).containsExactly("Department");
    }

    @Test
    void fieldThatIsOnlyTheAliasAndADotIsNotJoined() {
        final Split split = JoinWhereSplitter.split(and(term("hr.")), null, false, HR_INNER);

        assertThat(fields(split.first())).containsExactly("hr.");
    }

    @Test
    void disabledConditionsAreLeftOut() {
        final ExpressionOperator where = ExpressionOperator.builder()
                .addTerm(term("Host"))
                .addTerm(term("Type").copy().enabled(false).build())
                .build();

        final Split split = JoinWhereSplitter.split(where, null, false, HR_INNER);

        assertThat(fields(split.first())).containsExactly("Host");
    }

    @Test
    void findAliasFindsTheLongestPrefix() {
        final List<String> aliases = List.of("hr", "hr.users", "x");

        assertThat(JoinWhereSplitter.findAlias("hr.users.Name", aliases)).isEqualTo("hr.users");
        assertThat(JoinWhereSplitter.findAlias("hr.Name", aliases)).isEqualTo("hr");
        assertThat(JoinWhereSplitter.findAlias("hrx.Name", aliases)).isNull();
        assertThat(JoinWhereSplitter.findAlias("Name", aliases)).isNull();
        assertThat(JoinWhereSplitter.findAlias(null, aliases)).isNull();
    }

    @Test
    void renameFieldsRenamesEveryTerm() {
        final ExpressionOperator renamed = JoinWhereSplitter.renameFields(
                and(term("a"), and(term("b"))), field -> "_" + field);

        assertThat(fields(renamed)).containsExactly("_a", "_b");
        assertThat(JoinWhereSplitter.renameFields(null, field -> field)).isNull();
    }

    private static ExpressionOperator and(final Object... items) {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder();
        for (final Object item : items) {
            if (item instanceof final ExpressionTerm term) {
                builder.addTerm(term);
            } else {
                builder.addOperator((ExpressionOperator) item);
            }
        }
        return builder.build();
    }

    private static ExpressionTerm term(final String field) {
        return ExpressionTerm.equals(field, "x");
    }

    private static List<String> fields(final ExpressionOperator expression) {
        return expression == null
                ? List.of()
                : ExpressionUtil.fields(expression);
    }
}
