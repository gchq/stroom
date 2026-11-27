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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.catalogue.Catalogue;
import stroom.ai.impl.tools.draft.LocalCheck;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.impl.tools.validation.XPathChecks;
import stroom.mcp.impl.McpGoldenFiles;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/// The mapping checked against the sample before anything is generated or stepped: the mapping-level XPath checks,
/// their speed, and the check of each evaluation case's reference mapping.
class TestMappingSampleCheck {

    private static final List<String> KEYS = List.of("logid", "type", "action", "srcip", "dstip", "user", "msg");
    private static final List<String> QUOTED = List.of("logid", "type", "action", "user", "msg");
    private static final String SAMPLE = IntStream.range(0, 400)
            .mapToObj(i -> ToolJson.dumps(line(i, "")))
            .collect(Collectors.joining("\n"));
    // A message's key/value pairs read by extractions: a domain-qualified user and a stand-in for none among them.
    private static final String REUSE_MAPPING = """
            {"input": "json", "json_layout": "lines",
             "extract": [{"field": "msg", "regex": "user=(\\\\S+)", "names": ["user"]},
                         {"field": "msg", "regex": "act=(\\\\S+)", "names": ["act"]}],
             "common": [{"path": "EventTime/TimeCreated", "field": "time"},
                        {"path": "EventSource/User/Id", "field": "user"}],
             "events": [{"name": "logon", "when": [{"field": "act", "equals": "login"},
                                                   {"field": "user", "present": true}],
                         "fields": [{"path": "EventDetail/TypeId", "value": "Authenticate-Logon"},
                                    {"path": "EventDetail/Authenticate/Action", "value": "Logon"}]},
                        {"name": "logoff", "when": [{"field": "act", "equals": "logout"}],
                         "fields": [{"path": "EventDetail/TypeId", "value": "Authenticate-Logoff"},
                                    {"path": "EventDetail/Authenticate/Action", "value": "Logoff"}]},
                        {"name": "rest", "allow_unknown": "the rest",
                         "fields": [{"path": "EventDetail/TypeId", "value": "Unknown"},
                                    {"path": "EventDetail/Unknown/Data", "data_name": "msg", "field": "msg"}]}]}""";

    private static final String REUSE_SAMPLE = String.join("\n",
            "{\"time\": \"2026-10-01T00:00:00.000Z\", \"msg\": \"user=corp\\\\alice act=login\"}",
            "{\"time\": \"2026-10-01T00:00:01.000Z\", \"msg\": \"user=- act=login\"}",
            "{\"time\": \"2026-10-01T00:00:02.000Z\", \"msg\": \"user=corp\\\\bob act=logout\"}",
            "{\"time\": \"2026-10-01T00:00:03.000Z\", \"msg\": \"user=- act=logout\"}") + "\n";

    @Test
    @SuppressWarnings("unchecked")
    void theCatalogueIsCheckedByTheRecordsTheRulesTake() {
        // Logons confirmed, logoffs confirmed but taken by the rule for the rest (Unknown), status checks from the
        // documentation (not confirmed) written as Unknown, USB devices dropped by the user yet translated.
        final String sample = "{\"time\": \"2026-10-01T00:00:00.000Z\", \"action\": \"login\"}\n"
                              + "{\"time\": \"2026-10-01T00:00:01.000Z\", \"action\": \"logout\"}\n"
                              + "{\"time\": \"2026-10-01T00:00:02.000Z\", \"action\": \"usb\"}\n";
        final Map<String, Object> json = JsonUtil.readValue("""
                {"input": "json", "json_layout": "lines",
                 "common": [{"path": "EventTime/TimeCreated", "field": "time"}],
                 "events": [{"name": "logon", "when": [{"field": "action", "equals": "login"}],
                             "fields": [{"path": "EventDetail/TypeId", "value": "Authenticate-Logon"},
                                        {"path": "EventDetail/Authenticate/Action", "value": "Logon"}]},
                            {"name": "rest", "allow_unknown": "the rest",
                             "fields": [{"path": "EventDetail/TypeId", "value": "Unknown"},
                                        {"path": "EventDetail/Unknown/Data", "data_name": "action",
                                         "field": "action"}]}]}""", Map.class);
        final Catalogue catalogue = Catalogue.fromJson(JsonUtil.readValue("""
                {"event_types": [
                  {"type_id": "Authenticate-Logon", "event_detail": "Authenticate", "action": "Logon",
                   "recognised_by": [{"when": [{"field": "action", "equals": "login"}]}], "status": "confirmed"},
                  {"type_id": "Authenticate-Logoff", "event_detail": "Authenticate", "action": "Logoff",
                   "recognised_by": [{"when": [{"field": "action", "equals": "logout"}]}], "status": "confirmed"},
                  {"type_id": "Alert-Usb", "event_detail": "Alert", "drop": true,
                   "recognised_by": [{"when": [{"field": "action", "equals": "usb"}]}], "status": "confirmed"}],
                 "catch_all": {"status": "confirmed"}}""", Map.class));

        final Map<String, Object> checked = MappingSampleCheck.check(TranslationMapping.fromJson(json),
                List.of(sample), null, TranslationWorld.SCHEMA, false, Clock.systemUTC(), catalogue, null);

        assertThat((List<ConfirmedEventTypes.Lost>) checked.get(MappingSampleCheck.CONFIRMED_LOST))
                .singleElement().satisfies(one -> {
                    assertThat(one.typeId()).isEqualTo("Authenticate-Logoff");
                    assertThat(one.writes()).isEqualTo("Unknown");
                });
        assertThat(strings(checked.get("warnings"))).anySatisfy(warning -> assertThat(warning)
                .startsWith("dropped event type Alert-Usb: 1 sample records are translated by rule 'rest'"));
        assertThat((List<String>) checked.get(MappingSampleCheck.CATALOGUE_CONTRADICTIONS)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aCheckAgainAfterTheUsersAnswersGivesWhatAWholeCheckGives() {
        // The domain split and the stand-ins left out, as the user answers the forms after the first check: the
        // check of the changed mapping that reuses the first's records, extracted values and verdicts says what a
        // whole check of it says.
        final Catalogue catalogue = reuseCatalogue();
        final TranslationMapping first = TranslationMapping.fromJson(JsonUtil.readValue(REUSE_MAPPING, Map.class));
        final Map<String, Object> firstCheck = reuseCheck(first, catalogue, null);
        final MappingSampleCheck.Earlier earlier = (MappingSampleCheck.Earlier) firstCheck.get(
                MappingSampleCheck.EARLIER);
        final List<LocalCheck.QualifiedIds> qualified = (List<LocalCheck.QualifiedIds>) firstCheck.get(
                MappingSampleCheck.DOMAIN_QUALIFIED);
        assertThat(qualified).isNotEmpty();
        assertThat((List<Object>) firstCheck.get(MappingSampleCheck.PLACEHOLDERS)).isNotEmpty();
        assertThat((List<Object>) firstCheck.get(MappingSampleCheck.CONFIRMED_LOST)).isEmpty();

        final TranslationMapping split = DomainSplits.split(first, qualified.getFirst(), TranslationWorld.SCHEMA)
                .mapping();
        final TranslationMapping leftOut = PlaceholderChoices.leaveOut(split, List.of("-"));
        for (final TranslationMapping changed : List.of(split, leftOut)) {
            final Map<String, Object> again = reuseCheck(changed, catalogue, earlier);
            final Map<String, Object> whole = reuseCheck(changed, catalogue, null);

            assertThat(((MappingSampleCheck.Earlier) again.remove(MappingSampleCheck.EARLIER)).found())
                    .isSameAs(earlier.found());
            whole.remove(MappingSampleCheck.EARLIER);
            assertThat(again).isEqualTo(whole);
        }
        // '-' left out, the logon rule's 'user present' fails for its record: the confirmed logon goes to the rule
        // for the rest, told again, not taken from the first check.
        assertThat((List<ConfirmedEventTypes.Lost>) reuseCheck(leftOut, catalogue, earlier).get(
                MappingSampleCheck.CONFIRMED_LOST)).singleElement()
                .satisfies(one -> assertThat(one.writes()).isEqualTo("Unknown"));
    }

    @Test
    void aCheckAgainOfAMappingReadingRecordsOtherwiseReadsThemAgain() {
        final Catalogue catalogue = reuseCatalogue();
        final MappingSampleCheck.Earlier earlier = (MappingSampleCheck.Earlier) reuseCheck(
                TranslationMapping.fromJson(JsonUtil.readValue(REUSE_MAPPING, Map.class)), catalogue, null)
                .get(MappingSampleCheck.EARLIER);
        final TranslationMapping otherExtract = TranslationMapping.fromJson(JsonUtil.readValue(
                REUSE_MAPPING.replace("act=(", "action=("), Map.class));

        final Map<String, Object> again = reuseCheck(otherExtract, catalogue, earlier);
        final Map<String, Object> whole = reuseCheck(otherExtract, catalogue, null);

        assertThat(((MappingSampleCheck.Earlier) again.remove(MappingSampleCheck.EARLIER)).found())
                .isNotSameAs(earlier.found());
        whole.remove(MappingSampleCheck.EARLIER);
        assertThat(again).isEqualTo(whole);
    }

    private static Map<String, Object> reuseCheck(final TranslationMapping mapping,
                                                  final Catalogue catalogue,
                                                  final MappingSampleCheck.Earlier earlier) {
        return MappingSampleCheck.check(mapping, List.of(REUSE_SAMPLE), null, TranslationWorld.SCHEMA, false,
                Clock.systemUTC(), catalogue, null, earlier);
    }

    @SuppressWarnings("unchecked")
    private static Catalogue reuseCatalogue() {
        return Catalogue.fromJson(JsonUtil.readValue("""
                {"event_types": [
                  {"type_id": "Authenticate-Logon", "event_detail": "Authenticate", "action": "Logon",
                   "recognised_by": [{"when": [{"field": "act", "equals": "login"}]}], "status": "confirmed"},
                  {"type_id": "Authenticate-Logoff", "event_detail": "Authenticate", "action": "Logoff",
                   "recognised_by": [{"when": [{"field": "act", "equals": "logout"}]}], "status": "confirmed"}],
                 "catch_all": {"status": "confirmed"}}""", Map.class));
    }

    @Test
    void eachEvaluationCasesReferenceMappingPassesTheChecksOffline() {
        // What build_translation_xslt checks before it saves, for each evaluation case's reference mapping (the
        // cases whose samples are under 60 KB, their mappings the cases' own, TestEvalReferenceCopies): every one
        // passes. With -DtestUpdateGoldenFiles=true, each case's problems and warnings are recorded as they are now.
        final List<Map<String, Object>> cases = cases();
        for (final Map<String, Object> c : cases) {
            final TranslationMapping mapping = TranslationMapping.parse(c.get("mapping"));
            @SuppressWarnings("unchecked") final List<String> texts = (List<String>) c.get("texts");
            @SuppressWarnings("unchecked") final Map<String, Object> spec = (Map<String, Object>) c.get("splitter");
            final SplitterSpec splitter = spec == null
                    ? null
                    : SplitterSpec.fromMap(spec);
            final List<String> problems = new ArrayList<>(XsltGenerator.generate(mapping, TranslationWorld.SCHEMA,
                    TranslationWorld.VERSION).problems());
            final Map<String, Object> checked = MappingSampleCheck.check(mapping, texts, splitter,
                    TranslationWorld.SCHEMA, false, Clock.systemUTC());
            problems.addAll(strings(checked.get("problems")));
            if (McpGoldenFiles.updating()) {
                c.put("problems", problems);
                c.put("warnings", strings(checked.get("warnings")));
            }
            assertThat(problems).as(String.valueOf(c.get("id"))).isEqualTo(c.get("problems"));
        }
        if (McpGoldenFiles.updating()) {
            McpGoldenFiles.assertResourceMatches("/stroom/mcp/impl/tools/offline_cases.json",
                    ToolJson.dumps(cases, 0, false));
        }
    }

    @Test
    void anEntryWithoutRepeatGivingSeveralValuesIsWarnedOf() {
        // Seen: TWITTER's mentions without repeat came out joined in one Data, and the agent wrote the XSLT by hand.
        final String sample = "<tweets><tweet><data name=\"@timestamp\" value=\"2022-01-15T14:52:23.000Z\"/>"
                              + "<data name=\"text\" value=\"@pataz1 @Lancegooden Facts much\"/></tweet></tweets>";
        final Map<String, Object> mentions = new LinkedHashMap<>(Map.of("path", "EventDetail/Create/Document/Data",
                "data_name", "mentions", "xpath", "for $m in tokenize(data[@name='text']/@value, '\\s+')"
                                                  + "[starts-with(., '@')] return substring($m, 2)"));
        final Map<String, Object> json = new LinkedHashMap<>(Map.of("input", "xml", "root", "tweets",
                "record", "tweet", "common", List.of(Map.of("path", "EventTime/TimeCreated",
                        "xpath", "data[@name='@timestamp']/@value"))));
        json.put("events", List.of(Map.of("name", "tweet", "fields", List.of(
                Map.of("path", "EventDetail/TypeId", "value", "Tweet"), mentions))));

        assertThat(strings(MappingSampleCheck.check(TranslationMapping.fromJson(json), List.of(sample), null,
                TranslationWorld.SCHEMA, false, Clock.systemUTC()).get("warnings")))
                .anySatisfy(warning -> assertThat(warning)
                        .contains("(used for [tweet] EventDetail/Create/Document/Data mentions) gives 2 values")
                        .contains("repeat: true writes one element per value"));

        mentions.put("repeat", true);
        assertThat(strings(MappingSampleCheck.check(TranslationMapping.fromJson(json), List.of(sample), null,
                TranslationWorld.SCHEMA, false, Clock.systemUTC()).get("warnings")))
                .noneSatisfy(warning -> assertThat(warning).contains("values in sample record"));
    }

    @Test
    void pairAndChangeEntriesAreTheLoopsTheyAreAndReadTheirLists() {
        // Seen in chat 188: a Data per pair was warned of as giving several values without repeat (the agent then
        // tried repeat: true three times, refused each time), and the lists the pair entries read were said to be
        // read by nothing; a change was said to select nothing.
        final String sample = String.join("\n",
                "{\"time\": \"2026-10-01T00:00:00.000Z\", \"op\": \"read\", \"event\": {\"OperationProperties\": "
                + "[{\"Name\": \"MailAccessType\", \"Value\": \"Sync\"}, {\"Name\": \"IsThrottled\", \"Value\": "
                + "\"False\"}]}}",
                "{\"time\": \"2026-10-01T00:00:01.000Z\", \"op\": \"set\", \"Changes\": [{\"Name\": "
                + "\"DisplayName\", \"OldValue\": \"Sam T\", \"NewValue\": \"Sam Taylor\"}, {\"Name\": \"Title\", "
                + "\"OldValue\": \"Lead\", \"NewValue\": \"Manager\"}]}") + "\n";
        final Map<String, Object> pair = Map.of("list", "event.OperationProperties", "name_key", "Name",
                "value_key", "Value");
        final Map<String, Object> change = Map.of("list", "Changes", "name_key", "Name", "old_key", "OldValue",
                "new_key", "NewValue");
        final Map<String, Object> named = new LinkedHashMap<>(change);
        named.put("name", "DisplayName");
        final Map<String, Object> json = new LinkedHashMap<>(Map.of("input", "json", "json_layout", "lines",
                "common", List.of(Map.of("path", "EventTime/TimeCreated", "field", "time"))));
        json.put("events", List.of(
                Map.of("name", "read", "when", List.of(Map.of("field", "op", "equals", "read")), "fields", List.of(
                        Map.of("path", "EventDetail/TypeId", "value", "Read"),
                        Map.of("path", "EventDetail/View/Data", "pair", pair))),
                Map.of("name", "set", "when", List.of(Map.of("field", "op", "equals", "set")), "fields", List.of(
                        Map.of("path", "EventDetail/TypeId", "value", "Set"),
                        Map.of("path", "EventDetail/Update/Before/Object/Name", "change", named),
                        Map.of("path", "EventDetail/Update/After/Object/Name", "change", named),
                        Map.of("path", "EventDetail/Update/Before/Object/Data", "change", change),
                        Map.of("path", "EventDetail/Update/After/Object/Data", "change", change)))));

        final List<String> warnings = strings(MappingSampleCheck.check(TranslationMapping.fromJson(json),
                List.of(sample), null, TranslationWorld.SCHEMA, false, Clock.systemUTC()).get("warnings"));

        assertThat(warnings)
                .noneSatisfy(warning -> assertThat(warning).contains("values in sample record"))
                // A change's xpath unwraps JSON text with a function only the generated XSLT declares.
                .noneSatisfy(warning -> assertThat(warning).contains("selects nothing"))
                .noneSatisfy(warning -> assertThat(warning).contains("nothing reads").satisfiesAnyOf(
                        text -> assertThat(text).contains("event.OperationProperties"),
                        text -> assertThat(text).contains("'Changes'")));
    }

    @Test
    void aRequiredElementEverySampleRecordFillsIsNoLongerSaidToBeMaybeLeftOut() {
        // Seen in chat 185: the generator's "required [...] are left out when their input fields are empty" kept
        // beside a sample check that found none empty; the agent asked the user and put 'Unknown' in each.
        final String sample = "<records><record><t>2022-01-15T14:52:23.000Z</t><cmd>run.exe</cmd><dir>c</dir>"
                              + "</record><record><t>2022-01-15T14:52:24.000Z</t><cmd>b.exe</cmd></record></records>";
        final Map<String, Object> json = new LinkedHashMap<>(Map.of("input", "xml", "root", "records",
                "record", "record", "common", List.of(
                        Map.of("path", "EventTime/TimeCreated", "field", "t"),
                        Map.of("path", "EventSource/System/Name", "value", "S"),
                        Map.of("path", "EventSource/System/Environment", "value", "Dev"),
                        Map.of("path", "EventSource/Generator", "value", "g"),
                        Map.of("path", "EventSource/Device/HostName", "value", "h"))));
        json.put("events", List.of(Map.of("name", "launch", "fields", List.of(
                Map.of("path", "EventDetail/TypeId", "value", "Launch"),
                Map.of("path", "EventDetail/Process/Action", "value", "Execute"),
                Map.of("path", "EventDetail/Process/Command", "field", "cmd")))));
        final TranslationMapping mapping = TranslationMapping.fromJson(json);
        final List<String> warnings = new ArrayList<>(XsltGenerator.generate(mapping, TranslationWorld.SCHEMA,
                "4.1.0").warnings());
        assertThat(warnings).anySatisfy(warning -> assertThat(warning)
                .startsWith("[launch] required [")
                .contains("'EventDetail/Process/Command'] are left out"));

        final Map<String, Object> checked = MappingSampleCheck.check(mapping, List.of(sample), null,
                TranslationWorld.SCHEMA, false, Clock.systemUTC());
        @SuppressWarnings("unchecked") final Map<String, Set<String>> filled =
                (Map<String, Set<String>>) checked.get(MappingSampleCheck.REQUIRED_FILLED);
        assertThat(filled.get("launch")).containsExactlyInAnyOrder("EventTime", "EventDetail/Process/Command");
        XsltGenerator.withoutRequiredFilled(warnings, filled);
        assertThat(warnings).noneSatisfy(warning -> assertThat(warning).contains("are left out when"));

        // Only what the sample fills is dropped: the rest of the list stays.
        final List<String> two = new ArrayList<>(List.of("[launch] required ['EventDetail/Process/Command', "
                                                         + "'EventDetail/Process/Outcome'] are left out when their "
                                                         + "input fields are empty, which makes the event invalid. "
                                                         + "Fine if those fields are always filled.", "other"));
        XsltGenerator.withoutRequiredFilled(two, filled);
        assertThat(two).containsExactly("[launch] required ['EventDetail/Process/Outcome'] are left out when their "
                                        + "input fields are empty, which makes the event invalid. Fine if those "
                                        + "fields are always filled.", "other");
    }

    @Test
    void aRequiredElementWhoseInputIsEmptyInSampleRecordsIsCounted() {
        // Seen: "Invalid content was found starting with element '{Data}'. One of '{Command}' is expected" in 87
        // records, read as Data put in the wrong place: Command was left out where its field was empty.
        final String sample = "<records><record><t>2022-01-15T14:52:23.000Z</t><cmd>run.exe</cmd><note>a</note>"
                              + "</record><record><t>2022-01-15T14:52:24.000Z</t><note>b</note></record></records>";
        final Map<String, Object> command = new LinkedHashMap<>(Map.of("path", "EventDetail/Process/Command",
                "field", "cmd"));
        final Map<String, Object> json = new LinkedHashMap<>(Map.of("input", "xml", "root", "records",
                "record", "record", "common", List.of(
                        Map.of("path", "EventTime/TimeCreated", "field", "t"),
                        Map.of("path", "EventSource/System/Name", "value", "S"),
                        Map.of("path", "EventSource/System/Environment", "value", "Dev"),
                        Map.of("path", "EventSource/Generator", "value", "g"),
                        Map.of("path", "EventSource/Device/HostName", "value", "h"))));
        json.put("events", List.of(Map.of("name", "launch", "fields", List.of(
                Map.of("path", "EventDetail/TypeId", "value", "Launch"),
                Map.of("path", "EventDetail/Process/Action", "value", "Execute"), command,
                Map.of("path", "EventDetail/Process/Data", "data_name", "note", "field", "note")))));

        assertThat(strings(MappingSampleCheck.check(TranslationMapping.fromJson(json), List.of(sample), null,
                TranslationWorld.SCHEMA, false, Clock.systemUTC()).get("warnings")))
                .contains("[launch] EventDetail/Process/Command is required by the schema, but its input ('cmd') is "
                          + "empty in 1 of the 2 sample records this rule takes: those events fail the schema, "
                          + "which reports the element written after it (often Data) as unexpected. Give the entry "
                          + "a default, or any_of with a field that is filled.");

        // A value the mapping's nil_values leaves out is empty too: seen, '-' counted as a value.
        final String dashed = sample.replace("<note>b</note>", "<cmd>-</cmd><note>b</note>");
        json.put("nil_values", List.of("-"));
        assertThat(strings(MappingSampleCheck.check(TranslationMapping.fromJson(json), List.of(dashed), null,
                TranslationWorld.SCHEMA, false, Clock.systemUTC()).get("warnings")))
                .anySatisfy(warning -> assertThat(warning).contains("Command is required by the schema, but its "
                                                                    + "input ('cmd') is empty in 1 of the 2"));
        json.remove("nil_values");

        // Given a default: nothing to say.
        command.put("default", "unknown");
        assertThat(strings(MappingSampleCheck.check(TranslationMapping.fromJson(json), List.of(sample), null,
                TranslationWorld.SCHEMA, false, Clock.systemUTC()).get("warnings")))
                .noneSatisfy(warning -> assertThat(warning).contains("is required by the schema, but its input"));
    }

    @Test
    void aTypeIdTheSameForDifferentKindsIsAProblemAndAJoinOfTheirFieldsIsNot() {
        // Seen: two runs that left TypeId the SecretServer category alone ('User' for logons and logoffs), the
        // warning one among many.
        final String sample = "<records><record><t>2022-01-15T14:52:23.000Z</t><event>User</event>"
                              + "<action>Login</action><who>pat</who></record><record><t>2022-01-15T14:52:24.000Z</t>"
                              + "<event>User</event><action>Logout</action><who>sam</who></record></records>";
        final Map<String, Object> typeId = new LinkedHashMap<>(Map.of("path", "EventDetail/TypeId", "field", "event"));
        final Map<String, Object> json = new LinkedHashMap<>(Map.of("input", "xml", "root", "records",
                "record", "record", "common", List.of(
                        Map.of("path", "EventTime/TimeCreated", "field", "t"),
                        Map.of("path", "EventSource/System/Name", "value", "S"),
                        Map.of("path", "EventSource/System/Environment", "value", "Dev"),
                        Map.of("path", "EventSource/Generator", "value", "g"),
                        Map.of("path", "EventSource/Device/HostName", "value", "h"),
                        Map.of("path", "EventSource/User/Id", "field", "who"), typeId)));
        json.put("events", List.of(
                Map.of("name", "logon", "when", List.of(Map.of("field", "action", "equals", "Login")),
                        "fields", List.of(Map.of("path", "EventDetail/Authenticate/Action", "value", "Logon"),
                                Map.of("path", "EventDetail/Authenticate/User/Id", "field", "who"))),
                Map.of("name", "logoff", "when", List.of(Map.of("field", "action", "equals", "Logout")),
                        "fields", List.of(Map.of("path", "EventDetail/Authenticate/Action", "value", "Logoff"),
                                Map.of("path", "EventDetail/Authenticate/User/Id", "field", "who")))));

        assertThat(strings(MappingSampleCheck.check(TranslationMapping.fromJson(json), List.of(sample), null,
                TranslationWorld.SCHEMA, false, Clock.systemUTC()).get("problems")))
                .singleElement().asString()
                .startsWith("EventDetail/TypeId reads 'event', which in the sample is the same for events of "
                            + "different kinds ('User' for rules logoff, logon)")
                .contains("the format the standing instructions (AGENTS docs) set for TypeIds")
                .contains("\"join\": [\"event\", ");

        typeId.remove("field");
        typeId.put("join", List.of("event", "action"));
        assertThat(strings(MappingSampleCheck.check(TranslationMapping.fromJson(json), List.of(sample), null,
                TranslationWorld.SCHEMA, false, Clock.systemUTC()).get("problems"))).isEmpty();
    }

    @Test
    void dataNoneOfARulesRecordsHoldIsWarnedOf() {
        // Seen: a SecretServer launch rule's rpc_account, target and duration_ms, in none of its records: listed by
        // the documentation as "(not in the sample)".
        final String sample = "<records><record><t>2022-01-15T14:52:23.000Z</t><action>Login</action><who>pat</who>"
                              + "<node>ss-web-02</node></record><record><t>2022-01-15T14:52:24.000Z</t>"
                              + "<action>Login</action><who>sam</who></record></records>";
        final Map<String, Object> json = new LinkedHashMap<>(Map.of("input", "xml", "root", "records",
                "record", "record", "common", List.of(
                        Map.of("path", "EventTime/TimeCreated", "field", "t"),
                        Map.of("path", "EventSource/System/Name", "value", "S"),
                        Map.of("path", "EventSource/System/Environment", "value", "Dev"),
                        Map.of("path", "EventSource/Generator", "value", "g"),
                        Map.of("path", "EventSource/Device/HostName", "value", "h"),
                        Map.of("path", "EventSource/User/Id", "field", "who"),
                        Map.of("path", "EventDetail/TypeId", "value", "Logon"))));
        json.put("events", List.of(Map.of("name", "logon", "when", List.of(Map.of("field", "action",
                "equals", "Login")), "fields", List.of(
                Map.of("path", "EventDetail/Authenticate/Action", "value", "Logon"),
                Map.of("path", "EventDetail/Authenticate/User/Id", "field", "who"),
                Map.of("path", "EventDetail/Authenticate/Data", "data_name", "server_node", "field", "node"),
                Map.of("path", "EventDetail/Authenticate/Data", "data_name", "rpc_account", "field", "rpc"),
                Map.of("path", "EventDetail/Authenticate/Data", "data_name", "target", "field", "target")))));

        assertThat(strings(MappingSampleCheck.check(TranslationMapping.fromJson(json), List.of(sample), null,
                TranslationWorld.SCHEMA, false, Clock.systemUTC()).get("warnings")))
                .contains("[logon] Data 'rpc_account', 'target' read fields none of the 2 sample records this rule "
                          + "takes holds: remove them, unless other records will have them.")
                .noneMatch(warning -> warning.contains("'server_node'"));
    }

    @Test
    void theCheckSaysWhichExtractionsMatchNoneAndWhichSome() {
        // The user key is missing from every seventh record: 342 of the 400 match.
        final TranslationMapping m = kvMapping(List.of(extraction("(?:^|[\\s,;])absent=(\\S*)", "absent"),
                extraction("\\w+=(\\w+)", "first")));

        final XPathChecks.Findings found = checked(m, Instant.now().plusSeconds(60));

        assertThat(found.problems()).singleElement().asString()
                .startsWith("extract[7] (body): the regex matches none of the 400");
        assertThat(found.warnings()).singleElement().asString()
                .startsWith("extract[5] (body): the regex matches 342 of the 400");
    }

    @Test
    void entriesPastTheDeadlineAreNamedNotChecked() {
        final TranslationMapping m = kvMapping(List.of(extraction("(?:^|[\\s,;])absent=(\\S*)", "absent")));

        final XPathChecks.Findings found = checked(m, Instant.EPOCH);

        assertThat(found.problems()).isEmpty();
        assertThat(found.warnings()).containsExactly(IntStream.range(0, 8)
                .mapToObj(n -> "extract[" + n + "] (body)")
                .collect(Collectors.joining(", "))
                                                     + " not checked against the sample: the check's time ran out. "
                                                     + "step_sample shows what they give.");
    }

    @Test
    void aLargeKeyValueSampleIsCheckedInSeconds() {
        final String extra = IntStream.range(0, 50).mapToObj(j -> "k" + j + "=\"v" + j + "\"")
                .collect(Collectors.joining(" "));
        final String sample = IntStream.range(0, 3000)
                .mapToObj(i -> ToolJson.dumps(line(i, " " + extra)))
                .collect(Collectors.joining("\n"));
        final List<Map<String, Object>> more = IntStream.range(0, 50)
                .mapToObj(j -> extraction("(?:^|[\\s,;])k" + j + "=\"([^\"]*)\"", "k" + j))
                .toList();
        final TranslationMapping m = kvMapping(more);
        final long started = System.nanoTime();

        final XPathChecks.Findings found = MappingSampleCheck.checkExtractions(m, List.of(sample), null,
                LocalCheck.sampleRecords(m, List.of(sample), null).records(), null, Clock.systemUTC());

        assertThat(found.problems()).isEmpty();
        // Within the check's own time: Saxon reads every text here, some 13 s on its own, longer on a loaded
        // machine.
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(MappingSampleCheck.CHECK_TIME);
    }

    @Test
    void aFieldAnInferredSplitterLacksIsAWarningNotARefusal() {
        // Seen: a hand-written syslog splitter names a field 'pwd'; the sample, read with the spec inferred from
        // it, has 'pid' instead, and the mapping was refused though the build's converter does write 'pwd'.
        final Map<String, Object> json = TranslationWorld.mapping();
        @SuppressWarnings("unchecked") final List<Object> common = new ArrayList<>((List<Object>) json.get("common"));
        common.add(Map.of("path", "EventSource/Device/Name", "field", "hosts"));
        json.put("common", common);
        final TranslationMapping m = TranslationMapping.parse(json);
        final List<String> sample = List.of("time,action,user,result,sid,host,port\n"
                                            + "2026-09-28T10:00:00.000Z,login,alice,ok,s1,ws01,22\n"
                                            + "2026-09-28T10:03:00.000Z,login,bob,fail,s2,ws03,22\n");
        final SplitterSpec spec = SplitterSpec.fromMap(Map.of("kind", "delimited", "header", true));

        final Map<String, Object> guessed = MappingSampleCheck.check(m, sample, spec, TranslationWorld.SCHEMA, true,
                Clock.systemUTC());
        assertThat(strings(guessed.get("problems"))).noneMatch(p -> p.contains("'hosts'"));
        assertThat(strings(guessed.get("warnings"))).anyMatch(w -> w.contains("field 'hosts'")
                                                                    && w.contains("inferred Data Splitter"));

        final Map<String, Object> given = MappingSampleCheck.check(m, sample, spec, TranslationWorld.SCHEMA, false,
                Clock.systemUTC());
        assertThat(strings(given.get("problems"))).anyMatch(p -> p.contains("field 'hosts'"));
    }

    @Test
    void theXpathsCheckedAreThoseOnlyTheRecordAnswers() {
        // Not those calling a shared XSLT's functions (only Stroom, which imports it, can run them), nor those
        // reading a variable, which only the XSLT binds; a regex replacement's $1 is not one.
        final TranslationMapping m = TranslationMapping.parse(ToolJson.loads("""
                {"input": "xml", "root": "logs", "record": "log",
                 "functions": [{"href": "GeoIP-V1", "prefix": "geo", "namespace": "http://example.com/geo"}],
                 "common": [{"path": "EventTime/TimeCreated", "xpath": "@time"},
                            {"path": "EventSource/Device/HostName", "xpath": "replace(host, '(.*)', '$1')"},
                            {"path": "EventSource/Device/IPAddress", "xpath": "geo:lookup-ip(ip)"},
                            {"path": "EventSource/User/Id", "xpath": "stroom:meta('User')"}],
                 "events": [{"name": "a", "when": [{"xpath": "kind", "equals": "x"}],
                             "fields": [{"path": "EventDetail/TypeId", "xpath": "$kind"}]}]}"""));

        assertThat(MappingSampleCheck.xpathInputs(m)).isEqualTo(Map.of("@time", List.of("EventTime/TimeCreated"),
                "replace(host, '(.*)', '$1')", List.of("EventSource/Device/HostName"),
                "kind", List.of("[a] when")));
    }

    @Test
    void fragmentsAreCheckedInTheirWrappersNamespace() {
        // As the XMLFragmentParser's wrapper gives them: a fragment declaring no namespace takes its default. Read
        // without it, the checks passed xpaths that select nothing in Stroom.
        final TranslationMapping m = TranslationMapping.parse(ToolJson.loads("""
                {"input": "xml_fragments", "record": "Event", "xml_namespace": "records:2",
                 "common": [{"path": "EventTime/TimeCreated", "xpath": "System/TimeCreated/@SystemTime"}],
                 "events": [{"name": "a", "fields": [{"path": "EventDetail/TypeId", "xpath": "System/EventID"}]}]}"""));

        final List<String> documents = MappingSampleCheck.recordDocuments(m, List.of(TestSampleProfiling.FRAGMENTS),
                null);

        assertThat(documents).hasSize(3).allSatisfy(d -> assertThat(d).startsWith("<Event xmlns=\"records:2\">"));
        assertThat(MappingSampleCheck.checkXPaths(m, List.of(TestSampleProfiling.FRAGMENTS), null)).isEmpty();
        // An xpath no record answers is said.
        final TranslationMapping missing = TranslationMapping.parse(ToolJson.loads(ToolJson.dumps(
                m.toJson(DumpMode.COMPACT)).replace("System/EventID",
                "System/Nope")));
        assertThat(MappingSampleCheck.checkXPaths(missing, List.of(TestSampleProfiling.FRAGMENTS), null))
                .singleElement().asString().contains("System/Nope").contains("selects nothing in any of the 3");
    }

    // A record: a time, a host and a key=value body, every seventh without the user key.
    private static Map<String, Object> line(final int i, final String more) {
        final Map<String, String> pairs = new LinkedHashMap<>();
        pairs.put("logid", "\"" + String.format("%010d", i) + "\"");
        pairs.put("type", i % 3 != 0
                ? "\"traffic\""
                : "\"event\"");
        pairs.put("action", "\"accept\"");
        pairs.put("srcip", "10.0.0." + (i % 250));
        pairs.put("dstip", "8.8.8.8");
        pairs.put("user", "\"u" + i + "\"");
        pairs.put("msg", "\"message " + i + "\"");
        if (i % 7 == 0) {
            pairs.remove("user");
        }
        final Map<String, Object> record = new LinkedHashMap<>();
        record.put("time", "2026-10-01T10:00:00.000Z");
        record.put("host", "fw1");
        record.put("body", pairs.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                                   .collect(Collectors.joining(" ")) + more);
        return record;
    }

    private static TranslationMapping kvMapping(final List<Map<String, Object>> extra) {
        final List<Map<String, Object>> extract = new ArrayList<>();
        for (final String k : KEYS) {
            extract.add(extraction(QUOTED.contains(k)
                    ? "(?:^|[\\s,;])" + k + "=\"([^\"]*)\""
                    : "(?:^|[\\s,;])" + k + "=([^\\s,;\"]*)", k));
        }
        extract.addAll(extra);
        final Map<String, Object> json = new LinkedHashMap<>(TranslationWorld.mapping());
        json.put("input", "json");
        json.put("json_layout", "lines");
        json.put("extract", extract);
        json.put("common", ToolJson.loads("""
                [{"path": "EventTime/TimeCreated", "field": "time"},
                 {"path": "EventSource/System/Name", "value": "FortiOS"},
                 {"path": "EventSource/System/Environment", "value": "Test"},
                 {"path": "EventSource/Generator", "value": "fortigate"},
                 {"path": "EventSource/Device/HostName", "field": "host"},
                 {"path": "EventSource/User/Id", "field": "user"}]"""));
        @SuppressWarnings("unchecked") final List<Object> events = (List<Object>) json.get("events");
        json.put("events", List.of(events.getFirst()));
        json.remove("unmatched");
        return TranslationMapping.parse(json);
    }

    private static Map<String, Object> extraction(final String regex, final String name) {
        return Map.of("field", "body", "regex", regex, "names", List.of(name));
    }

    private static XPathChecks.Findings checked(final TranslationMapping m, final Instant deadline) {
        return MappingSampleCheck.checkExtractions(m, List.of(SAMPLE), null,
                LocalCheck.sampleRecords(m, List.of(SAMPLE), null).records(), deadline, Clock.systemUTC());
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(final Object value) {
        return value instanceof final List<?> list
                ? (List<String>) list
                : List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> cases() {
        try (final InputStream in = TestMappingSampleCheck.class.getResourceAsStream("offline_cases.json")) {
            return JsonUtil.readValue(new String(in.readAllBytes(), StandardCharsets.UTF_8), List.class);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
