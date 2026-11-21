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

package stroom.mcp.impl.e2e;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The question cases' tables of records ([EvalCases#table]), and the answers the join and sequence questions (cases
/// 71 to 75) expect, worked out here by brute force over the same records: every pair of events tried, no query.
@SuppressWarnings("unchecked")
class TestEvalCases {

    private static final Instant WEEK_FROM = Instant.parse("2026-01-05T00:00:00Z");
    private static final Instant WEEK_TO = Instant.parse("2026-01-12T00:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final String PAST_THE_LIMIT = "75_stroomql_join_exports_past_the_row_limit";
    // run_stroomql's default row limit (AiWorkspaceConfig).
    private static final int MAX_QUERY_ROWS = 10_000;

    @Test
    void aQuestionsTablesAreItsRecordsWithEachColumnsType() {
        final Map<String, Object> evalCase = EvalCases.load("71_stroomql_join_exports_by_department");

        final EvalCases.QuestionTable events = EvalCases.table((Map<String, Object>) evalCase.get("lucene_index"));
        final EvalCases.QuestionTable hr = EvalCases.table(((List<Map<String, Object>>) evalCase
                .get("data_connections")).getFirst());

        assertThat(events.name()).isEqualTo("Staff activity");
        assertThat(events.timeField()).isEqualTo("EventTime");
        assertThat(events.records()).hasSize(85).first().isEqualTo(Map.of("EventTime", "2026-01-02T09:00:00.000Z",
                "UserId", "erin", "Action", "Export", "Files", "300"));
        assertThat(hr.name()).isEqualTo("HR");
        assertThat(hr.timeField()).isNull();
        assertThat(hr.records()).hasSize(9).extracting(record -> record.get("Status")).containsOnly("Active",
                "Leaver");
        // The proxy uploads have no table of their own.
        assertThat(EvalCases.table(Map.of("name", "Proxy uploads"))).isNull();
    }

    @Test
    void aColumnWithNoTypeOrAnUnknownOneIsRefused() {
        assertThatThrownBy(() -> EvalCases.table(Map.of("name", "HR", "csv", "questions/hr-users.csv",
                "columns", Map.of("Username", "text", "Department", "text"))))
                .hasMessageContaining("column Status has type null");
        assertThatThrownBy(() -> EvalCases.table(Map.of("name", "HR", "csv", "questions/hr-users.csv",
                "columns", Map.of("Username", "text", "Department", "text", "Status", "keyword"))))
                .hasMessageContaining("column Status has type keyword, not one of [text, long, date]");
    }

    @Test
    void aQuestionsRecordsMayBeGeneratedTheSameEachTime() {
        final Map<String, Object> evalCase = EvalCases.load(PAST_THE_LIMIT);
        final Map<String, Object> spec = (Map<String, Object>) evalCase.get("lucene_index");

        final EvalCases.QuestionTable events = EvalCases.table(spec);
        final EvalCases.QuestionTable again = EvalCases.table(spec);
        final EvalCases.QuestionTable hr = EvalCases.table(((List<Map<String, Object>>) evalCase
                .get("data_connections")).getFirst());

        assertThat(events.name()).isEqualTo("Staff activity");
        assertThat(events.timeField()).isEqualTo("EventTime");
        assertThat(events.records()).hasSize(42_000).isEqualTo(again.records());
        assertThat(events.records().getFirst().keySet()).containsExactly("EventTime", "UserId", "Action", "Files");
        assertThat(events.records()).extracting(event -> event.get("Action"))
                .containsOnly("Logon", "Export", "Logoff");
        assertThat(hr.records()).hasSize(12_000).first().extracting(record -> record.get("Username"))
                .isEqualTo("user00001");
        assertThatThrownBy(() -> EvalQuestionRecords.generate(Map.of("kind", "nothing", "seed", 1, "users", 1)))
                .hasMessageContaining("No question records generator nothing");
    }

    @Test
    void theExportsPastTheLimitAreMoreRowsThanAQueryMayReturnEveryWayButGrouped() {
        final Map<String, Object> evalCase = EvalCases.load(PAST_THE_LIMIT);
        final List<Map<String, String>> exports = week(evalCase, event -> "Export".equals(event.get("Action")));

        // A hand join reads more rows than run_stroomql returns (by default 10,000) whichever way it goes: the
        // week's exports, each user's exports totalled, or HR's users.
        assertThat(exports).hasSizeGreaterThan(MAX_QUERY_ROWS);
        assertThat(exports.stream().map(event -> event.get("UserId")).distinct().count())
                .isGreaterThan(MAX_QUERY_ROWS);
        assertThat(hr(evalCase)).hasSizeGreaterThan(MAX_QUERY_ROWS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"71_stroomql_join_exports_by_department", PAST_THE_LIMIT})
    void theExportsByDepartmentAreTheJoinedWeeksFilesSummed(final String id) {
        final Map<String, Object> evalCase = EvalCases.load(id);
        final Map<String, Map<String, String>> hr = hr(evalCase);
        final Map<String, Long> files = new LinkedHashMap<>();
        for (final Map<String, String> event : week(evalCase, event -> "Export".equals(event.get("Action")))) {
            // Every exporter is in HR, so an inner join and a left join give the same rows.
            assertThat(hr).containsKey(event.get("UserId"));
            files.merge(hr.get(event.get("UserId")).get("Department"), Long.parseLong(event.get("Files")), Long::sum);
        }
        final List<List<Object>> rows = new ArrayList<>();
        files.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> rows.add(List.of(entry.getKey(), entry.getValue().intValue())));

        assertThat(rows).as(id).isEqualTo(expectedRows(evalCase));
    }

    @Test
    void theLeaversWhoLoggedOnAreTheWeeksLogonsOfHrsLeavers() {
        final Map<String, Object> evalCase = EvalCases.load("72_stroomql_leavers_still_logging_on");
        final Map<String, Map<String, String>> hr = hr(evalCase);
        final Map<String, Integer> logons = new LinkedHashMap<>();
        for (final Map<String, String> event : week(evalCase, event -> "Logon".equals(event.get("Action")))) {
            final Map<String, String> user = hr.get(event.get("UserId"));
            if (user != null && "Leaver".equals(user.get("Status"))) {
                logons.merge(event.get("UserId"), 1, Integer::sum);
            }
        }
        final List<List<Object>> rows = new ArrayList<>();
        logons.forEach((user, count) -> rows.add(List.of(user, count)));

        assertThat(rows).containsExactlyInAnyOrderElementsOf(expectedRows(evalCase));
    }

    @Test
    void theSequencesAreEveryLogonThenExportOfAUserWithinTheWindow() {
        for (final String id : List.of("73_sequence_logon_then_export_lucene",
                "74_sequence_logon_then_export_elastic")) {
            final Map<String, Object> evalCase = EvalCases.load(id);
            final List<Map<String, String>> week = week(evalCase, event -> true);
            final List<List<Object>> pairs = new ArrayList<>();
            for (final Map<String, String> logon : week) {
                for (final Map<String, String> export : week) {
                    if ("Logon".equals(logon.get("Action")) && "Export".equals(export.get("Action"))
                        && logon.get("UserId").equals(export.get("UserId"))) {
                        final Duration gap = Duration.between(time(logon), time(export));
                        if (!gap.isNegative() && gap.compareTo(WINDOW) <= 0) {
                            pairs.add(List.of(logon.get("UserId"), logon.get("EventTime"), export.get("EventTime")));
                        }
                    }
                }
            }

            assertThat(pairs).as(id).containsExactlyInAnyOrderElementsOf(expectedRows(evalCase));
            // Each user's pair is their only one, so EQL (each event in one sequence) and the windowed join (every
            // pair) agree.
            assertThat(pairs).as(id).extracting(List::getFirst).doesNotHaveDuplicates();
        }
    }

    // The case's events (its Lucene or Elastic index's) in the week, that the filter keeps.
    private static List<Map<String, String>> week(final Map<String, Object> evalCase,
                                                  final Predicate<Map<String, String>> filter) {
        final Map<String, Object> spec = (Map<String, Object>) (evalCase.containsKey("lucene_index")
                ? evalCase.get("lucene_index")
                : evalCase.get("elastic_index"));
        return EvalCases.table(spec).records().stream()
                .filter(event -> !time(event).isBefore(WEEK_FROM) && time(event).isBefore(WEEK_TO))
                .filter(filter)
                .toList();
    }

    // The case's HR records, by user.
    private static Map<String, Map<String, String>> hr(final Map<String, Object> evalCase) {
        final Map<String, Map<String, String>> byUser = new LinkedHashMap<>();
        EvalCases.table(((List<Map<String, Object>>) evalCase.get("data_connections")).getFirst()).records()
                .forEach(record -> byUser.put(record.get("Username"), record));
        return byUser;
    }

    private static Instant time(final Map<String, String> event) {
        return Instant.parse(event.get("EventTime"));
    }

    private static List<List<Object>> expectedRows(final Map<String, Object> evalCase) {
        return (List<List<Object>>) ((Map<String, Object>) ((Map<String, Object>) evalCase.get("expected"))
                .get("answer")).get("rows");
    }
}
