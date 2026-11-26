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

import stroom.ai.impl.tools.sample.SourceTypeFields;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.mcp.impl.McpTestClient;
import stroom.pipeline.shared.XsltDoc;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/// Drop rules on an aggregated feed of a logging framework's records, through the REAL tools on a real Stroom (seen
/// in external testing, 2026-11-26; records shaped like the tester's, [LoggerFeed]): each record's `event` is JSON
/// text naming its logger and appender, some longer than the JSONParser's `stringTruncateLength`.
///
/// - B: a catalogue drop rule (`drop: true`) on `event.appender_ref` is refused: for the cut held event (its long
///   records), and, with `appender_ref` recorded as the source type field, for that too; each said plainly.
/// - C: after that refusal, the catch-all is put to the user as ever, and declining it tells the agent its proposal
///   wasn't recorded and to correct it, rather than sending it back to the same proposal.
/// - D: a mapping's `drop_when` on `event.logger_name` (negated, and positive) and an events rule with `drop: true`,
///   built and processed: the long records are missed alike, as the build now warns.
@SuppressWarnings("unchecked")
class TestE2eLoggerDropRules extends AbstractE2eTest {

    private static final int RECORDS = 120;
    private static final String APPENDER = "event.appender_ref";
    private static final String LOGGER = "event.logger_name";
    private static final String EVENT_TYPE = "event.eventType";
    private static final List<String> DROPPED_APPENDERS = List.of("CATALINA", "APPLICATION");
    private static final List<String> DROPPED_LOGGERS = List.of(LoggerFeed.VALVE, LoggerFeed.COYOTE,
            LoggerFeed.DATABASE);
    private static final String CATCH_ALL_FORM = "uncatalogued";
    private static final String DROP_RULE = "Drop-OperationalLogs";
    private static final String WRAPPER = """
            [{"path": "EventTime/TimeCreated", "field": "@timestamp"},
             {"path": "EventSource/System/Name", "value": "Platform"},
             {"path": "EventSource/System/Environment", "value": "Production"},
             {"path": "EventSource/Generator", "value": "Platform"},
             {"path": "EventSource/Device/HostName", "field": "host"}]""";

    // B, appender_ref recorded as the build's source type field (the survey offers it): the drop rule's condition
    // on it is refused for that, and for the held event the string cut leaves unreadable on its long records.
    @Test
    void aDropRuleOnTheAppenderRecordedAsTheSourceTypeFieldIsRefusedSayingWhy() {
        final String build = "drop-src-1";
        final String feed = "E2E-DROP-SRC";
        createFeed("drop-src-raw", feed);
        final List<Long> raw = List.of(upload(feed, LoggerFeed.lines(RECORDS, 0, false)));
        client.answeringWith(TestE2eSourceTypeGroups.scopeOf(List.of("SECURITY", "ALERT", "CATALINA",
                "APPLICATION"), "Platform all"));
        json("start_build", args("build", build, "feeds", List.of(feed)));
        json("survey_feed", args("feed", feed, "build", build, "source_type_field", "event>appender_ref"));
        final Map<String, Object> shown = json("record_source_types", args("build", build, "feed", feed,
                "patterns", "exact"));
        json("record_source_types", args("build", build, "feed", feed, "patterns", "exact", "reviewed",
                shown.get("review_id"), "group", "Platform all"));
        json(CATALOGUE, args("build", build, "stream_ids", raw));
        assertThat(proposedText(build, raw, catalogue())).contains("\"recorded\":true");

        final String reply = proposedText(build, raw, List.of(dropRule()));
        assertThat(reply).contains("\"recorded\":false");
        assertThat(problemsOf(reply, DROP_RULE)).as("why the drop rule is refused").hasSize(2)
                .anySatisfy(problem -> assertThat(problem).contains("event.appender_ref",
                        "the build's source type field (event>appender_ref, recorded by the survey"))
                .anySatisfy(problem -> assertThat(problem).contains("'event' is longer than stringTruncateLength",
                        "on 20 record(s)", "never drops them"));
    }

    // B, a feed of one source: the drop rule's condition on the appender inside the held event, refused because
    // the string cut leaves the held event unreadable on its 20 long records (stack traces), saying so.
    @Test
    void aDropRuleOnTheAppenderOfAFeedOfOneSourceIsRefusedForTheCutHeldEvent() {
        final String build = "drop-one-1";
        final List<Long> raw = oneSource(build, "E2E-DROP-ONE");
        json(CATALOGUE, args("build", build, "stream_ids", raw));
        assertThat(proposedText(build, raw, catalogue())).contains("\"recorded\":true");

        final String reply = proposedText(build, raw, List.of(dropRule()));
        assertThat(reply).contains("\"recorded\":false");
        assertThat(problemsOf(reply, DROP_RULE)).as("why the drop rule is refused").singleElement().asString()
                .contains("'event' is longer than stringTruncateLength", "on 20 record(s)",
                        "appender_ref among them", "never drops them");
    }

    // C: the event types confirmed and the catch-all next, the drop rule for the rest refused (B). Put to the user
    // then, the catch-all's form showed the same records becoming Unknown, and its decline sent the agent back to
    // the same drop proposal.
    @Test
    void theCatchAllAfterARefusedDropRuleIsShownAndItsDeclineSaysTheProposalWasNotRecorded() {
        final String build = "drop-all-1";
        final List<Long> raw = oneSource(build, "E2E-DROP-ALL");
        json(CATALOGUE, args("build", build, "stream_ids", raw));
        proposedText(build, raw, catalogue());
        confirmedUntilTheCatchAll(build);
        assertThat(proposedText(build, raw, List.of(dropRule()))).contains("\"recorded\":false");

        final List<String> rounds = catchAllDeclined(build);
        assertThat(rounds).as("the catch-all not held over the refused proposal")
                .noneSatisfy(round -> assertThat(round).contains("isn't put to the user yet"));
        // The 20 long records whose held event processing's cut leaves unreadable are uncatalogued too, as
        // processing writes them to the catch-all (found in review, 2026-11-27: they were left out of the count).
        assertThat(rounds).anySatisfy(round -> assertThat(round).startsWith("FORM: ")
                .contains("Uncatalogued records: 60", "Held values that don't parse: 20 of the uncatalogued records"));
        assertThat(rounds.getLast()).as("what the agent is told of the decline")
                .contains("The user declined the catch-all", "Your latest proposals for them were not recorded",
                        "stringTruncateLength", "Correct those proposals", "rather than asking the user again");
    }

    // D: drop_when on the logger inside the held event (negated, and positive) and an events rule with drop: true,
    // each built and processed. Processing cuts the long records' held event, so none of its keys can be read
    // there: a positive condition never holds (the long Tomcat records are kept), a negated one always does (the
    // long logons are dropped). drop_when and drop: true alike (their order with json_fields is not the cause).
    @Test
    void dropRulesOnAKeyOfTheCutHeldEventMissItsLongRecordsAndTheBuildSaysSo() {
        final String build = "drop-map-1";
        final String feed = "E2E-DROP-MAP";
        createFeed(build, feed);
        final List<Long> raw = List.of(upload(feed, LoggerFeed.lines(RECORDS, 0)));
        json("survey_feed", args("feed", feed, "build", build, "stream_ids", raw, "source_type_field",
                SourceTypeFields.NOT_AGGREGATED));
        // The user's choice for the long values: cut, as the parser does by default.
        json("draft_translation_mapping", args("build", build, "stream_ids", raw, "long_values", "cut"));
        final Set<Integer> kept = records(i -> LoggerFeed.KEPT.contains(LoggerFeed.loggerOf(i)));
        final Set<Integer> longOperational = records(i -> !LoggerFeed.KEPT.contains(LoggerFeed.loggerOf(i))
                                                          && LoggerFeed.isLong(i));
        final Set<Integer> longLogons = records(i -> LoggerFeed.SECURITY.equals(LoggerFeed.loggerOf(i))
                                                     && LoggerFeed.isLong(i));
        final Set<Integer> positiveKept = new TreeSet<>(kept);
        positiveKept.addAll(longOperational);
        final Set<Integer> negatedKept = new TreeSet<>(kept);
        negatedKept.removeAll(longLogons);
        final Map<String, Object> negated = condition(LOGGER, "one_of", LoggerFeed.KEPT, true);
        final Map<String, Object> positive = condition(LOGGER, "one_of", DROPPED_LOGGERS, false);

        assertThat(processedKeeping(build, raw, "0", withDropWhen(positive), "drops them read whole, but in "
                                                                             + "processing doesn't"))
                .as("drop_when one_of the operational loggers").isEqualTo(positiveKept);
        assertThat(processedKeeping(build, raw, "1", withDropWhen(negated), "keeps them read whole, but in "
                                                                            + "processing drops them"))
                .as("drop_when not one_of the kept loggers").isEqualTo(negatedKept);
        assertThat(processedKeeping(build, raw, "2", withDropRule(positive), "drops them read whole, but in "
                                                                             + "processing doesn't"))
                .as("an events rule with drop: true, one_of the operational loggers").isEqualTo(positiveKept);
        assertThat(processedKeeping(build, raw, "3", withDropRule(negated), "keeps them read whole, but in "
                                                                            + "processing drops them"))
                .as("an events rule with drop: true, not one_of the kept loggers").isEqualTo(negatedKept);
    }

    // The mapping built (its sample check warning of the cut held event as said), its pipeline stepped and
    // processed: the records its Events kept.
    private Set<Integer> processedKeeping(final String build,
                                          final List<Long> raw,
                                          final String suffix,
                                          final Map<String, Object> mapping,
                                          final String warned) {
        final String name = "E2E-DROP-MAP-" + suffix;
        final CallToolResult built = buildCall(build, raw, name, mapping);
        final String builtText = McpTestClient.text(built);
        assertThat(built.isError()).as("built: %s", builtText).isNotEqualTo(Boolean.TRUE);
        final Map<String, Object> result = (Map<String, Object>) ToolJson.loads(builtText);
        assertThat(strings(result, "warnings")).as("the build's warnings").anySatisfy(warning -> assertThat(warning)
                .contains("'event' is longer than stringTruncateLength", warned));
        final String xslt = (String) map(result, "saved").get("uuid");
        final String pipeline = uuidOf(agreed("create_pipeline", args("build", build, "name", name,
                "template_uuid", templateUuid("translation", "Event Data (JSON)"), "set_properties",
                List.of(docProperty("translationFilter", "xslt", xslt, XsltDoc.TYPE)))));
        assertThat(stepSample(pipeline, raw)).containsEntry("verdict", "clean");
        final List<Long> events = ids(list(process(pipeline, raw), "streams").getFirst(), "events");
        return keptRecords(events.isEmpty()
                ? null
                : storedText(events.getFirst()));
    }

    // A build of one source surveyed, its stream of the platform's records (none of its logons long). Returns the
    // stream.
    private List<Long> oneSource(final String build, final String feed) {
        createFeed(build, feed);
        final List<Long> raw = List.of(upload(feed, LoggerFeed.lines(RECORDS, 0, false)));
        json("survey_feed", args("feed", feed, "build", build, "stream_ids", raw, "source_type_field",
                SourceTypeFields.NOT_AGGREGATED));
        return raw;
    }

    private static Set<Integer> records(final IntPredicate which) {
        return IntStream.range(0, RECORDS)
                .filter(which)
                .boxed()
                .collect(Collectors.toCollection(TreeSet::new));
    }

    // The tester's catalogue: the security logger's logons, logoffs and password changes, and the alerts.
    private static List<Map<String, Object>> catalogue() {
        final List<Map<String, Object>> out = new ArrayList<>();
        out.add(withKeys(eventType("Authenticate-Logon", "Authenticate", "high", when(LOGGER, "equals",
                LoggerFeed.SECURITY, EVENT_TYPE, "equals", "Login")), "action", "Logon", "actor", "event.userid"));
        out.add(withKeys(eventType("Authenticate-Logoff", "Authenticate", "high", when(LOGGER, "equals",
                LoggerFeed.SECURITY, EVENT_TYPE, "equals", "Logout")), "action", "Logoff", "actor", "event.userid"));
        out.add(withKeys(eventType("Authenticate-PasswordChange", "Authenticate", "high", when(LOGGER, "equals",
                        LoggerFeed.SECURITY, EVENT_TYPE, "equals", "Password change")), "action", "ChangePassword",
                "actor", "event.userid"));
        out.add(processPlaced(eventType("Process-Alert", "Process", "high", when(LOGGER, "equals",
                LoggerFeed.ALERTS))));
        return out;
    }

    // The user's drop rule for the operational appenders, as the agent proposed it.
    private static Map<String, Object> dropRule() {
        return args("op", "add", "type_id", DROP_RULE, "title", "Operational logs",
                "description", "Tomcat and application operational records, dropped as the user said",
                "drop", true, "basis", "user_confirmed", "confidence", "high", "event_detail", "Process",
                "recognised_by", List.of(args("when", List.of(args("field", APPENDER, "one_of",
                        DROPPED_APPENDERS)), "names", List.of())));
    }

    // What the catalogue says of the proposals, whatever it says.
    private String proposedText(final String build, final List<Long> raw, final List<Map<String, Object>> proposals) {
        return McpTestClient.text(client.call(CATALOGUE, args("build", build, "stream_ids", raw, "proposals",
                proposals)));
    }

    // Every set of the catalogue confirmed in its form until the catch-all is next (its table shown, its form
    // not yet).
    private void confirmedUntilTheCatchAll(final String build) {
        client.answeringWith(McpTestClient::agree);
        for (int round = 0; round < 8; round++) {
            final String text = McpTestClient.text(client.call(CATALOGUE, args("build", build, "confirm", true)));
            if (text.contains("**Uncatalogued records**")) {
                return;
            }
        }
        throw new AssertionError("The catch-all of " + build + " never came next");
    }

    // The catch-all put to the user (confirm=true) until its form is shown, which the user declines: each round's
    // answer, the form's message among them, the last what the agent is told of the decline.
    private List<String> catchAllDeclined(final String build) {
        final List<String> said = new ArrayList<>();
        client.answeringWith(form -> {
            if (form.message().contains(CATCH_ALL_FORM)) {
                said.add("FORM: " + form.message());
                return McpTestClient.decline(form);
            }
            return McpTestClient.agree(form);
        });
        for (int round = 0; round < 4; round++) {
            final String text = McpTestClient.text(client.call(CATALOGUE, args("build", build, "confirm", true)));
            said.add(text);
            if (text.contains("declined the catch-all")) {
                return said;
            }
        }
        throw new AssertionError("The catch-all of " + build + " was never declined: " + said);
    }

    // The problems a catalogue answer says of one event type (or drop rule).
    private static List<String> problemsOf(final String reply, final String typeId) {
        final Map<String, Object> result = (Map<String, Object>) ToolJson.loads(reply);
        return list(checkOf(result), "problems").stream()
                .filter(problem -> typeId.equals(problem.get("type_id")))
                .map(problem -> String.valueOf(problem.get("problem")))
                .toList();
    }

    // The mapping with a drop_when of the condition.
    private static Map<String, Object> withDropWhen(final Map<String, Object> condition) {
        final Map<String, Object> mapping = mapping();
        mapping.put("drop_when", List.of(args("reason", "operational loggers, as the user said", "when",
                List.of(condition))));
        return mapping;
    }

    // The mapping with an events rule dropping the condition's records, before the catch-all.
    private static Map<String, Object> withDropRule(final Map<String, Object> condition) {
        final Map<String, Object> mapping = mapping();
        final List<Map<String, Object>> events = new ArrayList<>();
        events.add(args("name", "operational", "drop", true, "when", List.of(condition)));
        events.addAll((List<Map<String, Object>>) mapping.get("events"));
        mapping.put("events", events);
        return mapping;
    }

    private static Map<String, Object> condition(final String field,
                                                 final String test,
                                                 final Object value,
                                                 final boolean not) {
        final Map<String, Object> condition = args("field", field, test, value);
        if (not) {
            condition.put("not", true);
        }
        return condition;
    }

    // An agent's mapping of the records: the held event read as JSON, every record a line kept as Unknown, its
    // logger and message as Data.
    private static Map<String, Object> mapping() {
        return new LinkedHashMap<>((Map<String, Object>) ToolJson.loads("""
                {"input": "json", "json_layout": "lines", "json_fields": ["event"],
                 "common": %s,
                 "events": [{"name": "line", "allow_unknown": "a test of which records are dropped",
                             "fields": [{"path": "EventDetail/TypeId", "value": "PlatformLine"},
                                        {"path": "EventDetail/Unknown/Data", "data_name": "logger",
                                         "field": "event.logger_name"}]}]}""".formatted(WRAPPER)));
    }

    private CallToolResult buildCall(final String build,
                                     final List<Long> raw,
                                     final String name,
                                     final Map<String, Object> mapping) {
        final Map<String, Object> call = args("build", build, "schema_version", VERSION, "stream_ids", raw,
                "name", name, "mapping", mapping);
        for (int round = 0; round < 4; round++) {
            final CallToolResult result = client.call("build_translation_xslt", call);
            final String text = McpTestClient.text(result);
            if (Boolean.TRUE.equals(result.isError()) || !text.contains("\"needs_review\"")) {
                return result;
            }
            call.put("reviewed", ((Map<String, Object>) ToolJson.loads(text)).get("review_id"));
        }
        return client.call("build_translation_xslt", call);
    }

    // The records an Events stream kept, by their number (their time says which).
    private static Set<Integer> keptRecords(final String events) {
        final Set<Integer> kept = new TreeSet<>();
        if (events == null) {
            return kept;
        }
        final Map<String, Integer> byTime = new LinkedHashMap<>();
        for (int i = 0; i < RECORDS; i++) {
            byTime.put("2026-11-26T09:%02d:%02d.%03dZ".formatted(i / 3600 % 60, i / 60 % 60, i % 60 * 7), i);
        }
        for (final Element event : children(parse(events).getDocumentElement(), "Event")) {
            final Integer i = byTime.get(textAt(event, "EventTime/TimeCreated"));
            kept.add(i == null
                    ? -1
                    : i);
        }
        return kept;
    }
}
