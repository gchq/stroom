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
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    void recentRecordsMoveByWholeDaysSoTheLatestIsYesterday() {
        final List<Map<String, String>> records = List.of(
                Map.of("EventTime", "2026-11-17T08:40:03.000Z", "UserId", "jsmith"),
                Map.of("EventTime", "2026-11-20T23:59:59.000Z", "UserId", "bob"),
                Map.of("EventTime", "", "UserId", "nobody"));

        final List<Map<String, String>> moved = EvalCases.recent(records, Map.of("EventTime", "date", "UserId",
                "text"), LocalDate.parse("2026-11-24"));

        assertThat(moved).extracting(record -> record.get("EventTime")).containsExactly("2026-11-21T08:40:03.000Z",
                "2026-11-24T23:59:59.000Z", "");
        assertThat(moved).extracting(record -> record.get("UserId")).containsExactly("jsmith", "bob", "nobody");
        assertThat(EvalCases.table(Map.of("name", "Logons", "events", "questions/skill-logons.csv", "recent", true,
                "fields", Map.of("EventTime", "date", "UserId", "text", "Outcome", "text", "Host", "text")))
                .records().stream().map(record -> record.get("EventTime").substring(0, 10)).max(String::compareTo))
                .contains(LocalDate.now(ZoneOffset.UTC).minusDays(1).toString());
    }

    @Test
    void theSkillCasesRunInTheChatAndTheHandWrittenCaseStartsFromItsPipeline() {
        final List<String> skills = EvalCases.IDS.stream()
                .filter(id -> EvalCases.isSkill(EvalCases.load(id)) && !EvalCases.isInstructions(EvalCases.load(id)))
                .toList();
        final List<String> instructions = EvalCases.IDS.stream()
                .filter(id -> EvalCases.isInstructions(EvalCases.load(id)))
                .toList();
        final Map<String, Object> handWritten = EvalCases.load("97_json_hand_written_add_source_ip");

        assertThat(skills).hasSize(9).allSatisfy(id -> assertThat(id).matches("(8[89]|9[0-6])_skill_.*"));
        assertThat(instructions).hasSize(4).allSatisfy(id -> assertThat(id).matches("(98|99|100|101)_agents_.*"));
        for (final String id : java.util.stream.Stream.concat(skills.stream(), instructions.stream()).toList()) {
            final Map<String, Object> evalCase = EvalCases.load(id);
            assertThat(evalCase).as(id).containsKeys("request", "expected", "reference");
            assertThat(EvalCases.isDrafted(evalCase)).as(id).isFalse();
            assertThat(EvalCases.maps(EvalCases.map(evalCase.get("reference")).get("calls"))).as(id).isNotEmpty();
        }
        assertThat(EvalCases.handWritten(handWritten)).containsEntry("feed", "EVAL-97")
                .containsEntry("pipeline", "EVAL-97-Events").containsKey("sample");
        assertThat(EvalCases.isDrafted(handWritten)).isFalse();
        assertThat(EvalCases.isDrafted(EvalCases.load("01_csv_vpn"))).isTrue();
        assertThat(EvalCases.handWritten(EvalCases.load("01_csv_vpn"))).isNull();
    }

    @Test
    void theCasesDescribingInPlaceStartFromAHandWrittenPipelineAndScoreWhatIsWrittenInPlace() {
        for (final String id : List.of("106_json_describe_in_place_pre_process",
                "107_json_describe_in_place_shared_two_feeds", "108_json_describe_in_place_users_text_kept",
                "109_json_describe_in_place_timeline_roles")) {
            final Map<String, Object> evalCase = EvalCases.load(id);
            assertThat(EvalCases.handWritten(evalCase)).as(id).isNotNull();
            assertThat(EvalCases.isDrafted(evalCase)).as(id).isFalse();
            assertThat(EvalCases.map(evalCase.get("expected")).get("in_place")).as(id).isInstanceOf(Map.class);
            assertThat(EvalCases.strings(EvalCases.map(EvalCases.map(evalCase.get("expected")).get("route"))
                    .get("forbidden_calls"))).as(id).contains("copy_pipeline", "promote_build");
        }
        assertThat(EvalCases.handWritten(EvalCases.load("106_json_describe_in_place_pre_process")))
                .containsEntry("pre_process", "vpn_op_codes_pre.xsl");
        assertThat(EvalCases.map(EvalCases.handWritten(EvalCases.load("107_json_describe_in_place_shared_two_feeds"))
                .get("shared_with"))).containsEntry("feed", "EVAL-107-B");
    }

    @Test
    void aCasesLuceneIndexesAreOneOrAList() {
        final Map<String, Object> one = Map.of("name", "Logons");
        final Map<String, Object> two = Map.of("name", "VPN");

        assertThat(EvalCases.luceneIndexes(Map.of("lucene_index", one))).containsExactly(one);
        assertThat(EvalCases.luceneIndexes(Map.of("lucene_index", List.of(one, two)))).containsExactly(one, two);
        assertThat(EvalCases.luceneIndexes(Map.of())).isEmpty();
        assertThat(EvalCases.luceneIndexes(EvalCases.load("92_skill_add_vpn_source")))
                .extracting(spec -> spec.get("name")).containsExactly("Logons", "VPN");
    }

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

    @Test
    void theInsiderCasesAnswersAreTheGeneratorsEvents() {
        final List<Map<String, String>> week = EvalCases.table((Map<String, Object>) EvalCases.load(
                "79_insider_leaver_week_timeline").get("elastic_index")).records();
        // The same records for the cases that share the week.
        for (final String id : List.of("80_insider_working_on_a_day", "81_insider_vpn_session_at_night")) {
            assertThat(EvalCases.table((Map<String, Object>) EvalCases.load(id).get("elastic_index")).records())
                    .as(id).isEqualTo(week);
        }
        assertThat(week).extracting(record -> record.get("PersonId")).contains(InsiderDataset.LEAVER,
                InsiderDataset.DRIFT);

        // 81: the leaver's VPN sessions between 18:00 and midnight on the 17th, Sydney time.
        final Instant nightFrom = Instant.parse("2026-09-17T08:00:00Z");
        final Instant nightTo = Instant.parse("2026-09-17T14:00:00Z");
        final Map<String, List<String>> sessions = new LinkedHashMap<>();
        week.stream()
                .filter(event -> InsiderDataset.LEAVER.equals(event.get("PersonId"))
                                 && "vpn".equals(event.get("Source")) && !time(event).isBefore(nightFrom)
                                 && time(event).isBefore(nightTo))
                .forEach(event -> sessions.computeIfAbsent(event.get("SessionId"), id -> new ArrayList<>())
                        .add(event.get("EventTime")));
        final List<List<Object>> vpnRows = new ArrayList<>();
        sessions.forEach((session, times) -> vpnRows.add(List.of(times.stream().min(String::compareTo)
                .orElseThrow(), times.stream().max(String::compareTo).orElseThrow(), session)));
        assertThat(vpnRows).isNotEmpty().isEqualTo(expectedRows(EvalCases.load("81_insider_vpn_session_at_night")));

        // 82: each archive then an upload of over 10 MB to personal cloud by the same person within the hour, an
        // event in one sequence at most (EQL's rule: each person's latest archive before the upload), in time order
        // (the background people's archives and personal cloud use come after the rest in the records).
        final Map<String, Object> uploads = EvalCases.load("82_insider_archive_then_personal_cloud_upload");
        final List<Map<String, String>> events = EvalCases.table((Map<String, Object>) uploads.get("elastic_index"))
                .records().stream()
                .sorted(java.util.Comparator.comparing(TestEvalCases::time))
                .toList();
        final List<List<Object>> sequences = new ArrayList<>();
        final Map<String, Map<String, String>> archives = new LinkedHashMap<>();
        for (final Map<String, String> event : events) {
            final Map<String, String> archive = archives.get(event.get("PersonId"));
            if ("file.archive".equals(event.get("EventCode"))) {
                archives.put(event.get("PersonId"), event);
            } else if (archive != null && InsiderDataset.PERSONAL_CLOUD_CATEGORY.equals(event.get("Category"))
                       && Long.parseLong(event.getOrDefault("BytesOut", "0")) > 10_000_000
                       && Duration.between(time(archive), time(event)).compareTo(Duration.ofHours(1)) <= 0) {
                sequences.add(List.of(event.get("PersonId"), archive.get("EventTime"), event.get("EventTime")));
                archives.remove(event.get("PersonId"));
            }
        }
        assertThat(sequences).hasSize(6).isEqualTo(expectedRows(uploads));
        // Hand pairing can't do: the archives and personal cloud events are past what one esql_query gives.
        assertThat(events.stream().filter(event -> "file.archive".equals(event.get("EventCode"))
                                                   || InsiderDataset.PERSONAL_CLOUD_CATEGORY.equals(
                event.get("Category")))).hasSizeGreaterThan(stroom.ai.api.ElasticEsql.MAX_ROWS);
        // The near misses are there and none is a sequence.
        final List<Map<String, Object>> misses = (List<Map<String, Object>>) InsiderDataset.generate(
                new InsiderDataset.Options(InsiderDataset.SEED, 30, 0.1, List.of(new InsiderDataset.Period(
                        java.time.LocalDate.of(2026, 6, 1), java.time.LocalDate.of(2026, 9, 30))))).knownAnswer()
                .get("near_misses");
        assertThat(misses).extracting(miss -> miss.get("person_id")).containsExactlyInAnyOrder("P20001", "P20003",
                "P20004");
        assertThat(sequences).allSatisfy(row -> assertThat(row.getFirst()).isEqualTo(InsiderDataset.LEAVER));

        // 80: the drift person's tasks that day, as the known answer has them.
        final InsiderDataset dataset = InsiderDataset.generate(new InsiderDataset.Options(InsiderDataset.SEED, 5,
                0.3, List.of(new InsiderDataset.Period(java.time.LocalDate.of(2026, 9, 16),
                java.time.LocalDate.of(2026, 9, 16)))));
        final List<String> tasks = ((Map<String, List<Map<String, Object>>>) ((Map<String, Object>) dataset
                .knownAnswer().get("tasks")).get(InsiderDataset.DRIFT)).get("2026-09-16").stream()
                .map(task -> String.valueOf(task.get("task")))
                .distinct()
                .sorted()
                .toList();
        assertThat(tasks).isNotEmpty().isEqualTo(((Map<String, Object>) EvalCases.load(
                "80_insider_working_on_a_day").get("expected")).get("activities"));
    }

    @Test
    void theCompareCasesFindingsAreTheGeneratorsKnownAnswer() {
        final Map<String, Object> known = InsiderDataset.generate(new InsiderDataset.Options(InsiderDataset.SEED, 12,
                0.15, List.of(new InsiderDataset.Period(LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 2)))))
                .knownAnswer();
        final Map<String, Map<String, Object>> plants = new LinkedHashMap<>();
        ((List<Map<String, Object>>) known.get("plants")).forEach(plant -> plants.put(String.valueOf(plant.get(
                "person_id")), plant));
        final Map<String, Map<String, Object>> decoys = new LinkedHashMap<>();
        ((List<Map<String, Object>>) known.get("decoys")).forEach(decoy -> decoys.put(String.valueOf(decoy.get(
                "kind")), decoy));
        final String pipeline = String.valueOf(decoys.get("source-wide pipeline change").get("from"));

        // 83: the leaver's job-site browsing from the plant's first day, her bulk downloads from 8 September; the
        // pipeline change is not hers.
        final Map<String, Object> leaver = EvalCases.load("83_compare_leaver_before_leaving");
        final Map<String, Object> leaverPlant = plants.get(InsiderDataset.LEAVER);
        assertThat(matcher(leaver, "must_include", 0)).containsEntry("near", leaverPlant.get("from"))
                .containsEntry("feature", "category:" + InsiderDataset.JOB_CATEGORY);
        assertThat(matcher(leaver, "must_include", 1)).containsEntry("kind", "week")
                .containsEntry("near", "2026-09-08");
        assertThat(String.join(" ", (List<String>) leaverPlant.get("indicators")))
                .contains("bulk downloads on working days 2026-09-08");
        assertThat(matcher(leaver, "must_not_include", 0)).containsEntry("near", pipeline);
        assertThat(String.valueOf(leaver.get("request"))).contains("25 September 2026");
        assertThat(((Map<String, Object>) leaverPlant.get("facts")).get("leaving")).isEqualTo("2026-09-25");
        final List<Map<String, String>> leaverEvents = EvalCases.table((Map<String, Object>) leaver.get(
                "elastic_index")).records().stream()
                .filter(event -> InsiderDataset.LEAVER.equals(event.get("PersonId")))
                .toList();
        assertThat(leaverEvents).anySatisfy(event -> assertThat(event.get("Category"))
                .isEqualTo(InsiderDataset.JOB_CATEGORY));
        assertThat(leaverEvents).anySatisfy(event -> assertThat(event.get("Site")).isEqualTo("sites/legal"));

        // 84: the viewer's look-ups from the plant's start, above her peers of the plant's Organisation, read from
        // the events' XML in Stroom (one event a record).
        final Map<String, Object> viewer = EvalCases.load("84_compare_viewer_against_team");
        final Map<String, Object> viewerPlant = plants.get(InsiderDataset.VIEWER);
        assertThat(matcher(viewer, "must_include", 0)).containsEntry("evidence_contains", "Organisation '"
                + ((Map<String, Object>) viewerPlant.get("facts")).get("peers") + "'");
        assertThat(matcher(viewer, "must_include", 1)).containsEntry("near", viewerPlant.get("from"));
        final Map<String, Object> generate = (Map<String, Object>) ((Map<String, Object>) viewer.get("elastic_index"))
                .get("generate");
        assertThat(EvalQuestionRecords.storesInsiderStreams(generate)).isTrue();
        final List<String> xml = EvalQuestionRecords.insiderEventXml(generate);
        final List<Map<String, String>> viewerRecords = EvalCases.table((Map<String, Object>) viewer.get(
                "elastic_index")).records();
        assertThat(xml).hasSameSizeAs(viewerRecords);
        assertThat(xml.getFirst()).contains("<Organisation>");
        assertThat(xml.getFirst()).contains(viewerRecords.getFirst().get("PersonId"));

        // 85: a new country while on leave, as planted.
        final Map<String, Object> compromised = EvalCases.load("85_compare_compromised_in_april");
        final Map<String, Object> facts = (Map<String, Object>) plants.get(InsiderDataset.COMPROMISED).get("facts");
        assertThat(matcher(compromised, "must_include", 0)).containsEntry("window", facts.get("country"));
        final List<String> leave = (List<String>) facts.get("leave");
        assertThat((List<String>) ((Map<String, Object>) ((Map<String, Object>) compromised.get("reference"))
                .get("compare_activity")).get("leave")).containsExactly(leave.get(0) + ".." + leave.get(1));
        assertThat(EvalCases.table((Map<String, Object>) compromised.get("elastic_index")).records())
                .anySatisfy(event -> {
                    assertThat(event.get("PersonId")).isEqualTo(InsiderDataset.COMPROMISED);
                    assertThat(event.get("Country")).isEqualTo(facts.get("country"));
                });

        // 86: the migration's days and the pipeline change aren't the person's.
        final Map<String, Object> decoy = EvalCases.load("86_compare_decoys_not_flagged");
        final Map<String, Object> migration = decoys.get("approved migration");
        assertThat(matcher(decoy, "must_not_include", 0)).containsEntry("window_in", List.of(migration.get("from"),
                migration.get("to")));
        assertThat(matcher(decoy, "must_not_include", 1)).containsEntry("near", pipeline);
        assertThat((List<String>) ((Map<String, Object>) ((Map<String, Object>) decoy.get("reference"))
                .get("compare_activity")).get("exceptions")).containsExactly(migration.get("from") + ".."
                                                                              + migration.get("to") + ": "
                                                                              + migration.get("exception"));
    }

    @Test
    void theDoneToThemCaseIsTheGeneratorsKnownAnswer() {
        final Map<String, Object> evalCase = EvalCases.load("87_compare_done_to_leaver_before_leaving");
        final Map<String, Object> grant = ((List<Map<String, Object>>) InsiderDataset.generate(
                        InsiderDataset.Options.reduced()).knownAnswer().get("targets")).stream()
                .filter(plant -> InsiderDataset.LEAVER.equals(plant.get("person_id")))
                .findFirst()
                .orElseThrow();
        final String day = String.valueOf(((Map<String, Object>) grant.get("facts")).get("day"));

        // The grant, done to her by the administrator on its day, is in the case's records, the group its Object; the
        // answer must name the group and who granted it (their person id or account, or name), whatever tool found
        // them.
        final List<Map<String, String>> records = EvalCases.table((Map<String, Object>) evalCase.get(
                "elastic_index")).records();
        final Map<String, String> granted = records.stream()
                .filter(record -> InsiderDataset.LEAVER.equals(record.get("TargetPersonId")))
                .findFirst()
                .orElseThrow();
        assertThat(records).filteredOn(record -> InsiderDataset.LEAVER.equals(record.get("TargetPersonId")))
                .singleElement().satisfies(record -> {
                    assertThat(record).containsEntry("PersonId", InsiderDataset.GRANTING_ADMIN)
                            .containsEntry("Action", "AddGroup")
                            .containsEntry("Object", InsiderDataset.PRIVILEGED_GROUP);
                    assertThat(record.get("EventTime")).startsWith(day + "T00:");
                });
        final Map<String, Object> findings = (Map<String, Object>) ((Map<String, Object>) evalCase.get("expected"))
                .get("findings");
        assertThat((List<List<String>>) findings.get("answer_must_say_all_of")).containsExactly(
                List.of(InsiderDataset.PRIVILEGED_GROUP), List.of(InsiderDataset.GRANTING_ADMIN,
                        granted.get("UserName"), "Ben Petrov"));
        assertThat(((Map<String, Object>) evalCase.get("expected"))).doesNotContainKeys("tools_used", "answer");
        // Her own events are there too, and none done to her among them.
        assertThat(records).anySatisfy(record -> assertThat(record).containsEntry("PersonId", InsiderDataset.LEAVER)
                .doesNotContainKey("TargetPersonId"));
        assertThat((Map<String, Object>) ((Map<String, Object>) evalCase.get("reference")).get("compare_activity"))
                .containsEntry("includeTargets", true).doesNotContainKey("targetPersonField");
        assertThat(String.valueOf(evalCase.get("agents"))).contains("- target person: TargetPersonId\n");
    }

    @Test
    void theInsiderCasesFieldsAreTheSitesAgentsDocs() {
        // As a site keeps them: the person and user fields in an AGENTS doc beside the index, so neither the
        // question, the user nor the reference names them.
        for (final String id : List.of("79_insider_leaver_week_timeline", "80_insider_working_on_a_day",
                "81_insider_vpn_session_at_night", "82_insider_archive_then_personal_cloud_upload",
                "83_compare_leaver_before_leaving", "84_compare_viewer_against_team", "85_compare_compromised_in_april",
                "86_compare_decoys_not_flagged", "87_compare_done_to_leaver_before_leaving")) {
            final Map<String, Object> evalCase = EvalCases.load(id);
            assertThat(String.valueOf(evalCase.get("agents"))).as(id).contains("## Fields\n- person: PersonId\n"
                                                                               + "- user: UserName\n");
            assertThat(String.valueOf(evalCase.get("user_knows"))).as(id).doesNotContain("PersonId", "UserName");
            ((Map<String, Object>) evalCase.get("reference")).values().forEach(call -> assertThat(
                    (Map<String, Object>) call).as(id).doesNotContainKeys("personField", "userField",
                    "targetPersonField"));
        }
    }

    // One of a compare case's finding matchers.
    private static Map<String, Object> matcher(final Map<String, Object> evalCase, final String list, final int at) {
        return (Map<String, Object>) ((List<?>) ((Map<String, Object>) ((Map<String, Object>) evalCase.get(
                "expected")).get("findings")).get(list)).get(at);
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
