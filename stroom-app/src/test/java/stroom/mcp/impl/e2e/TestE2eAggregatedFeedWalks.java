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

import stroom.ai.impl.tools.sample.SourceTypeRegistry;
import stroom.data.shared.StreamTypeNames;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/// An aggregated feed walked through the REAL tools as an agent would, one walk per synthetic sample family under
/// `aggregated/` (made by aggregated-feeds-generator.py): PEGA's JSON wrapper around a message holding JSON, text or
/// SOAP; Azure's audit log, with no wrapper and the `Workload` field; and a collector's `LogFilePath`, matched by
/// patterns.
///
/// Each walk's first build surveys the feed, confirms its source type field, profiles the source types and writes the
/// registry with a scope of one source type; classifies, drafts and builds that source type group from the draft
/// (its pipeline asked in a form), makes the pipeline, steps it clean, processes it, reviews coverage (the other
/// source types passed over and counted by the dispatcher), plans the feed's index and promotes. Its second build
/// adds another group to the promoted feed through working copies; data arriving later holds a source type the
/// registry lacks, which review_coverage reads from the dispatcher's warnings and adds to the registry as new; the
/// index plan is the union of both groups'; promotion writes it all back beside the feed.
@Disabled("aggregated feeds rework R2/R3 (2026-11-13)")
@SuppressWarnings("unchecked")
class TestE2eAggregatedFeedWalks extends AbstractE2eTest {

    private static final String STROOM_FLAT = "Stroom flat convention";
    private static final int RECORDS = 1000;
    // In a family's rules: common entries the agent adds to the draft's.
    private static final String COMMON_ADDED = "common_added";

    @Inject
    private Store streamStore;
    @Inject
    private SecurityContext securityContext;

    @Test
    void pegasWrappedMessagesInTwoBuilds() {
        walk(new Family("pega", "pega/pega-1.json", "sourcetype", "PegaSecurityEvent",
                rules(TestE2eSourceTypeGroups.securityMapping()), "PegaIntegration",
                rules(TestE2eSourceTypeGroups.integrationMapping()), "PegaBatch"));
    }

    @Test
    void azuresAuditLogByWorkloadInTwoBuilds() {
        walk(new Family("azure", "azure-audit/azure-audit-1.json", "Workload", "AzureActiveDirectory",
                TestE2eSourceTypeGroups.jsonValue("""
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
                                       "field": "UserId"}]}]}"""),
                "ExchangeOnline", TestE2eSourceTypeGroups.jsonValue("""
                        {"events": [
                          {"name": "mail", "allow_unknown": "mailbox activity, for a later build",
                           "fields": [{"path": "EventDetail/TypeId", "field": "Operation"},
                                      {"path": "EventDetail/Unknown/Data", "data_name": "user",
                                       "field": "UserId"}]}]}"""),
                "PowerBI"));
    }

    @Test
    void collectorPathsByPatternInTwoBuilds() {
        walk(new Family("collector", "collector-paths/collector-paths-1.json", "LogFilePath", "/var/log/secure",
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
                           "fields": [{"path": "EventDetail/TypeId", "field": "action"},
                                      {"path": "EventDetail/Unknown/Data", "data_name": "user",
                                       "field": "user"}]}]}"""),
                "/srv/batch/job.log"));
    }

    // --- the walk ---

    private void walk(final Family family) {
        final String tag = family.name().toUpperCase(Locale.ROOT);
        final String feed = "E2E-WALK-" + tag;
        final String folder = "E2E Feeds/Walk" + tag;
        final String index = "e2e-walk-" + family.name();
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
        client.answeringWith(scopeOf(List.of(firstType)));
        final Map<String, Object> written = json("record_source_types", args("build", first, "feed", feed,
                "system_name", tag, "environment", "Prod", "reviewed", shown.get("review_id"), "rows",
                shown.get("rows")));
        assertThat(strings(written, "scope")).containsExactly(firstType);

        classified(first, List.of(stream), family.firstRules());
        final Map<String, Object> firstGroup = group(first, feed, tag + " first", firstType, family.firstRules(),
                stream);
        assertThat(formMessages()).anySatisfy(message -> assertThat(message)
                .contains("is new: choose the pipeline that handles it"));
        assertThat(map(firstGroup, "pipeline")).containsEntry("own", false);

        final String pipelineName = feed + "-Events";
        final Map<String, Object> made = agreed("create_pipeline", args("build", first, "name", pipelineName,
                "template_uuid", templateUuid("translation", "Event Data (JSON)")));
        assertThat(strings(made, "source_types")).containsExactly(firstType);
        final String pipeline = uuidOf(made);
        final List<Long> firstEvents = events(pipeline, List.of(stream));

        // Coverage: the dispatcher's counts read from the Error streams, every other source type passed over.
        final Map<String, Object> firstReview = json("review_coverage", args("pipeline_uuid", pipeline, "follow_on",
                false));
        dump("review_coverage " + first, firstReview);
        final Map<String, Object> passed = map(map(map(firstReview, "source_types"), "processing"), "passed_over");
        assertThat(passed).as("passed over: %s", passed).isNotEmpty().doesNotContainKey(firstType);
        passed.values().forEach(entry -> assertThat((Map<String, Object>) entry)
                .containsEntry("status", SourceTypeRegistry.PARKED));
        assertThat(passed.values().stream().mapToLong(entry -> number(((Map<String, Object>) entry).get("records")))
                .sum()).isEqualTo(RECORDS - records(text, family, family.firstGroup()));
        assertThat(String.valueOf(firstReview.get("tell_user"))).contains("the dispatcher passed over");
        // The kinds the group's log lines name are its own records' (each record's number read, passed over or
        // not), found from its own lines, not sampled from the Events.
        assertThat(firstReview).doesNotContainKey("passed_over");
        assertThat(list(firstReview, "missed")).allSatisfy(kind -> assertThat(kind)
                .containsEntry("source_type", firstType)
                .doesNotContainKey("events"));

        // The feed's index, its backend and naming chosen with the user.
        client.answeringWith(choosing(STROOM_FLAT));
        json("get_field_conventions", args());
        client.answeringWith(McpTestClient::agree);
        final Map<String, Object> firstPlan = map(drafted(index, firstEvents), "plan");
        agreed("create_index_doc", args("build", first, "backend", "lucene", "name", index, "time_field",
                firstPlan.get("time_field")));
        json("save_xslt", args("build", first, "name", index + "-XSLT", "index_plan", firstPlan));
        promote(first, Map.of("Pipeline", folder, "XSLT", folder, "Index", folder, "Documentation", folder));

        // --- The second build: another group in the feed's promoted pipeline. ---
        final String second = "walk-" + family.name() + "-2";
        json("start_build", args("build", second, "feeds", List.of(feed)));
        json("survey_feed", args("feed", feed, "build", second));
        final Map<String, Object> shownAgain = json("record_source_types", args("build", second, "feed", feed));
        final String secondType = rowName(shownAgain, family.secondGroup());
        client.answeringWith(scopeOf(List.of(secondType)));
        json("record_source_types", args("build", second, "feed", feed, "reviewed", shownAgain.get("review_id"),
                "rows", shownAgain.get("rows")));
        classified(second, List.of(stream), family.secondRules());
        final int formsBefore = client.forms().size();
        group(second, feed, tag + " second", secondType, family.secondRules(), stream);
        // Asked again for the new group, the feed's pipeline in production now the default.
        assertThat(formsSince(formsBefore)).anySatisfy(form -> assertThat(form.message())
                .contains("'" + tag + " second'", "is new"));
        final String working = uuidOf(agreed("copy_pipeline", args("build", second, "source_uuid", pipeline,
                "new_name", pipelineName, "working_copy", true)));

        // Data arrives with a source type no registry row matches: passed over, and warned of.
        final String later = withNewSourceType(TestE2eSourceTypeGroups.lines(family.file(), 200), family);
        final long laterStream = send(feed, later);
        json("survey_feed", args("feed", feed, "build", second));
        final List<Long> secondEvents = events(working, List.of(stream, laterStream));
        final Map<String, Object> secondReview = json("review_coverage", args("pipeline_uuid", working,
                "follow_on", false));
        dump("review_coverage " + second, secondReview);
        final Map<String, Object> sourceTypes = map(secondReview, "source_types");
        assertThat(map(map(sourceTypes, "processing"), "new_source_types")).containsKey(family.newValue());
        assertThat(strings(sourceTypes, "new_source_types")).containsExactly(family.newValue());
        assertThat(String.valueOf(sourceTypes.get("registry"))).contains("added to the registry");

        // The index: inherited, its plan the union of both groups'.
        final Map<String, Object> inherited = json("get_field_conventions", args());
        assertThat(inherited).containsEntry("status", "chosen");
        final Map<String, Object> secondDraft = drafted(index, secondEvents);
        assertThat((List<String>) map(secondDraft, "feed_index").get("groups")).containsExactlyInAnyOrder(
                tag + " first", tag + " second");
        json("save_xslt", args("build", second, "name", index + "-XSLT-2", "index_plan", map(secondDraft,
                "plan")));
        promote(second, Map.of("XSLT", folder, "Documentation", folder));

        // Beside the feed: both groups translated, the new source type waiting for the user.
        final Map<String, Object> registry = SourceTypeRegistry.read(documentation(documentsIn("System/" + folder,
                "Documentation", feed + SourceTypeRegistry.SUFFIX).getFirst().getUuid()));
        final Map<String, Map<String, Object>> rows = rows(registry);
        assertThat(rows.get(firstType)).containsEntry("status", SourceTypeRegistry.TRANSLATED);
        assertThat(rows.get(secondType)).containsEntry("status", SourceTypeRegistry.TRANSLATED);
        assertThat(rows.get(family.newValue())).containsEntry("status", SourceTypeRegistry.NEW);
    }

    // --- the steps ---

    // build_translation_xslt group= from the scope's draft, its rules the agent's (the draft's reading of the input
    // and its common entries kept).
    private Map<String, Object> group(final String build,
                                      final String feed,
                                      final String name,
                                      final String sourceType,
                                      final Map<String, Object> rules,
                                      final long stream) {
        final Map<String, Object> draft = agreed("draft_translation_mapping", args("stream_ids", List.of(stream),
                "build", build));
        dump("draft_translation_mapping " + name, draft);
        assertThat(map(draft, "scope")).as("drafted through the scope").isNotEmpty();
        final Map<String, Object> mapping = new LinkedHashMap<>(map(draft, "mapping"));
        final Map<String, Object> changed = new LinkedHashMap<>(rules);
        // The draft's common entries kept, a time it couldn't choose chosen (the first candidate, the collector's
        // or wrapper's time), and what the source lacks added.
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
        // Its rules replace the draft's; how it reads the message (extractions, held fields) adds to the draft's.
        changed.forEach((key, value) -> {
            if (!"events".equals(key) && value instanceof final List<?> added
                && mapping.get(key) instanceof final List<?> drafted) {
                final List<Object> both = new ArrayList<>(drafted);
                added.stream().filter(item -> !both.contains(item)).forEach(both::add);
                mapping.put(key, both);
            } else {
                mapping.put(key, value);
            }
        });
        final Map<String, Object> saved = agreed("build_translation_xslt", args("build", build, "feeds",
                List.of(feed), "group", name, "source_types", List.of(sourceType), "schema_version", VERSION,
                "stream_ids", List.of(stream), "mapping", mapping));
        dump("build_translation_xslt " + name, saved);
        assertThat(saved).as("group %s saved: %s", name, saved.get("problems")).containsEntry("ok", true);
        return map(saved, "group");
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
        final List<Long> events = new ArrayList<>();
        list(process(pipeline, streams), "streams").forEach(done -> events.addAll(ids(done, "events")));
        assertThat(events).isNotEmpty();
        return events;
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

    // --- the data and the registry ---

    // Every fifth record given the new source type's value, as a source the feed starts carrying.
    private static String withNewSourceType(final String text, final Family family) {
        final List<String> lines = text.lines().toList();
        final StringBuilder out = new StringBuilder();
        final String key = "\"" + family.field() + "\": \"";
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            final int at = line.indexOf(key);
            if (i % 5 == 0 && at >= 0) {
                final int start = at + key.length();
                line = line.substring(0, start) + family.newValue() + line.substring(line.indexOf('"', start));
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

    // The records of a text whose source type field holds this value.
    private static long records(final String text, final Family family, final String value) {
        final String held = "\"" + family.field() + "\": \"" + value + "\"";
        return text.lines().filter(line -> line.contains(held)).count();
    }

    // A hand mapping's rules and its reading of the held message, without its input or common entries: what the
    // agent changes in a draft.
    private static Map<String, Object> rules(final Map<String, Object> mapping) {
        final Map<String, Object> out = new LinkedHashMap<>(mapping);
        out.keySet().removeAll(List.of("input", "json_layout", "common"));
        return out;
    }

    // A user who chooses these source types for the build's scope and agrees to everything else.
    private static Function<ElicitFormRequest, ElicitResult> scopeOf(final List<String> chosen) {
        return TestE2eSourceTypeGroups.scopeOf(chosen, "for a later build");
    }


    // --------------------------------------------------------------------------------


    /// A sample family.
    ///
    /// @param name        Short, for names.
    /// @param file        Its sample under `aggregated/`.
    /// @param field       Its source type field.
    /// @param firstGroup  The value (or pattern) of the first build's source type.
    /// @param firstRules  What the agent changes in the first group's draft: its rules, and how it reads the message.
    /// @param secondGroup The value (or pattern) of the second build's.
    /// @param secondRules What it changes in the second's.
    /// @param newValue    A value no row matches, for data that arrives later.
    private record Family(String name,
                          String file,
                          String field,
                          String firstGroup,
                          Map<String, Object> firstRules,
                          String secondGroup,
                          Map<String, Object> secondRules,
                          String newValue) {

    }
}
