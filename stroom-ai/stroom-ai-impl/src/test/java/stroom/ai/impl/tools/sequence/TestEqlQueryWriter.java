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

package stroom.ai.impl.tools.sequence;

import stroom.ai.impl.tools.sequence.EqlQueryWriter.Filter;
import stroom.ai.impl.tools.sequence.EqlQueryWriter.Step;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.FieldType;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestEqlQueryWriter {

    private static final Map<String, FieldType> TYPES = Map.of(
            "user", FieldType.KEYWORD,
            "host", FieldType.KEYWORD,
            "action", FieldType.KEYWORD,
            "message", FieldType.TEXT,
            "ip", FieldType.IPV4_ADDRESS,
            "bytes", FieldType.LONG,
            "@timestamp", FieldType.DATE,
            "ok", FieldType.BOOLEAN,
            "by", FieldType.KEYWORD,
            "user name", FieldType.KEYWORD);

    @Test
    void stepsWithTheirOwnJoinFieldsAndTheWindow() {
        final Step copy = step(1, List.of(filter("action", Condition.EQUALS, "Copy")), List.of("user", "host"));
        final Step shell = step(2, List.of(new Filter("action", Condition.ENDS_WITH, "powershell.exe", true)),
                List.of("SubjectUserName", "Computer"));

        assertThat(EqlQueryWriter.eql(List.of(copy, shell), null, Duration.ofMinutes(2), 2)).isEqualTo("""
                sequence with maxspan=2m
                  [any where stroom_eql_doc == "1" and action == "Copy"] by user, host
                  [any where stroom_eql_doc == "2" and action like~ "*powershell.exe"] by SubjectUserName, Computer""");
    }

    @Test
    void aStepNotRestrictedHasNoDocCondition() {
        final Step copy = step(1, List.of(filter("action", Condition.EQUALS, "Copy")), List.of("user"))
                .withRestricted(false);
        final Step shell = step(2, List.of(filter("host", Condition.EQUALS, "H1")), List.of("user"));

        assertThat(EqlQueryWriter.eql(List.of(copy, shell), null, Duration.ofMinutes(2), 2)).isEqualTo("""
                sequence with maxspan=2m
                  [any where action == "Copy"] by user
                  [any where stroom_eql_doc == "2" and host == "H1"] by user""");
    }

    @Test
    void oneDocNeedsNoDocCondition() {
        final Step a = step(1, List.of(), List.of("user"));
        final Step b = step(1, List.of(filter("action", Condition.EQUALS, "x")), List.of("user"));

        assertThat(EqlQueryWriter.eql(List.of(a, b), null, Duration.ofSeconds(90), 1)).isEqualTo("""
                sequence with maxspan=90s
                  [any where true] by user
                  [any where action == "x"] by user""");
    }

    @Test
    void missingRunsAndUntil() {
        final Step failed = new Step(1, List.of(filter("action", Condition.EQUALS, "fail")), List.of("user"), false,
                20, List.of(), TYPES);
        final Step noLogoff = new Step(1, List.of(filter("action", Condition.EQUALS, "logoff")), List.of("user"),
                true, 1, List.of(), TYPES);
        final Step success = step(1, List.of(filter("action", Condition.EQUALS, "ok")), List.of("user"));
        final Step reset = step(1, List.of(filter("action", Condition.EQUALS, "reset")), List.of("user"));

        assertThat(EqlQueryWriter.eql(List.of(failed, noLogoff, success), reset, Duration.ofHours(1), 1))
                .isEqualTo("""
                        sequence with maxspan=1h
                          [any where action == "fail"] by user with runs=20
                          ![any where action == "logoff"] by user
                          [any where action == "ok"] by user
                        until [any where action == "reset"] by user""");
    }

    @Test
    void equalityCaseAndWildcards() {
        assertThat(eql(filter("user", Condition.EQUALS, "Alice"))).isEqualTo("user == \"Alice\"");
        assertThat(eql(new Filter("user", Condition.EQUALS, "alice", true))).isEqualTo("user : \"alice\"");
        assertThat(eql(filter("user", Condition.EQUALS, "al*"))).isEqualTo("user like \"al*\"");
        assertThat(eql(new Filter("user", Condition.EQUALS, "AL*", true))).isEqualTo("user like~ \"AL*\"");
        assertThat(eql(filter("user", Condition.NOT_EQUALS, "bob"))).isEqualTo("user != \"bob\"");
        assertThat(eql(new Filter("user", Condition.NOT_EQUALS, "bob", true))).isEqualTo("not user : \"bob\"");
        assertThat(eql(filter("user", Condition.NOT_EQUALS, "b*"))).isEqualTo("not user like \"b*\"");
        assertThat(eql(filter("message", Condition.CONTAINS, "usb"))).isEqualTo("message like \"*usb*\"");
        assertThat(eql(new Filter("message", Condition.STARTS_WITH, "USB", true))).isEqualTo("message like~ \"USB*\"");
        assertThat(eql(filter("message", Condition.ENDS_WITH, ".exe"))).isEqualTo("message like \"*.exe\"");
        assertThat(eql(filter("message", Condition.MATCHES_REGEX, "a.+b"))).isEqualTo("message regex \"a.+b\"");
        assertThat(eql(new Filter("message", Condition.MATCHES_REGEX, "a.+b", true)))
                .isEqualTo("message regex~ \"a.+b\"");
    }

    @Test
    void inCidrRangesAndNulls() {
        assertThat(eql(filter("user", Condition.IN, "alice, bob"))).isEqualTo("user in (\"alice\", \"bob\")");
        assertThat(eql(new Filter("user", Condition.IN, "Alice,Bob", true))).isEqualTo("user in~ (\"Alice\", \"Bob\")");
        assertThat(eql(filter("bytes", Condition.IN, "1, 2"))).isEqualTo("bytes in (1, 2)");
        assertThat(eql(filter("ip", Condition.EQUALS, "10.0.0.0/8"))).isEqualTo("cidrMatch(ip, \"10.0.0.0/8\")");
        assertThat(eql(filter("ip", Condition.IN, "10.0.0.0/8, 192.168.1.1")))
                .isEqualTo("cidrMatch(ip, \"10.0.0.0/8\", \"192.168.1.1/32\")");
        assertThat(eql(filter("ip", Condition.EQUALS, "10.1.2.3"))).isEqualTo("ip == \"10.1.2.3\"");
        assertThat(eql(filter("bytes", Condition.GREATER_THAN, "100"))).isEqualTo("bytes > 100");
        assertThat(eql(filter("bytes", Condition.GREATER_THAN_OR_EQUAL_TO, "100"))).isEqualTo("bytes >= 100");
        assertThat(eql(filter("bytes", Condition.LESS_THAN, "5.5"))).isEqualTo("bytes < 5.5");
        assertThat(eql(filter("bytes", Condition.LESS_THAN_OR_EQUAL_TO, "5"))).isEqualTo("bytes <= 5");
        assertThat(eql(filter("bytes", Condition.BETWEEN, "1,10"))).isEqualTo("(bytes >= 1 and bytes <= 10)");
        assertThat(eql(filter("@timestamp", Condition.GREATER_THAN, "2026-01-01T00:00:00Z")))
                .isEqualTo("@timestamp > \"2026-01-01T00:00:00Z\"");
        assertThat(eql(filter("ok", Condition.EQUALS, "TRUE"))).isEqualTo("ok == true");
        assertThat(eql(filter("user", Condition.IS_NULL, ""))).isEqualTo("user == null");
        assertThat(eql(filter("user", Condition.IS_NOT_NULL, ""))).isEqualTo("user != null");
        // Case-insensitive has no meaning for a number.
        assertThat(eql(new Filter("bytes", Condition.EQUALS, "7", true))).isEqualTo("bytes == 7");
    }

    @Test
    void stringsAndNamesAreEscaped() {
        assertThat(eql(filter("user", Condition.EQUALS, "C:\\Users\\\"x\""))).isEqualTo(
                "user == \"C:\\\\Users\\\\\\\"x\\\"\"");
        assertThat(eql(filter("by", Condition.EQUALS, "x"))).isEqualTo("`by` == \"x\"");
        assertThat(eql(filter("user name", Condition.EQUALS, "x"))).isEqualTo("`user name` == \"x\"");
        assertThat(EqlQueryWriter.eqlName("host.name")).isEqualTo("host.name");
        assertThat(EqlQueryWriter.eqlName("@timestamp")).isEqualTo("@timestamp");
    }

    @Test
    void valuesTheFieldCantTakeAreRefused() {
        assertThatThrownBy(() -> eql(filter("bytes", Condition.EQUALS, "lots")))
                .isInstanceOf(WorkspaceException.class)
                .hasMessageContaining("'bytes' is a number field");
        assertThatThrownBy(() -> eql(filter("bytes", Condition.BETWEEN, "1")))
                .hasMessageContaining("BETWEEN on 'bytes' takes 'from,to'");
        assertThatThrownBy(() -> eql(filter("user", Condition.IN, " , ")))
                .hasMessageContaining("IN on 'user' takes values separated by commas");
    }

    @Test
    void countsAreEsqlOfTheSameConditions() {
        final Step step = step(1, List.of(
                new Filter("user", Condition.EQUALS, "Alice", true),
                filter("action", Condition.EQUALS, "Co*"),
                filter("ip", Condition.IN, "10.0.0.0/8,10.1.1.1"),
                filter("bytes", Condition.BETWEEN, "1,10"),
                filter("host", Condition.IS_NOT_NULL, ""),
                new Filter("message", Condition.MATCHES_REGEX, "x", true)), List.of("user"));

        // Counted up to a cap: the LIMIT before STATS stops the read once it is passed.
        assertThat(EqlQueryWriter.countPipeline(step, 1_000)).isEqualTo(
                "| WHERE TO_LOWER(user) == \"alice\" AND action LIKE \"Co*\" AND CIDR_MATCH(ip, \"10.0.0.0/8\", "
                + "\"10.1.1.1/32\") AND (bytes >= 1 AND bytes <= 10) AND host IS NOT NULL | LIMIT 1001 "
                + "| STATS n = COUNT(*)");
        assertThat(EqlQueryWriter.countPipeline(step(1, List.of(), List.of("user")), 1_000))
                .isEqualTo("| LIMIT 1001 | STATS n = COUNT(*)");
        assertThat(EqlQueryWriter.esqlCondition(new Filter("user", Condition.IN, "A,B", true), FieldType.KEYWORD))
                .isEqualTo("TO_LOWER(user) IN (\"a\", \"b\")");
        assertThat(EqlQueryWriter.esqlCondition(filter("message", Condition.CONTAINS, "usb"), FieldType.TEXT))
                .isEqualTo("message LIKE \"*usb*\"");
        assertThat(EqlQueryWriter.esqlCondition(filter("user", Condition.IS_NULL, ""), FieldType.KEYWORD))
                .isEqualTo("user IS NULL");
    }

    @Test
    void windowsAreReadAndWritten() {
        assertThat(EqlQueryWriter.maxspan("2m")).isEqualTo(Duration.ofMinutes(2));
        assertThat(EqlQueryWriter.maxspan("2 minutes")).isEqualTo(Duration.ofMinutes(2));
        assertThat(EqlQueryWriter.maxspan("90s")).isEqualTo(Duration.ofSeconds(90));
        assertThat(EqlQueryWriter.maxspan("1 hour")).isEqualTo(Duration.ofHours(1));
        assertThat(EqlQueryWriter.maxspan("500ms")).isEqualTo(Duration.ofMillis(500));
        assertThat(EqlQueryWriter.maxspan("1d")).isEqualTo(Duration.ofDays(1));
        assertThat(EqlQueryWriter.maxspan("PT10M")).isEqualTo(Duration.ofMinutes(10));
        assertThat(EqlQueryWriter.maxspanText(Duration.ofMinutes(2))).isEqualTo("2m");
        assertThat(EqlQueryWriter.maxspanText(Duration.ofSeconds(90))).isEqualTo("90s");
        assertThat(EqlQueryWriter.maxspanText(Duration.ofHours(24))).isEqualTo("24h");
        assertThat(EqlQueryWriter.maxspanText(Duration.ofMillis(1_500))).isEqualTo("1500ms");
    }

    @Test
    void windowsThatCantBeUsedAreRefused() {
        assertThatThrownBy(() -> EqlQueryWriter.maxspan("25h")).hasMessageContaining("at most 24h");
        assertThatThrownBy(() -> EqlQueryWriter.maxspan("a while")).hasMessageContaining("can't be read");
        assertThatThrownBy(() -> EqlQueryWriter.maxspan("0s")).hasMessageContaining("longer than nothing");
        assertThatThrownBy(() -> EqlQueryWriter.maxspan("")).hasMessageContaining("can't be read");
    }

    private static String eql(final Filter filter) {
        return EqlQueryWriter.eqlCondition(filter, TYPES.get(filter.field()));
    }

    @Test
    void notInIsANegatedList() {
        assertThat(EqlQueryWriter.eqlCondition(new Filter("user", Condition.IN, "svc,system", false, true, false),
                FieldType.KEYWORD)).isEqualTo("not user in (\"svc\", \"system\")");
        assertThat(EqlQueryWriter.eqlCondition(new Filter("user", Condition.IN, "SVC,System", true, true, false),
                FieldType.KEYWORD)).isEqualTo("not user in~ (\"SVC\", \"System\")");
        assertThat(EqlQueryWriter.eqlCondition(new Filter("ip", Condition.IN, "10.0.0.0/8,192.168.1.1", false, true,
                false), FieldType.IPV4_ADDRESS))
                .isEqualTo("not cidrMatch(ip, \"10.0.0.0/8\", \"192.168.1.1/32\")");
        assertThat(EqlQueryWriter.eqlCondition(new Filter("bytes", Condition.IN, "1,2", false, true, false),
                FieldType.LONG)).isEqualTo("not bytes in (1, 2)");
        // The count, in ES|QL.
        assertThat(EqlQueryWriter.esqlCondition(new Filter("user", Condition.IN, "svc,system", false, true, false),
                FieldType.KEYWORD)).isEqualTo("user NOT IN (\"svc\", \"system\")");
        assertThat(EqlQueryWriter.esqlCondition(new Filter("ip", Condition.IN, "10.0.0.0/8", false, true, false),
                FieldType.IPV4_ADDRESS)).isEqualTo("NOT CIDR_MATCH(ip, \"10.0.0.0/8\")");
    }

    @Test
    void aNegatedPatternIsTheConditionNot() {
        // A person's own acts left out by their logon forms (a target step with logonForms).
        assertThat(EqlQueryWriter.eqlCondition(new Filter("user", Condition.MATCHES_REGEX, "(.*\\\\)?(alice)",
                true, true, false), FieldType.KEYWORD)).isEqualTo("not (user regex~ \"(.*\\\\\\\\)?(alice)\")");
        assertThat(EqlQueryWriter.esqlCondition(new Filter("user", Condition.MATCHES_REGEX, "alice.*", false, true,
                false), FieldType.KEYWORD)).isEqualTo("NOT (user RLIKE \"alice.*\")");
        // No ES|QL form for a case-insensitive regex, negated or not.
        assertThat(EqlQueryWriter.esqlCondition(new Filter("user", Condition.MATCHES_REGEX, "alice.*", true, true,
                false), FieldType.KEYWORD)).isNull();
    }

    @Test
    void aLengthIsComparedAsAWholeNumber() {
        assertThat(EqlQueryWriter.eqlCondition(new Filter("cmd", Condition.GREATER_THAN, "1000", false, false, true),
                FieldType.KEYWORD)).isEqualTo("length(cmd) > 1000");
        assertThat(EqlQueryWriter.eqlCondition(new Filter("cmd", Condition.BETWEEN, "10, 20", false, false, true),
                FieldType.KEYWORD)).isEqualTo("(length(cmd) >= 10 and length(cmd) <= 20)");
        assertThat(EqlQueryWriter.esqlCondition(new Filter("cmd", Condition.GREATER_THAN, "1000", false, false,
                true), FieldType.KEYWORD)).isEqualTo("LENGTH(cmd) > 1000");

        assertThatThrownBy(() -> EqlQueryWriter.eqlCondition(new Filter("cmd", Condition.GREATER_THAN, "long",
                false, false, true), FieldType.KEYWORD))
                .hasMessageContaining("whole number of characters, not 'long'");
        assertThatThrownBy(() -> EqlQueryWriter.eqlCondition(new Filter("cmd", Condition.CONTAINS, "10", false,
                false, true), FieldType.KEYWORD))
                .hasMessageContaining("length on 'cmd' is compared with EQUALS");
        assertThatThrownBy(() -> EqlQueryWriter.eqlCondition(new Filter("bytes", Condition.GREATER_THAN, "10", false,
                false, true), FieldType.LONG))
                .hasMessage("length is for string fields; 'bytes' isn't one.");
    }

    @Test
    void aQuestionMarkInAValueIsMatchedAsItIs() {
        // In a pattern and with ':', EQL reads ? as any one character (9.5.3: 'v : "a?b"' matched "axb"), and
        // can't escape it.
        assertThat(EqlQueryWriter.eqlCondition(new Filter("user", Condition.EQUALS, "a?b", true), FieldType.KEYWORD))
                .isEqualTo("user in~ (\"a?b\")");
        assertThat(EqlQueryWriter.eqlCondition(new Filter("user", Condition.EQUALS, "a?b", false), FieldType.KEYWORD))
                .isEqualTo("user == \"a?b\"");
        assertThat(EqlQueryWriter.eqlCondition(new Filter("user", Condition.NOT_EQUALS, "a?b", true),
                FieldType.KEYWORD)).isEqualTo("not user in~ (\"a?b\")");
        // Without a ?, ':' as before.
        assertThat(EqlQueryWriter.eqlCondition(new Filter("user", Condition.EQUALS, "ab", true), FieldType.KEYWORD))
                .isEqualTo("user : \"ab\"");

        for (final Filter pattern : List.of(new Filter("user", Condition.EQUALS, "a?b*", false),
                new Filter("user", Condition.CONTAINS, "a?b", true),
                new Filter("user", Condition.STARTS_WITH, "a?", false),
                new Filter("user", Condition.ENDS_WITH, "?b", false),
                new Filter("user", Condition.NOT_EQUALS, "*?", false))) {
            assertThatThrownBy(() -> EqlQueryWriter.eqlCondition(pattern, FieldType.KEYWORD))
                    .as(pattern.toString())
                    .hasMessageContaining("has a ? in a pattern")
                    .hasMessageContaining("use MATCHES_REGEX with \\? for the ?");
        }
    }

    @Test
    void anyOfIsOneOrGroupBesideTheOtherConditions() {
        final Step step = new Step(1, List.of(filter("action", Condition.EQUALS, "exec")),
                List.of(filter("user", Condition.EQUALS, "alice"), new Filter("host", Condition.STARTS_WITH, "web",
                        true)), List.of("user"), false, 1, List.of(), TYPES);
        final Step other = step(1, List.of(), List.of("user"));

        assertThat(EqlQueryWriter.eql(List.of(step, other), null, Duration.ofMinutes(1), 1)).isEqualTo("""
                sequence with maxspan=1m
                  [any where action == "exec" and (user == "alice" or host like~ "web*")] by user
                  [any where true] by user""");
        assertThat(EqlQueryWriter.countPipeline(step, 10))
                .isEqualTo("| WHERE action == \"exec\" AND (user == \"alice\" OR TO_LOWER(host) LIKE \"web*\") "
                           + "| LIMIT 11 | STATS n = COUNT(*)");
    }

    private static Filter filter(final String field, final Condition condition, final String value) {
        return new Filter(field, condition, value, false);
    }

    private static Step step(final int doc, final List<Filter> filters, final List<String> by) {
        return new Step(doc, filters, by, false, 1, List.of(), TYPES);
    }
}
