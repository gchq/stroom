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

import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.sample.SourceTypeRegistry;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.Source;
import stroom.data.store.api.SourceUtil;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.data.store.api.TargetUtil;
import stroom.mcp.impl.McpTestClient;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.StandardHeaderArguments;
import stroom.security.api.SecurityContext;

import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// An aggregated feed walked through the REAL tools as an agent would, one walk per synthetic sample family under
/// `aggregated/` (made by aggregated-feeds-generator.py): PEGA's JSON wrapper around a message holding JSON, text or
/// SOAP (`sourcetype`); Azure's audit log, with no wrapper (`Workload`); and a collector's `LogFilePath`, matched by
/// patterns. Every source type group has a pipeline of its own.
///
/// Each walk's first build surveys the feed, confirms its source type field, profiles the source types and writes the
/// registry with a scope of one source type; classifies, drafts and builds that group from the draft (where its Events
/// go asked in a form), makes the group's pipeline, steps it clean, processes it, reviews coverage (every other
/// record passed over and summed from the group's own lines, the other source types parked, no kind missed), plans
/// the feed's index and promotes. Its second build carries on: a second group with a pipeline of its own (in the
/// PEGA walk writing to an output feed of its own, with an index of its own), the promoted first group changed
/// through working copies (in the Azure walk); data arriving later holds a value no registry row matches, which
/// neither pipeline warns of (counted with the rest passed over); both pipelines process it, coverage is reviewed,
/// and promotion leaves the registry beside the feed with both groups translated, each by its own pipeline.
///
/// A last walk translates an aggregated feed whole, as a normal source: its later data's source type value the
/// survey didn't see is logged once a stream, triaged benign, and reported by review_coverage.
@SuppressWarnings("unchecked")
class TestE2eAggregatedFeedWalks extends AbstractE2eTest {

    private static final String STROOM_FLAT = "Stroom flat convention";
    private static final int RECORDS = 1000;
    private static final int LATER_RECORDS = 300;
    private static final String OWN_FEED = "To an output feed of its own";
    private static final String PLACEMENT = "is new. It gets a pipeline of its own";
    private static final String PIPELINE_FORM = "runs in pipeline";
    // In a family's rules: common entries the agent adds to the draft's.
    private static final String COMMON_ADDED = "common_added";
    private static final String AZURE_FIRST_RULES = """
            {"common_added": [{"path": "EventSource/Device/Name", "value": "Microsoft 365"}],
             "events": [
              {"name": "logon", "when": [{"field": "Operation", "equals": "UserLoggedIn"}],
               "fields": [{"path": "EventDetail/TypeId", "value": "Logon"},
                          {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                          {"path": "EventDetail/Authenticate/User/Id", "field": "UserId"},
                          {"path": "EventDetail/Authenticate/Outcome/Success", "value": "true"}]},
              {"name": "logon_failed", "when": [{"field": "Operation", "equals": "UserLoginFailed"}],
               "fields": [{"path": "EventDetail/TypeId", "value": "LogonFailed"},
                          {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                          {"path": "EventDetail/Authenticate/User/Id", "field": "UserId"},
                          {"path": "EventDetail/Authenticate/Outcome/Success", "value": "false"}]},
              {"name": "other", "allow_unknown": "role and password changes, for a later build",
               "fields": [{"path": "EventDetail/TypeId", "field": "Operation"},
                          {"path": "EventDetail/Unknown/Data", "data_name": "user",
                           "field": "UserId"}]}]}""";

    @Inject
    private Store streamStore;
    @Inject
    private SecurityContext securityContext;

    @Test
    void pegasWrappedMessagesInTwoBuildsTheSecondGroupWithAnOutputFeedOfItsOwn() {
        walk(new Family("pega", "pega/pega-1.json", "pega/pega-2.json", "sourcetype", "PegaSecurityEvent",
                rules(TestE2eSourceTypeGroups.securityMapping()), "PegaIntegration",
                rules(TestE2eSourceTypeGroups.integrationMapping()), "PegaBatch", true, false));
    }

    @Test
    void azuresAuditLogByWorkloadInTwoBuildsTheFirstGroupChangedThroughWorkingCopies() {
        walk(new Family("azure", "azure-audit/azure-audit-1.json", "azure-audit/azure-audit-2.json", "Workload",
                "AzureActiveDirectory", TestE2eSourceTypeGroups.jsonValue(AZURE_FIRST_RULES), "ExchangeOnline",
                TestE2eSourceTypeGroups.jsonValue("""
                        {"events": [
                          {"name": "mail", "allow_unknown": "mailbox activity, for a later build",
                           "fields": [{"path": "EventDetail/TypeId", "field": "Operation"},
                                      {"path": "EventDetail/Unknown/Data", "data_name": "user",
                                       "field": "UserId"}]}]}"""),
                "PowerBI", false, true));
    }

    @Test
    void collectorPathsByPatternInTwoBuildsBothGroupsIntoTheFeedsEvents() {
        walk(new Family("collector", "collector-paths/collector-paths-1.json",
                "collector-paths/collector-paths-2.json", "LogFilePath", "/var/log/secure",
                TestE2eSourceTypeGroups.jsonValue("""
                        {"extract": [{"field": "message",
                                      "regex": "sshd\\\\[\\\\d+\\\\]: (Accepted|Failed) password for \
                        (\\\\S+) from (\\\\S+) port",
                                      "names": ["result", "user", "client"]}],
                         "events": [
                          {"name": "logon", "when": [{"field": "result", "equals": "Accepted"}],
                           "fields": [{"path": "EventDetail/TypeId", "value": "Logon"},
                                      {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                                      {"path": "EventDetail/Authenticate/User/Id", "field": "user"},
                                      {"path": "EventDetail/Authenticate/Outcome/Success", "value": "true"}]},
                          {"name": "logon_failed", "when": [{"field": "result", "equals": "Failed"}],
                           "fields": [{"path": "EventDetail/TypeId", "value": "LogonFailed"},
                                      {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                                      {"path": "EventDetail/Authenticate/User/Id", "field": "user"},
                                      {"path": "EventDetail/Authenticate/Outcome/Success", "value": "false"}]},
                          {"name": "other", "allow_unknown": "other sshd lines, for a later build",
                           "fields": [{"path": "EventDetail/TypeId", "value": "sshd"},
                                      {"path": "EventDetail/Unknown/Data", "data_name": "message",
                                       "field": "message"}]}]}"""),
                "^/var/log/mail/audit/", TestE2eSourceTypeGroups.jsonValue("""
                        {"events": [
                          {"name": "audit", "allow_unknown": "mailbox administration, for a later build",
                           "fields": [{"path": "EventDetail/TypeId", "value": "mailaudit"},
                                      {"path": "EventDetail/Unknown/Data", "data_name": "message",
                                       "field": "message"}]}]}"""),
                "/srv/batch/job.log", false, false));
    }

    @Test
    void aFeedTranslatedWholeLogsASourceTypeItsSurveyDidNotSeeOnceAStream() {
        final String feed = "E2E-WALK-WHOLE";
        final String build = "walk-whole";
        final String text = TestE2eSourceTypeGroups.lines("azure-audit/azure-audit-1.json", RECORDS);
        createFeed("walk-whole-src", feed);
        final long stream = upload(feed, text);
        json("start_build", args("build", build, "feeds", List.of(feed)));
        final Map<String, Object> surveyed = json("survey_feed", args("feed", feed, "build", build));
        assertThat(map(surveyed, "source_types")).as("the source type field confirmed: %s",
                surveyed.get("source_types")).containsEntry("field", "Workload");

        // Translated whole, as a normal source: no registry, no group. The values the survey saw are kept.
        final Map<String, Object> rules = new LinkedHashMap<>(TestE2eSourceTypeGroups.jsonValue(
                AZURE_FIRST_RULES));
        classified(build, List.of(stream), rules);
        final Map<String, Object> draft = agreed("draft_translation_mapping", args("stream_ids", List.of(stream),
                "build", build));
        final Map<String, Object> mapping = withRules(map(draft, "mapping"), rules);
        // The system's name and environment as the user gives them.
        final List<Map<String, Object>> common = new ArrayList<>();
        for (final Map<String, Object> entry : list(mapping, "common")) {
            final Map<String, Object> given = new LinkedHashMap<>(entry);
            if ("EventSource/System/Name".equals(entry.get("path"))) {
                given.put("value", "Microsoft 365");
            } else if ("EventSource/System/Environment".equals(entry.get("path"))) {
                given.put("value", "Prod");
            }
            common.add(given);
        }
        mapping.put("common", common);
        final Map<String, Object> saved = agreed("build_translation_xslt", args("build", build, "feeds",
                List.of(feed), "name", feed + "-XSLT", "schema_version", VERSION, "stream_ids", List.of(stream),
                "mapping", mapping));
        dump("build_translation_xslt whole", saved);
        assertThat(saved).as("saved: %s", saved.get("problems")).containsEntry("ok", true).doesNotContainKey(
                "group");
        assertThat(String.valueOf(saved.get("seen_source_types"))).contains("Workload");
        final Map<String, Object> seen = map(keptMapping(uuidOf(saved)), "seen_source_types");
        assertThat(seen).containsEntry("field", "Workload");
        assertThat((List<String>) seen.get("values")).contains("SharePoint", "ExchangeOnline",
                "AzureActiveDirectory").doesNotContain("PowerBI");
        final String pipeline = uuidOf(agreed("create_pipeline", args("build", build, "name", feed + "-Events",
                "template_uuid", templateUuid("translation", "Event Data (JSON)"))));

        // The surveyed data: every value seen, nothing logged of them.
        assertThat(stepSample(pipeline, List.of(stream))).containsEntry("verdict", "clean");
        final Map<String, Object> first = list(processed(pipeline, List.of(stream)), "streams").getFirst();
        assertThat(streamText(ids(first, "errors").getFirst())).doesNotContain(XsltGenerator.UNSEEN_SOURCE_TYPE);

        // Later data carries a source the survey never met: logged once a stream however many records, benign.
        final long later = send(feed, withNewSourceType(TestE2eSourceTypeGroups.lines(
                "azure-audit/azure-audit-2.json", LATER_RECORDS), "Workload", "PowerBI"));
        final long laterAgain = send(feed, withNewSourceType(TestE2eSourceTypeGroups.slice(
                "azure-audit/azure-audit-2.json", LATER_RECORDS, LATER_RECORDS), "Workload", "PowerBI"));
        final Map<String, Object> stepped = stepSample(pipeline, List.of(later));
        assertThat(stepped).as("stepped clean: %s", stepped).containsEntry("verdict", "clean");
        final Map<String, Object> done = process(pipeline, List.of(later, laterAgain));
        final String line = XsltGenerator.UNSEEN_SOURCE_TYPE + "PowerBI" + XsltGenerator.UNSEEN_SOURCE_TYPE_IN
                            + "Workload" + XsltGenerator.UNSEEN_SOURCE_TYPE_END;
        for (final Map<String, Object> each : list(done, "streams")) {
            final String logged = streamText(ids(each, "errors").getFirst());
            assertThat(TestE2eSourceTypeGroups.count(logged, line)).as("once a stream: %s", logged).isEqualTo(1);
            // Its records translated by the rules as any other.
            assertThat(TestE2eSourceTypeGroups.count(streamText(ids(each, "events").getFirst()), "<Event>"))
                    .isEqualTo(LATER_RECORDS);
        }
        assertThat(list(done, "look_into")).filteredOn(entry -> String.valueOf(entry.get("message")).contains(
                "New source type 'PowerBI'")).isNotEmpty().allSatisfy(entry -> assertThat(entry)
                .containsEntry("class", "benign"));
        assertThat(list(done, "problems")).isEmpty();

        // review_coverage reports it, for the user to survey the feed again; no registry is written.
        final Map<String, Object> review = json("review_coverage", args("pipeline_uuid", pipeline, "follow_on",
                false));
        dump("review_coverage whole", review);
        final Map<String, Object> sourceTypes = map(review, "source_types");
        assertThat(map(map(sourceTypes, "unseen_source_types"), "PowerBI")).containsEntry("raw_streams",
                List.of(later, laterAgain));
        assertThat(sourceTypes).doesNotContainKeys("new_source_types", "group", "processing");
        assertThat(String.valueOf(review.get("tell_user"))).contains("PowerBI", "surveys the feed again");
    }

    // --- the walk ---

    private void walk(final Family family) {
        final String tag = family.name().toUpperCase(Locale.ROOT);
        final String feed = "E2E-WALK-" + tag;
        final String folder = "E2E Feeds/Walk" + tag;
        final String index = "e2e-walk-" + family.name();
        final String firstName = tag + " first";
        final String secondName = tag + " second";
        final String template = templateUuid("translation", "Event Data (JSON)");
        final String text = TestE2eSourceTypeGroups.lines(family.file(), RECORDS);
        createFeed("walk-src-" + family.name(), feed);
        final long stream = upload(feed, text);
        promote("walk-src-" + family.name(), Map.of("Feed", folder));

        // --- The first build: the source types step, one group, its pipeline, processing, coverage, index. ---
        final String first = "walk-" + family.name() + "-1";
        json("start_build", args("build", first, "feeds", List.of(feed)));
        final Map<String, Object> surveyed = json("survey_feed", args("feed", feed, "build", first));
        assertThat(map(surveyed, "source_types")).as("the source type field found: %s", surveyed.get("source_types"))
                .containsEntry("field", family.field());
        final Map<String, Object> shown = json("record_source_types", args("build", first, "feed", feed,
                "system_name", tag, "environment", "Prod"));
        assertThat(shown).containsEntry("status", "needs_review");
        final String firstType = rowName(shown, family.firstGroup());
        client.answeringWith(answering(List.of(firstType), null));
        final Map<String, Object> written = json("record_source_types", args("build", first, "feed", feed,
                "system_name", tag, "environment", "Prod", "reviewed", shown.get("review_id"), "rows",
                shown.get("rows")));
        assertThat(strings(written, "scope")).containsExactly(firstType);

        classified(first, List.of(stream), family.firstRules());
        final int placedBefore = client.forms().size();
        final Saved firstSaved = group(first, feed, firstName, firstType, family.firstRules(), stream);
        assertThat(formsSince(placedBefore)).anySatisfy(form -> assertThat(form.message())
                .contains("'" + firstName + "'", PLACEMENT));
        final String firstPipelineName = feed + " " + firstName;
        assertThat(map(firstSaved.group(), "pipeline")).containsEntry("name", firstPipelineName);
        final Map<String, Object> made = agreed("create_pipeline", args("build", first, "group", firstName,
                "template_uuid", template));
        assertThat(made).containsEntry("name", firstPipelineName);
        assertThat(strings(made, "source_types")).containsExactly(firstType);
        final String firstPipeline = uuidOf(made);
        final List<Long> firstEvents = events(firstPipeline, List.of(stream));

        // Coverage: the group's own lines summed, every other record passed over; no kind missed.
        final Map<String, Object> firstReview = json("review_coverage", args("pipeline_uuid", firstPipeline,
                "follow_on", false));
        dump("review_coverage " + first, firstReview);
        final Map<String, Object> firstTypes = map(firstReview, "source_types");
        assertThat(firstTypes).containsEntry("group", firstName).doesNotContainKeys("other_groups",
                "new_source_types", "unseen_source_types");
        assertThat(passedOver(firstTypes)).isEqualTo(RECORDS - records(text, family, firstType, written));
        assertThat(String.valueOf(firstReview.get("tell_user"))).contains("Judged against source type group '"
                                                                          + firstName + "'", "passed over");
        assertThat(firstReview).doesNotContainKey("passed_over");
        noKindMissed(firstReview, firstType);
        // The other source types parked, for later builds.
        final Map<String, Map<String, Object>> firstRows = rows(registryInBuild(firstSaved));
        firstRows.forEach((name, row) -> assertThat(row).as(name).containsEntry("status", name.equals(firstType)
                ? SourceTypeRegistry.TRANSLATED
                : SourceTypeRegistry.PARKED));

        // The feed's index, its backend and naming chosen with the user.
        client.answeringWith(choosing(STROOM_FLAT));
        json("get_field_conventions", args());
        client.answeringWith(McpTestClient::agree);
        final Map<String, Object> firstPlan = map(drafted(index, firstEvents), "plan");
        agreed("create_index_doc", args("build", first, "backend", "lucene", "name", index, "time_field",
                firstPlan.get("time_field")));
        json("save_xslt", args("build", first, "name", index + "-XSLT", "index_plan", firstPlan));
        promote(first, Map.of("Pipeline", folder, "XSLT", folder, "Index", folder, "Documentation", folder));
        assertThat(folderOf(firstPipeline)).startsWith("System/" + folder);

        // --- The second build: another group with its own pipeline; the first group changed in one walk. ---
        final String second = "walk-" + family.name() + "-2";
        json("start_build", args("build", second, "feeds", List.of(feed)));
        json("survey_feed", args("feed", feed, "build", second));
        final Map<String, Object> shownAgain = json("record_source_types", args("build", second, "feed", feed));
        final String secondType = rowName(shownAgain, family.secondGroup());
        client.answeringWith(answering(List.of(secondType), family.secondOwnFeed()
                ? secondName
                : null));
        json("record_source_types", args("build", second, "feed", feed, "reviewed", shownAgain.get("review_id"),
                "rows", shownAgain.get("rows")));
        classified(second, List.of(stream), family.secondRules());
        final int formsBefore = client.forms().size();
        final Saved secondSaved = group(second, feed, secondName, secondType, family.secondRules(), stream);
        assertThat(formsSince(formsBefore)).anySatisfy(form -> assertThat(form.message())
                .contains("'" + secondName + "'", PLACEMENT));
        final String outputFeed = feed + "-SECOND-EVENTS";
        if (family.secondOwnFeed()) {
            assertThat(secondSaved.group()).containsEntry("output_feed", "");
            agreed("create_feed", args("build", second, "name", outputFeed, "stream_type", "Events", "group",
                    secondName));
        } else {
            assertThat(secondSaved.group()).doesNotContainKey("output_feed");
        }
        final String secondPipeline = uuidOf(agreed("create_pipeline", args("build", second, "group", secondName,
                "template_uuid", template)));
        if (family.changesFirst()) {
            // The promoted first group changed: the user chooses to change its pipeline, through working copies.
            final int changeBefore = client.forms().size();
            final Map<String, Object> changed = agreed("build_translation_xslt", args("build", second, "feeds",
                    List.of(feed), "group", firstName, "source_types", List.of(firstType), "schema_version",
                    VERSION, "stream_ids", List.of(stream), "mapping", TestE2eSourceTypeGroups.jsonValue(
                            jsonOf(firstSaved.mapping()).replace("\"LogonFailed\"", "\"LogonFailedV2\""))));
            assertThat(changed).as("changed: %s", changed.get("problems")).containsEntry("ok", true);
            assertThat(formsSince(changeBefore)).anySatisfy(form -> assertThat(form.message())
                    .contains("'" + firstName + "'", PIPELINE_FORM, firstPipelineName));
            final String copy = uuidOf(map(map(changed, "group"), "xslt"));
            assertThat(copy).isNotEqualTo(uuidOf(map(firstSaved.group(), "xslt")));
            assertThat(readXslt(copy).getData()).contains("LogonFailedV2");
        }
        // Processing in the build runs the build's pipelines: the first group's production pipeline through a working
        // copy of it (the group's working copy, where the build changed it).
        final String firstRunning = uuidOf(agreed("copy_pipeline", args("build", second, "source_uuid", firstPipeline,
                "new_name", firstPipelineName, "working_copy", true)));

        // Data arrives with a value no registry row matches: neither pipeline warns of it, both count it passed
        // over.
        final String later = withNewSourceType(TestE2eSourceTypeGroups.lines(family.laterFile(), LATER_RECORDS),
                family.field(), family.newValue());
        final long laterStream = send(feed, later);
        final Map<String, Long> passed = new LinkedHashMap<>();
        passed.put(firstRunning, RECORDS - records(text, family, firstType, written)
                                 + LATER_RECORDS - records(later, family, firstType, written));
        passed.put(secondPipeline, RECORDS - records(text, family, secondType, written)
                                   + LATER_RECORDS - records(later, family, secondType, written));
        for (final String pipeline : passed.keySet()) {
            final Map<String, Object> stepped = stepSample(pipeline, List.of(laterStream));
            assertThat(stepped).as("stepped clean: %s", stepped).containsEntry("verdict", "clean");
            final Map<String, Object> done = processed(pipeline, List.of(stream, laterStream));
            for (final Map<String, Object> each : list(done, "streams")) {
                final String logged = streamText(ids(each, "errors").getFirst());
                assertThat(logged.lines().filter(line -> line.contains(family.newValue()))).as(pipeline).isEmpty();
                assertThat(logged.lines().filter(line -> line.contains("WARN") && line.contains("source type")))
                        .isEmpty();
                final String input = String.valueOf(each.get("input"));
                final String raw = input.equals(String.valueOf(laterStream))
                        ? later
                        : text;
                final String type = pipeline.equals(secondPipeline)
                        ? secondType
                        : firstType;
                assertThat(TestE2eSourceTypeGroups.passedOver(logged)).as("passed over in %s", input)
                        .isEqualTo(raw.lines().count() - records(raw, family, type, written));
            }
        }
        if (family.secondOwnFeed()) {
            assertThat(streamIdsOf(outputFeed)).isNotEmpty();
        }
        if (family.changesFirst()) {
            final List<String> records = later.lines().toList();
            final int failedAt = TestE2eSourceTypeGroups.indexOf(records, "\"UserLoginFailed\"", null);
            assertThat(translationOutput(firstRunning, laterStream, failedAt)).contains("LogonFailedV2");
        }

        // Coverage of each pipeline: its group's own, the other's source type left to it, nothing new.
        final Map<String, Object> secondReview = json("review_coverage", args("pipeline_uuid", secondPipeline,
                "follow_on", false));
        dump("review_coverage " + second, secondReview);
        final Map<String, Object> secondTypes = map(secondReview, "source_types");
        assertThat(secondTypes).containsEntry("group", secondName).doesNotContainKeys("new_source_types",
                "unseen_source_types");
        assertThat(passedOver(secondTypes)).isEqualTo(passed.get(secondPipeline));
        noKindMissed(secondReview, secondType);
        final Map<String, Object> firstAgain = json("review_coverage", args("pipeline_uuid", firstRunning,
                "follow_on", false));
        assertThat(map(firstAgain, "source_types")).containsEntry("group", firstName);
        assertThat(passedOver(map(firstAgain, "source_types"))).isEqualTo(passed.get(firstRunning));
        noKindMissed(firstAgain, firstType);

        // The index: one per output feed, inherited where the group's Events share the feed's.
        final Map<String, Object> inherited = json("get_field_conventions", args());
        assertThat(inherited).containsEntry("status", "chosen");
        final List<Long> secondEvents = eventsWritten(secondPipeline, List.of(stream));
        if (family.secondOwnFeed()) {
            final String ownIndex = index + "-second";
            final Map<String, Object> ownDraft = drafted(ownIndex, secondEvents);
            assertThat(map(ownDraft, "feed_index")).containsEntry("output_feed", outputFeed);
            assertThat((List<String>) map(ownDraft, "feed_index").get("groups")).containsExactly(secondName);
            json("save_xslt", args("build", second, "name", ownIndex + "-XSLT", "index_plan", map(ownDraft,
                    "plan")));
        } else {
            final Map<String, Object> secondDraft = drafted(index, secondEvents);
            assertThat((List<String>) map(secondDraft, "feed_index").get("groups")).containsExactlyInAnyOrder(
                    firstName, secondName);
            json("save_xslt", args("build", second, "name", index + "-XSLT-2", "index_plan", map(secondDraft,
                    "plan")));
        }
        promote(second, Map.of("Pipeline", folder, "XSLT", folder, "Documentation", folder));

        // Beside the feed: both groups translated, each by its own pipeline; the value no row matches not added.
        final Map<String, Object> registry = SourceTypeRegistry.read(documentation(documentsIn("System/" + folder,
                "Documentation", feed + SourceTypeRegistry.SUFFIX).getFirst().getUuid()));
        final Map<String, Map<String, Object>> rows = rows(registry);
        assertThat(rows.get(firstType)).containsEntry("status", SourceTypeRegistry.TRANSLATED)
                .containsEntry("group", firstName);
        assertThat(map(rows.get(firstType), "pipeline")).containsEntry("uuid", firstPipeline)
                .containsEntry("name", firstPipelineName);
        assertThat(rows.get(secondType)).containsEntry("status", SourceTypeRegistry.TRANSLATED)
                .containsEntry("group", secondName);
        assertThat(map(rows.get(secondType), "pipeline")).containsEntry("uuid", secondPipeline);
        assertThat(rows).doesNotContainKey(family.newValue());
        assertThat(folderOf(secondPipeline)).isEqualTo(folderOf(firstPipeline));
        if (family.secondOwnFeed()) {
            assertThat(rows.get(secondType)).containsEntry("output_feed", outputFeed);
        }
        assertThat(jsonOf(registry)).doesNotContain(firstRunning);
        if (family.changesFirst()) {
            // The working copy written back: production translates as changed.
            assertThat(readXslt(uuidOf(map(firstSaved.group(), "xslt"))).getData()).contains("LogonFailedV2");
        }
    }

    // --- the steps ---

    // build_translation_xslt group= from the scope's draft, its rules the agent's (the draft's reading of the input
    // and its common entries kept).
    private Saved group(final String build,
                        final String feed,
                        final String name,
                        final String sourceType,
                        final Map<String, Object> rules,
                        final long stream) {
        final Map<String, Object> draft = agreed("draft_translation_mapping", args("stream_ids", List.of(stream),
                "build", build));
        dump("draft_translation_mapping " + name, draft);
        assertThat(map(draft, "scope")).as("drafted through the scope").isNotEmpty();
        final Map<String, Object> mapping = withRules(map(draft, "mapping"), rules);
        final Map<String, Object> saved = agreed("build_translation_xslt", args("build", build, "feeds",
                List.of(feed), "group", name, "source_types", List.of(sourceType), "schema_version", VERSION,
                "stream_ids", List.of(stream), "mapping", mapping));
        dump("build_translation_xslt " + name, saved);
        assertThat(saved).as("group %s saved: %s", name, saved.get("problems")).containsEntry("ok", true);
        return new Saved(map(saved, "group"), mapping);
    }

    // A draft's mapping with the agent's rules: the draft's common entries kept, a time it couldn't choose chosen
    // (the first candidate, the collector's or wrapper's time), and what the source lacks added; its rules replace
    // the draft's; how it reads the message (extractions, held fields) adds to the draft's.
    private static Map<String, Object> withRules(final Map<String, Object> drafted, final Map<String, Object> rules) {
        final Map<String, Object> mapping = new LinkedHashMap<>(drafted);
        final Map<String, Object> changed = new LinkedHashMap<>(rules);
        final List<Map<String, Object>> common = new ArrayList<>();
        for (final Map<String, Object> entry : list(mapping, "common")) {
            final Map<String, Object> kept = new LinkedHashMap<>(entry);
            if (kept.remove("choose_from") instanceof final List<?> candidates) {
                kept.put("field", candidates.getFirst());
            }
            common.add(kept);
        }
        common.addAll((List<Map<String, Object>>) changed.getOrDefault(COMMON_ADDED, List.of()));
        changed.remove(COMMON_ADDED);
        mapping.put("common", common);
        changed.forEach((key, value) -> {
            if (!"events".equals(key) && value instanceof final List<?> added
                && mapping.get(key) instanceof final List<?> drafts) {
                final List<Object> both = new ArrayList<>(drafts);
                added.stream().filter(item -> !both.contains(item)).forEach(both::add);
                mapping.put(key, both);
            } else {
                mapping.put(key, value);
            }
        });
        return mapping;
    }

    // classify_kinds as the agent does it with the user: each kind the rules it will write name as what they make
    // of it (the action element and its Action), the rest Unknown.
    private void classified(final String build, final List<Long> streams, final Map<String, Object> rules) {
        final Map<String, Object> listed = json("classify_kinds", args("build", build, "stream_ids", streams));
        final List<Map<String, Object>> kinds = list(listed, "to_classify");
        if (kinds.isEmpty()) {
            return;
        }
        final List<Map<String, Object>> proposals = new ArrayList<>();
        for (final Map<String, Object> kind : kinds) {
            final Map<String, Object> proposal = new LinkedHashMap<>();
            proposal.put("field", kind.get("field"));
            proposal.put("value", kind.get("value"));
            proposal.put("description", String.valueOf(kind.get("value")));
            proposal.put("event_detail", "Unknown");
            proposal.put("from_user", true);
            for (final Map<String, Object> rule : list(rules, "events")) {
                final boolean forKind = list(rule, "when").stream()
                        .anyMatch(when -> kind.get("field").equals(when.get("field"))
                                          && kind.get("value").equals(String.valueOf(when.get("equals"))));
                if (forKind) {
                    for (final Map<String, Object> field : list(rule, "fields")) {
                        final String[] path = String.valueOf(field.get("path")).split("/");
                        if (path.length > 2 && "EventDetail".equals(path[0])) {
                            proposal.put("event_detail", path[1]);
                            if ("Action".equals(path[path.length - 1]) && field.get("value") != null) {
                                proposal.put("action", field.get("value"));
                            }
                        }
                    }
                }
            }
            proposals.add(proposal);
        }
        json("classify_kinds", args("build", build, "proposals", proposals));
    }

    // The Events of the streams, processed through a pipeline once it steps clean.
    private List<Long> events(final String pipeline, final List<Long> streams) {
        final Map<String, Object> stepped = stepSample(pipeline, streams);
        assertThat(stepped).as("stepped clean: %s", stepped).containsEntry("verdict", "clean");
        return eventsWritten(pipeline, streams, processed(pipeline, streams));
    }

    // The Events streams a pipeline wrote for these raw streams, as a processing round gave them.
    private List<Long> eventsWritten(final String pipeline, final List<Long> streams, final Map<String, Object> done) {
        final List<Long> events = new ArrayList<>();
        list(done, "streams").forEach(each -> events.addAll(ids(each, "events")));
        assertThat(events).as("%s wrote Events for %s", pipeline, streams).isNotEmpty();
        return events;
    }

    // The Events streams a pipeline has written for these raw streams, as wait_for_processing finds them.
    private List<Long> eventsWritten(final String pipeline, final List<Long> streams) {
        return eventsWritten(pipeline, streams, json("wait_for_processing", args("pipeline_uuid", pipeline,
                "stream_ids", streams, "timeout_seconds", 10)));
    }

    // Processed through the pipeline, with nothing to fix.
    private Map<String, Object> processed(final String pipeline, final List<Long> streams) {
        final Map<String, Object> done = process(pipeline, streams);
        assertThat(list(done, "problems")).as("%s", done).isEmpty();
        return done;
    }

    // draft_index_mapping for the Events, on Lucene with the flat convention.
    private Map<String, Object> drafted(final String index, final List<Long> events) {
        final Map<String, Object> drafted = agreed("draft_index_mapping", args("backend", "lucene", "index_name",
                index, "convention", "stroom-flat", "events_stream_ids", events));
        assertThat(drafted).as("drafted: %s", drafted).containsKey("plan");
        return drafted;
    }

    // promote_build: the layout shown first (needs_review), then promoted once the user agrees in the form.
    private void promote(final String build, final Map<String, Object> destinations) {
        final Map<String, Object> review = json("promote_build", args("build", build, "destinations",
                destinations));
        assertThat(review).as("the layout first: %s", review).containsEntry("status", "needs_review");
        final Map<String, Object> promoted = json("promote_build", args("build", build, "destinations",
                destinations, "reviewed", review.get("review_id")));
        dump("promote_build " + build, promoted);
        assertThat(promoted).as("promoted: %s", promoted).containsKey("promoted");
    }

    // The text stored in the feed as Stroom's receiver stores data posted to its datafeed.
    private long send(final String feed, final String data) {
        return securityContext.asProcessingUserResult(() -> {
            final MetaProperties properties = MetaProperties.builder()
                    .feedName(feed)
                    .typeName(StreamTypeNames.RAW_EVENTS)
                    .createMs(System.currentTimeMillis())
                    .build();
            try (final Target target = streamStore.openTarget(properties)) {
                TargetUtil.write(target, data);
                target.getAttributes().put(StandardHeaderArguments.FEED, feed);
                return target.getMeta().getId();
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    // A stream's text, as Stroom stores it.
    private String streamText(final long id) {
        return securityContext.asProcessingUserResult(() -> {
            try (final Source source = streamStore.openSource(id)) {
                return SourceUtil.readString(source);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    // The registry in the build, as a group's save named it.
    private Map<String, Object> registryInBuild(final Saved saved) {
        return SourceTypeRegistry.read(documentation(uuidOf(map(saved.group(), "registry"))));
    }

    // --- the review ---

    // The records the group's pipeline passed over, summed from its lines a document over the streams read.
    private static long passedOver(final Map<String, Object> sourceTypes) {
        return number(map(map(sourceTypes, "processing"), "passed_over").get("records"));
    }

    // No kind the rules missed: only the kinds the group keeps as Unknown (logged once a stream), all of its own
    // source type, none of another's.
    private static void noKindMissed(final Map<String, Object> review, final String sourceType) {
        assertThat(list(review, "missed")).allSatisfy(kind -> assertThat(kind)
                .containsEntry("source_type", sourceType)
                .containsKey("streams")
                .doesNotContainKey("events"));
        assertThat(review).doesNotContainKeys("passed_over", "other_groups");
    }

    // --- the data and the registry ---

    // Every fifth record given this value of the source type field, as a source the feed starts carrying.
    private static String withNewSourceType(final String text, final String field, final String value) {
        final List<String> lines = text.lines().toList();
        final StringBuilder out = new StringBuilder();
        final String key = "\"" + field + "\": \"";
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            final int at = line.indexOf(key);
            if (i % 5 == 0 && at >= 0) {
                final int start = at + key.length();
                line = line.substring(0, start) + value + line.substring(line.indexOf('"', start));
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    // The name of the row a needs_review reply proposes for a value or pattern: `name = value` or `name ~ pattern`.
    private static String rowName(final Map<String, Object> shown, final String valueOrPattern) {
        for (final Object row : (List<Object>) shown.get("rows")) {
            final String text = String.valueOf(row);
            final int at = Math.max(text.indexOf(" = "), text.indexOf(" ~ "));
            if (at > 0 && text.substring(at + 3).equals(valueOrPattern)) {
                return text.substring(0, at);
            }
        }
        throw new AssertionError("No row for " + valueOrPattern + ": " + shown.get("rows"));
    }

    private static Map<String, Map<String, Object>> rows(final Map<String, Object> registry) {
        final Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        list(registry, "rows").forEach(row -> rows.put((String) row.get("name"), row));
        return rows;
    }

    // The records of a text of a source type, as the registry's rows tell them (the first match winning).
    private static long records(final String text,
                                final Family family,
                                final String sourceType,
                                final Map<String, Object> registry) {
        final List<Map<String, Object>> rows = list(registry, "rows");
        final String key = "\"" + family.field() + "\": \"";
        return text.lines().filter(line -> {
            final int at = line.indexOf(key);
            if (at < 0) {
                return false;
            }
            final int start = at + key.length();
            final String value = line.substring(start, line.indexOf('"', start));
            return rows.stream()
                    .filter(row -> matches(String.valueOf(row.get("match")), value))
                    .findFirst()
                    .map(row -> sourceType.equals(row.get("name")))
                    .orElse(false);
        }).count();
    }

    // A row's match as record_source_types shows it (`= value` or `~ pattern`) against a value.
    private static boolean matches(final String match, final String value) {
        if (match.startsWith("~ ")) {
            return Pattern.compile(match.substring(2)).matcher(value).find();
        }
        return match.startsWith("= ") && match.substring(2).equals(value);
    }

    // A hand mapping's rules and its reading of the held message, without its input or common entries: what the
    // agent changes in a draft.
    private static Map<String, Object> rules(final Map<String, Object> mapping) {
        final Map<String, Object> out = new LinkedHashMap<>(mapping);
        out.keySet().removeAll(List.of("input", "json_layout", "common"));
        return out;
    }

    // A user who chooses these source types for the build's scope, gives the group named (if any) an output feed of
    // its own, and agrees to everything else (a new group's Events into the feed's, a change to a promoted group's
    // pipeline going to that pipeline).
    private static Function<ElicitFormRequest, ElicitResult> answering(final List<String> scope,
                                                                       final String ownGroup) {
        final Function<ElicitFormRequest, ElicitResult> scoping = TestE2eSourceTypeGroups.scopeOf(scope,
                "for a later build");
        return form -> {
            if (form.message().contains(TestE2eSourceTypeGroups.SCOPE_FORM)) {
                return scoping.apply(form);
            }
            final ElicitResult agreed = McpTestClient.agree(form);
            if (ownGroup == null || !form.message().contains("'" + ownGroup + "'")
                || !form.message().contains("is new")) {
                return agreed;
            }
            final Map<String, Object> content = new LinkedHashMap<>(agreed.content());
            final Map<?, ?> properties = (Map<?, ?>) form.requestedSchema().get("properties");
            properties.forEach((key, field) -> {
                final List<?> options = (List<?>) ((Map<?, ?>) field).get("enum");
                if (options != null) {
                    options.stream()
                            .map(String::valueOf)
                            .filter(option -> option.startsWith(OWN_FEED))
                            .findFirst()
                            .ifPresent(option -> content.put(String.valueOf(key), option));
                }
            });
            return new ElicitResult(ElicitResult.Action.ACCEPT, content, null);
        };
    }


    // --------------------------------------------------------------------------------


    /// A sample family.
    ///
    /// @param name          Short, for names.
    /// @param file          Its sample under `aggregated/`.
    /// @param laterFile     Its later sample, for data arriving after the first build.
    /// @param field         Its source type field.
    /// @param firstGroup    The value (or pattern) of the first build's source type.
    /// @param firstRules    What the agent changes in the first group's draft: its rules, and how it reads the
    ///                      message.
    /// @param secondGroup   The value (or pattern) of the second build's.
    /// @param secondRules   What it changes in the second's.
    /// @param newValue      A value no row matches, for data that arrives later.
    /// @param secondOwnFeed Whether the second group's Events go to an output feed of its own.
    /// @param changesFirst  Whether the second build changes the promoted first group too.
    private record Family(String name,
                          String file,
                          String laterFile,
                          String field,
                          String firstGroup,
                          Map<String, Object> firstRules,
                          String secondGroup,
                          Map<String, Object> secondRules,
                          String newValue,
                          boolean secondOwnFeed,
                          boolean changesFirst) {

    }

    /// A group saved.
    ///
    /// @param group   What build_translation_xslt said of the group.
    /// @param mapping The mapping sent.
    private record Saved(Map<String, Object> group, Map<String, Object> mapping) {

    }
}
