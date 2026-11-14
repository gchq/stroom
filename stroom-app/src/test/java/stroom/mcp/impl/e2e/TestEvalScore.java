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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
class TestEvalScore {

    private static final String LOGON = event("<EventTime><TimeCreated>2026-09-28T08:01:12.000Z</TimeCreated>"
                                              + "</EventTime>", "<TypeId>login</TypeId><Authenticate>"
                                                                + "<Action>Logon</Action><User><Id>alice</Id></User>"
                                                                + "<Data Name=\"gateway\" Value=\"vpn-gw1\"/>"
                                                                + "</Authenticate>");
    private static final String DENY = event("<EventTime><TimeCreated>2026-09-28T08:02:00.000Z</TimeCreated>"
                                             + "</EventTime>", "<TypeId>deny</TypeId><Network><Deny><Source>"
                                                               + "<Device><IPAddress>10.0.0.1</IPAddress></Device>"
                                                               + "</Source></Deny></Network>");

    @Test
    void eventsHoldingWhatTheCaseExpectsAreStage1() {
        final EvalScore score = new EvalScore("c");

        score.scoreEvents(expecting(Map.of("records", 2, "event_types", List.of("Authenticate", "Network"),
                        "paths", List.of("EventTime/TimeCreated", "EventDetail/TypeId"),
                        "values", Map.of("EventDetail/Authenticate/User/Id", List.of("alice")))),
                List.of(LOGON, DENY), List.of(true, true), null);
        score.indexed(true);

        assertThat(score.stage1()).isTrue();
        assertThat(score.passed()).isTrue();
        assertThat(score.events()).isEqualTo(2);
        assertThat(score.toMap()).containsEntry("event_types", List.of("Authenticate", "Network"));
    }

    @Test
    void aPathMayNameAlternativesOrAnyOneElement() {
        final EvalScore score = new EvalScore("c");

        score.scoreEvents(expecting(Map.of("records", 1, "event_types", List.of("Authenticate|Authorise", "Network"),
                        "paths", List.of("EventDetail/Network/*/Source", "EventDetail/Network/Permit|"
                                                                         + "EventDetail/Network/Deny"))),
                List.of(DENY), List.of(true), null);

        assertThat(score.toMap().get("missing_types")).isEqualTo(List.of("Authenticate|Authorise"));
        assertThat(score.toMap().get("missing_paths")).isEqualTo(List.of());
        assertThat(score.stage1()).isFalse();
    }

    @Test
    void whatIsWrongIsSaid() {
        final EvalScore score = new EvalScore("c");
        final Map<String, Object> expected = new HashMap<>(Map.of("records", 3,
                "event_types", List.of("Authenticate"), "paths", List.of("EventDetail/Authenticate/Outcome"),
                "values", Map.of("EventDetail/Authenticate/User/Id", List.of("bob")),
                "forbidden_values", Map.of("EventDetail/Authenticate/User/Id", List.of("alice")),
                "forbidden_types", List.of("Network")));

        score.scoreEvents(expecting(expected), List.of(LOGON, DENY, "not xml"), List.of(true, false, false), null);
        score.indexed(true);

        assertThat(score.passed()).isFalse();
        assertThat((List<String>) score.toMap().get("problems")).containsExactly(
                "2 events, expected 3",
                "2 of 3 Events records invalid",
                "no event has EventDetail/Authenticate/User/Id = [bob]",
                "events have EventDetail/Authenticate/User/Id = [alice], which none should",
                "events of type [Network], which none should be");
        assertThat(score.toMap().get("missing_paths")).isEqualTo(List.of("EventDetail/Authenticate/Outcome"));
        assertThat(score.summary()).startsWith("c: FAIL stage1=false indexed=true events=1/2");
    }

    @Test
    void dataIsLookedForWhereItBelongsInEveryEventOrSome() {
        final Map<String, Object> every = Map.of("name", "gateway", "at", "EventDetail/Authenticate");
        final Map<String, Object> some = Map.of("name", "gateway", "value", "vpn-gw1",
                "at", "EventDetail/Authenticate", "every", false);
        final Map<String, Object> elsewhere = Map.of("name", "gateway", "at", "EventDetail/Authenticate/User");

        assertThat(problems(List.of(every), List.of(LOGON))).isEmpty();
        assertThat(problems(List.of(some), List.of(LOGON, DENY))).isEmpty();
        assertThat(problems(List.of(every), List.of(LOGON, DENY)))
                .containsExactly("not every event has Data 'gateway' under EventDetail/Authenticate");
        assertThat(problems(List.of(elsewhere), List.of(LOGON)))
                .containsExactly("not every event has Data 'gateway' under EventDetail/Authenticate/User");
    }

    @Test
    void onlySomeReadTheCountIsTheStreamsAll() {
        final EvalScore score = new EvalScore("c");

        score.scoreEvents(expecting(Map.of("records", 1000, "event_types", List.of("Authenticate"),
                "paths", List.of())), List.of(LOGON), List.of(true), 1000);

        assertThat(score.stage1()).isTrue();
        assertThat(score.events()).isEqualTo(1000);
        assertThat((List<String>) score.toMap().get("notes"))
                .containsExactly("1000 events in the Events streams; 1 read and checked");
    }

    @Test
    void noEventsReadIsNoPass() {
        final EvalScore score = new EvalScore("c");

        score.scoreEvents(expecting(Map.of("records", 0, "event_types", List.of(), "paths", List.of())),
                List.of(), List.of(), null);
        score.indexed(true);

        assertThat(score.stage1()).isFalse();
        assertThat((List<String>) score.toMap().get("problems")).containsExactly("0 of 0 Events records invalid");
    }

    @Test
    void anAggregatedFeedsBuildIsScoredFromItsRegistryAndWhatItsGroupsPipelineLogged() {
        final EvalScore score = new EvalScore("c");

        score.scoreSourceTypes(expecting(Map.of("source_types", Map.of("field", "LogFilePath",
                "group", Map.of("name", "web server", "values", List.of("/var/log/httpd/access_log",
                        "/var/log/httpd/error_log"), "others", List.of("/var/log/mail/maillog",
                        "/opt/gateway/logs/gw.log")),
                "rows", List.of(Map.of("match", "pattern", "value", "^/opt/gateway/logs/", "status", "parked")),
                "passed_over", 240))), registry(), 240L, 0);

        assertThat(score.sourceTypes()).isTrue();
        assertThat((List<String>) score.toMap().get("problems")).isEmpty();
        assertThat((List<Map<String, Object>>) score.toMap().get("source_type_checks"))
                .extracting(check -> check.get("check"))
                .containsExactly("field", "group source types", "group name", "row pattern ^/opt/gateway/logs/ "
                                                                              + "(parked)", "passed_over");
    }

    @Test
    void whatIsWrongWithTheSourceTypesIsSaid() {
        final EvalScore score = new EvalScore("c");

        // Expected: the group takes the mail log too, a row the registry lacks, more passed over.
        score.scoreSourceTypes(expecting(Map.of("source_types", Map.of("field", "Path",
                "group", Map.of("name", "Web", "values", List.of("/var/log/httpd/access_log",
                        "/var/log/mail/maillog"), "others", List.of("/var/log/httpd/error_log")),
                "rows", List.of(Map.of("match", "member", "value", "Mail")),
                "passed_over", 100))), registry(), 240L, 2);

        assertThat(score.sourceTypes()).isFalse();
        assertThat((List<String>) score.toMap().get("problems")).containsExactly(
                "the registry's source type field is LogFilePath, expected Path",
                "the group Web server [Web server] doesn't take [/var/log/mail/maillog] and takes "
                + "[/var/log/httpd/error_log], which it shouldn't",
                "the group is called Web server, expected Web",
                "the registry has no row member Mail; its rows: [Web server: pattern ^/var/log/httpd/ (translated), "
                + "Mail: pattern ^/var/log/mail/ (parked), Gateway: pattern ^/opt/gateway/logs/ (parked)]",
                "the group's pipeline passed over 240 record(s), expected 100, and logged 2 ERROR or FATAL line(s)");
    }

    @Test
    void noRegistryOrNoSourceTypesExpected() {
        final EvalScore none = new EvalScore("c");
        none.scoreSourceTypes(expecting(Map.of("source_types", Map.of("field", "Workload"))), null, null, 0);
        assertThat(none.sourceTypes()).isFalse();
        assertThat((List<String>) none.toMap().get("problems"))
                .containsExactly("the build has no source type registry");

        // A case that isn't an aggregated feed's: nothing checked.
        final EvalScore plain = new EvalScore("c");
        plain.scoreSourceTypes(expecting(Map.of("records", 1)), null, null, 0);
        assertThat(plain.sourceTypes()).isTrue();
        assertThat(plain.toMap()).doesNotContainKey("source_type_checks");
    }

    @Test
    void theRegistrysGroupsAreScoredExactly() {
        final EvalScore score = new EvalScore("c");

        score.scoreSourceTypes(expecting(Map.of("source_types", Map.of("groups", List.of("Web", "Mail")))),
                laterRegistry("p-new"), null, 0);

        assertThat((List<String>) score.toMap().get("problems"))
                .containsExactly("the registry's groups are [Messaging, Web], expected [Web, Mail]");
    }

    @Test
    void aLaterBuildIsScoredAgainstWhatItsSetupLeft() {
        final Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("pipeline", "a");
        snapshot.put("XSLT", "b");
        final EvalScore.Before before = new EvalScore.Before("Messaging", "p-messaging", "i-messaging", snapshot);
        final Map<String, Object> evalCase = expecting(Map.of("source_types", Map.of(
                        "group", Map.of("name", "Web", "values", List.of("/var/log/httpd/access_log")),
                        "pipeline", "new"),
                "unchanged", true, "separate_index", true));

        final EvalScore passing = new EvalScore("c");
        passing.scoreLaterBuild(evalCase, laterRegistry("p-new"), before, Map.of("pipeline", "a", "XSLT", "b"),
                "eval-60", "i-web");
        assertThat(passing.sourceTypes()).isTrue();
        assertThat((List<Map<String, Object>>) passing.toMap().get("source_type_checks"))
                .extracting(check -> check.get("check"))
                .containsExactly("pipeline new", "unchanged pipeline", "unchanged XSLT", "separate index");

        // The setup group's pipeline taken, its XSLT changed, and the Events in its index.
        final EvalScore failing = new EvalScore("c");
        failing.scoreLaterBuild(evalCase, laterRegistry("p-messaging"), before, Map.of("pipeline", "a",
                "XSLT", "changed"), "eval-60", "i-messaging");
        assertThat((List<String>) failing.toMap().get("problems")).containsExactly(
                "the group's rows name pipeline(s) [p-messaging], expected a new one, not the setup's p-messaging",
                "the setup group Messaging's XSLT changed",
                "the build's Events were found in index i-messaging, expected one of their own, not the setup "
                + "group's i-messaging");
    }

    @Test
    void aJoinedGroupKeepsItsPipelineAndItsIndexGainsTheBuildsFields() {
        final EvalScore.Before before = new EvalScore.Before("Web", "p-web", "i-web", Map.of());
        final Map<String, Object> evalCase = expecting(Map.of("source_types", Map.of(
                "group", Map.of("name", "Web", "values", List.of("/var/log/httpd/access_log")),
                "pipeline", "same", "index", Map.of("fields_added", true))));
        final Map<String, Object> registry = new HashMap<>(laterRegistry("p-web"));
        registry.put("indexes", Map.of("web-index", Map.of("index", "web-index", "groups", List.of("Web"),
                "added_by", Map.of("Path", "eval-56", "Name", "eval-61"))));

        final EvalScore joined = new EvalScore("c");
        joined.scoreLaterBuild(evalCase, registry, before, Map.of(), "eval-61", "i-web");
        assertThat(joined.sourceTypes()).as("%s", joined.toMap()).isTrue();

        // Nothing added by this build.
        final EvalScore unchanged = new EvalScore("c");
        unchanged.scoreLaterBuild(evalCase, registry, before, Map.of(), "eval-62", "i-web");
        assertThat((List<String>) unchanged.toMap().get("problems")).singleElement().asString()
                .startsWith("the index of group Web (web-index) has no field added by eval-62");
    }

    @Test
    void theFormsAskedAndNotAskedAreScored() {
        final Map<String, Object> evalCase = expecting(Map.of("forms", Map.of(
                "asked", List.of(Map.of("message", "runs in pipeline", "answer", "Change ")),
                "not_asked", List.of("Which field says"))));
        final Map<String, Object> changed = Map.of("message", "Group 'M' runs in pipeline P, in production.",
                "content", Map.of("choice", "Change P (its working copy ...)"));
        final Map<String, Object> versioned = Map.of("message", "Group 'M' runs in pipeline P, in production.",
                "content", Map.of("choice", "A new version of P"));
        final Map<String, Object> field = Map.of("message", "Which field says which source a record came from?");

        final EvalScore passing = new EvalScore("c");
        passing.scoreForms(evalCase, List.of(changed));
        assertThat(passing.sourceTypes()).isTrue();

        final EvalScore failing = new EvalScore("c");
        failing.scoreForms(evalCase, List.of(versioned, field));
        assertThat((List<String>) failing.toMap().get("problems")).containsExactly(
                "the form 'runs in pipeline' wasn't answered 'Change …': [{choice=A new version of P}]",
                "a form asked 'Which field says', which none should");

        final EvalScore none = new EvalScore("c");
        none.scoreForms(evalCase, List.of());
        assertThat((List<String>) none.toMap().get("problems")).containsExactly("no form asked 'runs in pipeline'");
    }

    // A later build's registry: the web server group's row naming its pipeline, the mail row the Messaging group's.
    private static Map<String, Object> laterRegistry(final String pipeline) {
        return Map.of("field", "LogFilePath",
                "rows", List.of(
                        Map.of("name", "Web server", "match", "pattern", "value", "^/var/log/httpd/",
                                "status", "translated", "group", "Web", "pipeline", Map.of("uuid", pipeline)),
                        Map.of("name", "Mail", "match", "pattern", "value", "^/var/log/mail/", "status",
                                "translated", "group", "Messaging", "pipeline", Map.of("uuid", "p-messaging"))),
                "scope", Map.of("group", "Web", "source_types", List.of("Web server")));
    }

    // A registry as SourceTypeRegistry reads it: the collector's paths, its scope the web server group.
    private static Map<String, Object> registry() {
        return Map.of("field", "LogFilePath",
                "rows", List.of(
                        Map.of("name", "Web server", "match", "pattern", "value", "^/var/log/httpd/",
                                "status", "translated"),
                        Map.of("name", "Mail", "match", "pattern", "value", "^/var/log/mail/", "status", "parked"),
                        Map.of("name", "Gateway", "match", "pattern", "value", "^/opt/gateway/logs/",
                                "status", "parked")),
                "scope", Map.of("group", "Web server", "source_types", List.of("Web server")));
    }

    private static List<String> problems(final List<Map<String, Object>> data, final List<String> records) {
        final EvalScore score = new EvalScore("c");
        score.scoreEvents(expecting(Map.of("records", records.size(), "event_types", List.of(), "paths", List.of(),
                "data", data)), records, records.stream().map(record -> true).toList(), null);
        return (List<String>) score.toMap().get("problems");
    }

    private static Map<String, Object> expecting(final Map<String, Object> expected) {
        return Map.of("expected", expected);
    }

    private static String event(final String time, final String detail) {
        return "<Events xmlns=\"event-logging:3\"><Event>" + time + "<EventDetail>" + detail
               + "</EventDetail></Event></Events>";
    }
}
