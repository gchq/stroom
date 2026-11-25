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

package stroom.ai.impl.tools.timeline;

import stroom.ai.impl.tools.sequence.StreamFilters;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A role's conditions held against an event's values in memory, as [StreamFilters] writes them.
class TestRoleConditions {

    @Test
    void noConditionsHoldForEveryEvent() {
        assertThat(RoleConditions.holds(null, Map.of())).isTrue();
        assertThat(RoleConditions.fields(null)).isEmpty();
    }

    @Test
    void equalsIsExactUnlessItHasWildcardsOrIsCaseInsensitive() {
        final Map<String, List<Object>> event = Map.of("Action", List.of("Focus"));
        assertThat(RoleConditions.holds(term("Action", Condition.EQUALS, "Focus"), event)).isTrue();
        assertThat(RoleConditions.holds(term("Action", Condition.EQUALS, "focus"), event)).isFalse();
        assertThat(RoleConditions.holds(insensitive(term("Action", Condition.EQUALS, "focus")), event)).isTrue();
        assertThat(RoleConditions.holds(term("Action", Condition.EQUALS, "Fo*"), event)).isTrue();
        assertThat(RoleConditions.holds(term("Action", Condition.EQUALS, "*cus"), event)).isTrue();
        assertThat(RoleConditions.holds(term("Action", Condition.EQUALS, "*x*"), event)).isFalse();
        // Presence, as IS_NOT_NULL is written.
        assertThat(RoleConditions.holds(term("Action", Condition.EQUALS, "*"), event)).isTrue();
        assertThat(RoleConditions.holds(term("Other", Condition.EQUALS, "*"), event)).isFalse();
    }

    @Test
    void aMissingFieldHoldsOnlyANegatedCondition() {
        final Map<String, List<Object>> event = Map.of();
        assertThat(RoleConditions.holds(term("Action", Condition.NOT_EQUALS, "x"), event)).isTrue();
        assertThat(RoleConditions.holds(not(term("Action", Condition.EQUALS, "*")), event)).isTrue();
        assertThat(RoleConditions.holds(term("Action", Condition.IN, "a,b"), event)).isFalse();
    }

    @Test
    void inTakesValuesSeparatedByCommasAndAnyOfSeveralValuesHolds() {
        final Map<String, List<Object>> event = Map.of("EventID", List.of("4624", "4800"));
        assertThat(RoleConditions.holds(term("EventID", Condition.IN, "4800, 4801"), event)).isTrue();
        assertThat(RoleConditions.holds(term("EventID", Condition.IN, "1,2"), event)).isFalse();
        assertThat(RoleConditions.holds(not(term("EventID", Condition.IN, "1,2")), event)).isTrue();
    }

    @Test
    void numbersCompareAsNumbersTimesAsTimesAndOtherwiseAsText() {
        final Map<String, List<Object>> event = Map.of("Bytes", List.of(1500.0), "When",
                List.of("2026-11-10T09:00:00Z"), "Name", List.of("beta"));
        assertThat(RoleConditions.holds(term("Bytes", Condition.EQUALS, "1500"), event)).isTrue();
        assertThat(RoleConditions.holds(term("Bytes", Condition.GREATER_THAN, "999"), event)).isTrue();
        assertThat(RoleConditions.holds(term("Bytes", Condition.LESS_THAN, "999"), event)).isFalse();
        assertThat(RoleConditions.holds(term("Bytes", Condition.BETWEEN, "1000,1500"), event)).isTrue();
        assertThat(RoleConditions.holds(term("Bytes", Condition.BETWEEN, "1501,2000"), event)).isFalse();
        assertThat(RoleConditions.holds(term("Bytes", Condition.BETWEEN, "nonsense"), event)).isFalse();
        assertThat(RoleConditions.holds(term("When", Condition.GREATER_THAN_OR_EQUAL_TO,
                "2026-11-10T09:00:00Z"), event)).isTrue();
        assertThat(RoleConditions.holds(term("When", Condition.LESS_THAN, "2026-11-09T00:00:00Z"), event)).isFalse();
        assertThat(RoleConditions.holds(term("Name", Condition.LESS_THAN_OR_EQUAL_TO, "gamma"), event)).isTrue();
    }

    @Test
    void aRegexMustMatchTheWholeValueAndABadOneNeverHolds() {
        final Map<String, List<Object>> event = Map.of("Process", List.of("WINWORD.EXE"));
        assertThat(RoleConditions.holds(term("Process", Condition.MATCHES_REGEX, "WIN.*"), event)).isTrue();
        assertThat(RoleConditions.holds(term("Process", Condition.MATCHES_REGEX, "WORD"), event)).isFalse();
        assertThat(RoleConditions.holds(term("Process", Condition.MATCHES_REGEX, "(unclosed"), event)).isFalse();
    }

    @Test
    void whereAndAnyOfAreAndAndOrAndTheirFieldsAreCollected() {
        final ExpressionOperator conditions = ExpressionOperator.builder().op(Op.AND)
                .addTerm(term("Action", Condition.EQUALS, "copy"))
                .addOperator(ExpressionOperator.builder().op(Op.OR)
                        .addTerm(term("Target", Condition.EQUALS, "E:*"))
                        .addTerm(term("Device", Condition.EQUALS, "usb"))
                        .build())
                .build();
        assertThat(RoleConditions.holds(conditions, Map.of("Action", List.of("copy"), "Target",
                List.of("E:\\a.docx")))).isTrue();
        assertThat(RoleConditions.holds(conditions, Map.of("Action", List.of("copy"), "Device",
                List.of("usb")))).isTrue();
        assertThat(RoleConditions.holds(conditions, Map.of("Action", List.of("copy"), "Target",
                List.of("C:\\a.docx")))).isFalse();
        assertThat(RoleConditions.holds(conditions, Map.of("Target", List.of("E:\\a.docx")))).isFalse();
        assertThat(RoleConditions.fields(conditions)).containsExactly("Action", "Target", "Device");
    }

    // ------------------------------------------------------------------------------------------------

    private static ExpressionTerm term(final String field, final Condition condition, final String value) {
        return ExpressionTerm.builder().field(field).condition(condition).value(value).build();
    }

    private static ExpressionTerm insensitive(final ExpressionTerm term) {
        return term.copy().options(Map.of(StreamFilters.CASE_INSENSITIVE, "true")).build();
    }

    private static ExpressionOperator not(final ExpressionTerm term) {
        return ExpressionOperator.builder().op(Op.NOT).addTerm(term).build();
    }
}
