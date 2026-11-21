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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SuppressWarnings("unchecked")
class TestEvalScore {

    private static final String LOGON = event("<EventTime><TimeCreated>2026-09-28T08:01:12.000Z</TimeCreated>"
                                              + "</EventTime>", "<TypeId>Authenticate-Logon</TypeId>"
                                                                + "<Description>Signed in</Description><Authenticate>"
                                                                + "<Action>Logon</Action><User><Id>alice</Id></User>"
                                                                + "<Data Name=\"gateway\" Value=\"vpn-gw1\"/>"
                                                                + "</Authenticate>");
    private static final String DENY = event("<EventTime><TimeCreated>2026-09-28T08:02:00.000Z</TimeCreated>"
                                             + "</EventTime>", "<TypeId>Network-Deny</TypeId>"
                                                               + "<Description>Denied</Description><Network><Deny>"
                                                               + "<Source><Device><IPAddress>10.0.0.1</IPAddress>"
                                                               + "</Device></Source></Deny></Network>");

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
    void aLaterBuildThatNeverFinishedFailsWithAPlainProblem() {
        // Seen in eval case 61: the build never saved its group, so the joining source type's row was still
        // parked, with no pipeline, and scoring threw a NullPointerException.
        final EvalScore.Before before = new EvalScore.Before("Web", "p-web", "i-web", Map.of());
        final Map<String, Object> evalCase = expecting(Map.of("source_types", Map.of(
                "group", Map.of("name", "Web", "values", List.of("/var/log/httpd/access_log",
                        "/var/log/mail/maillog")),
                "pipeline", "same")));
        final Map<String, Object> registry = Map.of("field", "LogFilePath",
                "rows", List.of(
                        Map.of("name", "Web server", "match", "pattern", "value", "^/var/log/httpd/",
                                "status", "translated", "group", "Web", "pipeline", Map.of("uuid", "p-web")),
                        Map.of("name", "Mail", "match", "pattern", "value", "^/var/log/mail/", "status",
                                "parked")));

        final EvalScore unfinished = new EvalScore("c");
        unfinished.scoreLaterBuild(evalCase, registry, before, Map.of(), "eval-61", null);

        assertThat(unfinished.sourceTypes()).isFalse();
        assertThat((List<String>) unfinished.toMap().get("problems")).containsExactly(
                "the group's rows name pipeline(s) [p-web, null], expected the setup's p-web");
        // No setup pipeline recorded either: still a plain problem.
        final EvalScore noSetup = new EvalScore("c");
        noSetup.scoreLaterBuild(evalCase, registry, new EvalScore.Before("Web", null, null, Map.of()), Map.of(),
                "eval-61", null);
        assertThat((List<String>) noSetup.toMap().get("problems")).singleElement().asString()
                .startsWith("the group's rows name pipeline(s) [p-web, null]");
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

    @Test
    void aFormAskedTooOftenOrOfferingWhatNoneShouldIsAProblem() {
        final Map<String, Object> evalCase = expecting(Map.of("forms", Map.of(
                "asked", List.of(Map.of("message", "go in this build's", "at_most", 1)),
                "not_asked", List.of(Map.of("message", "kinds of event apart", "offering", "^(time|orig_rt)( |$)")))));
        final Map<String, Object> scope = Map.of("message", "Which source types go in this build's group?");
        final Map<String, Object> kinds = Map.of("message", "Which field tells the kinds of event apart?",
                "options", List.of("event.action (LOGIN, LOGOUT)", "level (INFO, WARN)"));
        final Map<String, Object> timeKinds = Map.of("message", "Which field tells the kinds of event apart?",
                "options", List.of("time (1794992402171, 1794992403171)", "event.action (LOGIN)"));

        final EvalScore passing = new EvalScore("c");
        passing.scoreForms(evalCase, List.of(scope, kinds));
        assertThat(passing.sourceTypes()).isTrue();

        final EvalScore failing = new EvalScore("c");
        failing.scoreForms(evalCase, List.of(scope, scope, timeKinds));
        assertThat((List<String>) failing.toMap().get("problems")).containsExactly(
                "the form 'go in this build's' was asked 2 times, at most 1 expected",
                "a form asking 'kinds of event apart' offered [time (1794992402171, 1794992403171)], which none "
                + "should");
    }

    @Test
    void theLayoutIsScoredOnTheGroupsPipelineFolder() {
        final Map<String, Object> evalCase = expecting(Map.of("source_types", Map.of("layout", "/Events/V1\\.0$")));

        final EvalScore passing = new EvalScore("c");
        passing.scoreLayout(evalCase, "System/Eval Feeds/EVAL-65/Security audit/Events/V1.0");
        assertThat(passing.sourceTypes()).isTrue();

        final EvalScore failing = new EvalScore("c");
        failing.scoreLayout(evalCase, "System/Eval Feeds/EVAL-65/V1.0/Events");
        failing.scoreLayout(evalCase, null);
        assertThat((List<String>) failing.toMap().get("problems")).containsExactly(
                "the group's pipeline is in System/Eval Feeds/EVAL-65/V1.0/Events, expected a folder matching "
                + "/Events/V1\\.0$",
                "the group's pipeline is in null, expected a folder matching /Events/V1\\.0$");

        final EvalScore none = new EvalScore("c");
        none.scoreLayout(expecting(Map.of()), null);
        assertThat(none.sourceTypes()).isTrue();
    }

    @Test
    void textsAreLookedForAnywhereInAnEvent() {
        final String quoted = event("<EventTime><TimeCreated>2026-09-28T08:01:12.000Z</TimeCreated></EventTime>",
                "<TypeId>Network-Block</TypeId><Description>say \"hi\"</Description><Network><Open><Destination>"
                + "<Data Name=\"msg\" Value=\"a &quot;b&quot; c\"/></Destination></Open></Network>");
        final Map<String, Object> expected = Map.of("records", 1, "texts", List.of("say \"hi\"", "a \"b\" c"),
                "forbidden_texts", List.of("say \\\"hi\\\""));

        final EvalScore passing = new EvalScore("c");
        passing.scoreEvents(expecting(expected), List.of(quoted), List.of(true), null);
        assertThat(passing.stage1()).isTrue();

        final String escaped = quoted.replace("say \"hi\"", "say \\\"hi\\\"");
        final EvalScore failing = new EvalScore("c");
        failing.scoreEvents(expecting(expected), List.of(escaped), List.of(true), null);
        assertThat(failing.stage1()).isFalse();
        assertThat((List<String>) failing.toMap().get("problems")).containsExactly(
                "no event holds [say \"hi\"] anywhere",
                "events hold [say \\\"hi\\\"], which none should");
    }

    @Test
    void typeIdsAreScoredVerbObjectDistinctForbiddenAndShared() {
        final List<String> events = List.of(
                typed("Authenticate-Logon", "<Data Name=\"eventType\" Value=\"user.session.start\"/>"),
                typed("Authenticate-Logon", "<Data Name=\"type\" Value=\"login\"/>"),
                typed("Authenticate-Logoff", "<Data Name=\"type\" Value=\"logout\"/>"),
                unknown("x"));
        final Map<String, Object> expected = Map.of("records", 4, "type_ids", Map.of("pattern", "verb_object",
                "min_distinct", 2, "forbidden", List.of("login"),
                "same", List.of(List.of(Map.of("eventType", "user.session.start"), Map.of("type", "login")))),
                "unknown_at_most", 1);

        final EvalScore score = new EvalScore("c");
        score.scoreEvents(expecting(expected), events, List.of(true, true, true, true), null);

        assertThat((List<String>) score.toMap().get("problems")).isEmpty();
        assertThat(score.stage1()).isTrue();
        assertThat(score.toMap()).containsEntry("type_ids", List.of("Authenticate-Logoff", "Authenticate-Logon",
                "Unknown-X"));
    }

    @Test
    void whatIsWrongWithTheTypeIdsIsSaid() {
        final List<String> events = List.of(
                typed("user.session.start", "<Data Name=\"event.eventType\" Value=\"user.session.start\"/>"),
                typed("login", "<Data Name=\"type\" Value=\"login\"/>"),
                unknown("a"),
                unknown("b"));
        final Map<String, Object> expected = Map.of("records", 4, "type_ids", Map.of("pattern", "verb_object",
                "min_distinct", 3, "forbidden", List.of("login"),
                "same", List.of(List.of(Map.of("eventType", "user.session.start"), Map.of("type", "login")),
                        List.of(Map.of("type", "logout")))),
                "unknown_at_most", 1);

        final EvalScore score = new EvalScore("c");
        score.scoreEvents(expecting(expected), events, List.of(true, true, true, true), null);

        assertThat(score.stage1()).isFalse();
        assertThat((List<String>) score.toMap().get("problems")).containsExactly(
                "TypeIds [login, user.session.start] are not Verb-Object",
                "2 distinct TypeIds [login, user.session.start], expected at least 3",
                "events have TypeId [login], which none should",
                "records that must share one TypeId have [login, user.session.start]: {eventType=user.session.start}"
                + " [user.session.start], {type=login} [login]",
                "no event matches {type=logout}, which must share its TypeId",
                "2 Unknown events, at most 1 expected");
    }

    @Test
    void aTypeIdOfMoreThanTwoPartsIsVerbObjectAsTheCatalogueHasIt() {
        // Seen in eval case 69 (2026-11-20): Raise-Malware-Alert, which the catalogue takes, scored not Verb-Object.
        final EvalScore score = new EvalScore("c");
        score.scoreEvents(expecting(Map.of("records", 2, "type_ids", Map.of("pattern", "verb_object"))),
                List.of(typed("Raise-Malware-Alert", ""), typed("Update-User2-Password", "")), List.of(true, true),
                null);

        assertThat((List<String>) score.toMap().get("problems")).isEmpty();

        final EvalScore trailing = new EvalScore("c");
        trailing.scoreEvents(expecting(Map.of("records", 1, "type_ids", Map.of("pattern", "verb_object"))),
                List.of(typed("Raise-Malware-", "")), List.of(true), null);
        assertThat((List<String>) trailing.toMap().get("problems")).containsExactly(
                "TypeIds [Raise-Malware-] are not Verb-Object");
    }

    @Test
    void aTypeIdOfAnyFormMayBeAllowedAndUnknownEventsAreLeftOutOfTheForm() {
        final EvalScore score = new EvalScore("c");
        score.scoreEvents(expecting(Map.of("records", 2, "type_ids", Map.of("pattern", "any"))),
                List.of(typed("login", ""), unknown("x")), List.of(true, true), null);

        assertThat((List<String>) score.toMap().get("problems")).isEmpty();

        final EvalScore verbObject = new EvalScore("c");
        verbObject.scoreEvents(expecting(Map.of("records", 1, "type_ids", Map.of("pattern", "verb_object"))),
                List.of(unknown("lower case")), List.of(true), null);
        assertThat((List<String>) verbObject.toMap().get("problems")).isEmpty();
    }

    @Test
    void aDescriptionOnEveryEventIsScoredLeavingOutUnknownEvents() {
        final String described = event("", "<TypeId>Authenticate-Logon</TypeId><Description>Signed in</Description>"
                                            + "<Authenticate><Action>Logon</Action></Authenticate>");
        final String bare = event("", "<TypeId>Authenticate-Logoff</TypeId><Description> </Description>"
                                      + "<Authenticate><Action>Logoff</Action></Authenticate>");
        final Map<String, Object> expected = Map.of("records", 2, "description_every_event", true);

        final EvalScore passing = new EvalScore("c");
        passing.scoreEvents(expecting(expected), List.of(described, unknown("x")), List.of(true, true), null);
        assertThat((List<String>) passing.toMap().get("problems")).isEmpty();

        final EvalScore failing = new EvalScore("c");
        failing.scoreEvents(expecting(expected), List.of(described, bare), List.of(true, true), null);
        assertThat(failing.stage1()).isFalse();
        assertThat((List<String>) failing.toMap().get("problems"))
                .containsExactly("1 of 2 events have no EventDetail/Description: TypeIds [Authenticate-Logoff]");
    }

    @Test
    void aDescriptionIsOptionalUnlessTheCaseAsksForOne() {
        // Decided by the user 2026-11-20: an event type need not have a description, so a case scores Descriptions
        // only when it says so (one whose records say what happened).
        final String bare = event("", "<TypeId>Authenticate-Logoff</TypeId>"
                                      + "<Authenticate><Action>Logoff</Action></Authenticate>");

        final EvalScore score = new EvalScore("c");
        score.scoreEvents(expecting(Map.of("records", 1)), List.of(bare), List.of(true), null);

        assertThat((List<String>) score.toMap().get("problems")).isEmpty();
    }

    @Test
    void theCatalogueIsScoredFromTheSourceNotesAndTheCheck() {
        final Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("catch_all_confirmed", true);
        expected.put("no_overlaps", true);
        expected.put("description_every", true);
        expected.put("no_condition_on_source_type_field", true);
        expected.put("recognised_two_ways", List.of("Authenticate-Logon"));
        expected.put("documentation_only", List.of("Unlock-Account|Update-Account"));
        expected.put("dropped", List.of("process-heartbeat"));
        final EvalScore score = new EvalScore("c");

        score.scoreCatalogue(expecting(Map.of("catalogue", expected)), catalogue(), check(List.of()));

        assertThat((List<String>) score.toMap().get("problems")).isEmpty();
        assertThat((List<Map<String, Object>>) score.toMap().get("catalogue_checks"))
                .extracting(check -> check.get("check"))
                .containsExactly("catch-all confirmed", "no overlaps", "description on every event type",
                        "no condition on the source type field", "Authenticate-Logon recognised two ways",
                        "Unlock-Account|Update-Account from documentation only", "process-heartbeat dropped");

        final EvalScore counted = new EvalScore("c");
        counted.scoreCatalogue(expecting(Map.of("catalogue", Map.of("documentation_only", 1))), catalogue(),
                check(List.of()));
        assertThat((List<String>) counted.toMap().get("problems")).isEmpty();
    }

    @Test
    void whatIsWrongWithTheCatalogueIsSaid() {
        final Map<String, Object> notes = catalogue();
        notes.put("catch_all", Map.of("status", "proposed"));
        final List<Map<String, Object>> types = (List<Map<String, Object>>) notes.get("event_types");
        types.getFirst().put("description", "");
        ((List<Map<String, Object>>) types.get(1).get("recognised_by")).add(Map.of("when", List.of(
                Map.of("field", "event.log_type", "equals", "Integration"))));
        final Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("catch_all_confirmed", true);
        expected.put("no_overlaps", true);
        expected.put("description_every", true);
        expected.put("no_condition_on_source_type_field", true);
        expected.put("recognised_two_ways", List.of("Unlock-Account", "Process-Missing"));
        expected.put("documentation_only", 2);
        expected.put("dropped", List.of("Authenticate-Logon"));
        final EvalScore score = new EvalScore("c");

        score.scoreCatalogue(expecting(Map.of("catalogue", expected)), notes, check(List.of(Map.of("record", 3,
                "type_ids", List.of("Authenticate-Logon", "Authenticate-Logoff")))));

        assertThat((List<String>) score.toMap().get("problems")).containsExactly(
                "the catch-all is proposed, not confirmed",
                "event types overlap: record 3 [Authenticate-Logon, Authenticate-Logoff]",
                "event types with no description: [Authenticate-Logon]",
                "event types recognised by the source type field event>log_type: [Authenticate-Logoff]",
                "Unlock-Account is recognised 1 way, expected two or more",
                "no event type Process-Missing in the catalogue",
                "1 event type(s) from documentation only, with no record in the sample, expected at least 2",
                "Authenticate-Logon is not dropped");
    }

    @Test
    void soManyEventTypesRecognisedTwoWaysAreScoredWhateverTheirTypeIds() {
        // Seen in eval cases 30 and 67 (2026-11-20): the agent's TypeIds are its own (Start-Session for
        // Authenticate-Logon), so a case may count the event types recognised two ways instead of naming them.
        final EvalScore one = new EvalScore("c");
        one.scoreCatalogue(expecting(Map.of("catalogue", Map.of("recognised_two_ways", 1))), catalogue(),
                check(List.of()));
        assertThat((List<String>) one.toMap().get("problems")).isEmpty();
        assertThat((List<Map<String, Object>>) one.toMap().get("catalogue_checks"))
                .extracting(check -> check.get("check"))
                .containsExactly("at least 1 recognised two ways");

        final EvalScore two = new EvalScore("c");
        two.scoreCatalogue(expecting(Map.of("catalogue", Map.of("recognised_two_ways", 2))), catalogue(),
                check(List.of()));
        assertThat((List<String>) two.toMap().get("problems")).containsExactly(
                "1 event type(s) recognised two or more ways [Authenticate-Logon], expected at least 2");
    }

    @Test
    void aCatalogueThatGrewHasAtLeastSoManyEventTypesNotDropped() {
        final EvalScore grown = new EvalScore("c");
        grown.scoreCatalogue(expecting(Map.of("catalogue", Map.of("event_types_at_least", 3))), catalogue(),
                check(List.of()));
        assertThat((List<String>) grown.toMap().get("problems")).isEmpty();

        final EvalScore small = new EvalScore("c");
        small.scoreCatalogue(expecting(Map.of("catalogue", Map.of("event_types_at_least", 4))), catalogue(),
                check(List.of()));
        assertThat((List<String>) small.toMap().get("problems"))
                .containsExactly("the catalogue has 3 event types, expected at least 4");
    }

    @Test
    void noCatalogueOrNoCheckIsAProblemAndNoneExpectedIsNotScored() {
        final EvalScore none = new EvalScore("c");
        none.scoreCatalogue(expecting(Map.of("catalogue", Map.of("catch_all_confirmed", true))), null, null);
        assertThat((List<String>) none.toMap().get("problems"))
                .containsExactly("the build's source notes hold no event type catalogue");

        final EvalScore unchecked = new EvalScore("c");
        unchecked.scoreCatalogue(expecting(Map.of("catalogue", Map.of("no_overlaps", true))), catalogue(), null);
        assertThat((List<String>) unchecked.toMap().get("problems"))
                .containsExactly("no catalogue check to score the overlaps on");

        final EvalScore plain = new EvalScore("c");
        plain.scoreCatalogue(expecting(Map.of("records", 1)), null, null);
        assertThat(plain.toMap()).doesNotContainKey("catalogue_checks");
        assertThat((List<String>) plain.toMap().get("problems")).isEmpty();
    }

    @Test
    void typedPathsAreHeldOutsideDataWithAlternatives() {
        final String send = event("", "<TypeId>Send-Mail</TypeId><Description>Sent</Description><Send><Source><User>"
                                      + "<EmailAddress>kate@contoso.example</EmailAddress></User></Source>"
                                      + "<Destination><User><Id>anja</Id></User></Destination><Payload><Email>"
                                      + "<Subject>Re: roster</Subject></Email></Payload>"
                                      + data("Operation", "Send") + "</Send>");
        final Map<String, Object> expected = Map.of("records", 1, "typed", List.of(Map.of(
                "select", Map.of("text", "Send"),
                "paths", List.of("Send/Source/User/Id|Send/Source/User/EmailAddress", "Send/Destination/User",
                        "Send/Payload/*/Subject"))));

        final EvalScore score = new EvalScore("c");
        score.scoreEvents(expecting(expected), List.of(send), List.of(true), null);

        assertThat((List<String>) score.toMap().get("problems")).isEmpty();
        assertThat(score.stage1()).isTrue();
        assertThat((List<Map<String, Object>>) score.toMap().get("typed_checks"))
                .extracting(check -> check.get("check"), check -> check.get("passed"))
                .containsExactly(
                        tuple("typed {text=Send} Send/Source/User/Id|Send/Source/User/EmailAddress", true),
                        tuple("typed {text=Send} Send/Destination/User", true),
                        tuple("typed {text=Send} Send/Payload/*/Subject", true));
    }

    @Test
    void dataNeverSatisfiesATypedPath() {
        // The values are all there, but in Data: a Data element named as the path's element, a typed element that
        // holds only Data, and Data that * would reach.
        final String send = event("", "<TypeId>Send-Mail</TypeId><Send><Source><User>"
                                      + data("Id", "kate") + "</User></Source>"
                                      + data("Operation", "Send") + "<Data Name=\"Subject\" Value=\"Re: roster\"/>"
                                      + "</Send>");
        final Map<String, Object> expected = Map.of("records", 1, "typed", List.of(Map.of(
                "select", Map.of("action", "Send"),
                "paths", List.of("Send/Source/User", "Send/Source/User/Data", "Send/Subject|Send/*"))));

        final EvalScore score = new EvalScore("c");
        score.scoreEvents(expecting(expected), List.of(send), List.of(true), null);

        assertThat(score.stage1()).isFalse();
        assertThat((List<String>) score.toMap().get("problems")).containsExactly(
                "no event of {action=Send} (TypeIds [Send-Mail]) has a typed EventDetail/Send/Source/User (Data "
                + "doesn't count)",
                "no event of {action=Send} (TypeIds [Send-Mail]) has a typed EventDetail/Send/Source/User/Data (Data "
                + "doesn't count)",
                "no event of {action=Send} (TypeIds [Send-Mail]) has a typed EventDetail/Send/Subject|Send/* (Data "
                + "doesn't count)");
    }

    @Test
    void aTypedPathNeedsOnlyOneSelectedEventAndAnEntrySelectingNoneIsAProblem() {
        final String typed = event("", "<TypeId>Move-MailItems</TypeId><Move><Source><Folder><Path>\\Inbox</Path>"
                                       + "</Folder></Source>" + data("Operation", "MoveToDeletedItems") + "</Move>");
        final String bare = event("", "<TypeId>Move-MailItems</TypeId><Move>"
                                      + data("Operation", "MoveToDeletedItems") + "</Move>");
        final Map<String, Object> expected = Map.of("records", 2, "typed", List.of(
                Map.of("select", Map.of("text", "MoveToDeletedItems"), "paths", List.of("Move/Source/Folder/Path")),
                Map.of("select", Map.of("text", "TeamMemberAdded"), "paths", List.of("Authorise/User/Id"))));

        final EvalScore score = new EvalScore("c");
        score.scoreEvents(expecting(expected), List.of(bare, typed), List.of(true, true), null);

        assertThat(score.stage1()).isFalse();
        assertThat((List<String>) score.toMap().get("problems")).containsExactly(
                "no event matches {text=TeamMemberAdded}, whose typed paths are expected");
    }

    @Test
    void theEventsAreSelectedByTypeIdActionTextOrData() {
        final String move = event("", "<TypeId>Move-MailItems</TypeId><Move><Source><Folder><Path>\\Inbox</Path>"
                                      + "</Folder></Source>" + data("event.Operation", "MoveToDeletedItems")
                                      + "</Move>");
        final String delete = event("", "<TypeId>Delete-MailItem</TypeId><Delete><Email><Id>m1</Id></Email>"
                                        + data("Operation", "HardDelete") + "</Delete>");
        final List<Map<String, Object>> selectors = List.of(
                Map.of("type_id", "Move-.*"),
                Map.of("type_id", "Move"),
                Map.of("action", "Delete|Move"),
                Map.of("text", "\\Inbox"),
                Map.of("data", Map.of("Operation", "MoveToDeletedItems")),
                Map.of("action", "Delete", "text", "MoveToDeletedItems"));
        final List<Object> selected = new ArrayList<>();
        for (final Map<String, Object> selector : selectors) {
            final EvalScore score = new EvalScore("c");
            score.scoreEvents(expecting(Map.of("records", 2, "typed", List.of(Map.of("select", selector,
                    "paths", List.of())), "data_share_at_most", 1)), List.of(move, delete), List.of(true, true),
                    null);
            selected.add(((List<String>) score.toMap().get("problems")).isEmpty());
        }

        // A TypeId pattern matches the whole TypeId; every key of a selector must hold.
        assertThat(selected).containsExactly(true, false, true, true, true, false);
        final EvalScore unknownKey = new EvalScore("c");
        assertThatThrownBy(() -> unknownKey.scoreEvents(expecting(Map.of("records", 1, "typed", List.of(Map.of(
                "select", Map.of("operation", "Send"), "paths", List.of())))), List.of(move), List.of(true), null))
                .hasMessageContaining("typed select operation is none of type_id, action, text and data");
    }

    @Test
    void theDataShareIsMeasuredByTypeIdReportedAndCapped() {
        // Send-Mail: 4 values (the actor, a recipient, the subject in Data, the outcome) of which 1 in Data;
        // Update-InboxRule: all 3 in Data, a nested Data's values counted and its Name not. The TypeId, the
        // Description and the Unknown event are left out.
        final String send = event("", "<TypeId>Send-Mail</TypeId><Description>Sent</Description><Send><Source>"
                                      + "<User><Id>kate</Id></User></Source><Destination><User><Id>anja</Id>"
                                      + "</User></Destination>" + data("Subject", "Re: roster")
                                      + "<Outcome><Success>true</Success></Outcome></Send>");
        final String rule = event("", "<TypeId>Update-InboxRule</TypeId><Update><After><Object>"
                                      + data("Operation", "New-InboxRule") + "<Data Name=\"Parameters\">"
                                      + data("Name", "FW to personal") + data("MoveToFolder", "Archive")
                                      + "</Data></Object></After></Update>");
        final Map<String, Object> expected = Map.of("records", 3, "data_share_at_most", 0.5);

        final EvalScore score = new EvalScore("c");
        score.scoreEvents(expecting(expected), List.of(send, rule, unknown("x")), List.of(true, true, true), null);

        assertThat(score.toMap().get("data_shares")).isEqualTo(Map.of(
                "Send-Mail", Map.of("share", 0.25, "data", 1, "values", 4),
                "Update-InboxRule", Map.of("share", 1.0, "data", 3, "values", 3)));
        assertThat(score.toMap().get("data_share")).isEqualTo(Map.of("share", 0.57, "data", 4, "values", 7));
        assertThat(score.stage1()).isFalse();
        assertThat((List<String>) score.toMap().get("problems")).containsExactly(
                "the events hold 0.57 of their EventDetail values in Data (4 of 7), at most 0.5 expected");
        assertThat(score.summary()).contains(" data_share=0.57");

        // Under the cap, the shares are still reported.
        final EvalScore passing = new EvalScore("c");
        passing.scoreEvents(expecting(Map.of("records", 1, "data_share_at_most", 0.3)), List.of(send),
                List.of(true), null);
        assertThat(passing.stage1()).isTrue();
        assertThat(passing.toMap().get("data_share")).isEqualTo(Map.of("share", 0.25, "data", 1, "values", 4));
        assertThat((List<Map<String, Object>>) passing.toMap().get("typed_checks"))
                .containsExactly(Map.of("check", "data share at most 0.3", "passed", true));

        // A case expecting neither has no shares in its score.
        final EvalScore plain = new EvalScore("c");
        plain.scoreEvents(expecting(Map.of("records", 1)), List.of(send), List.of(true), null);
        assertThat(plain.toMap()).doesNotContainKeys("typed_checks", "data_share", "data_shares");
    }

    @Test
    void anEntrysDataShareIsOfTheEventsItSelects() {
        final String send = event("", "<TypeId>Send-Mail</TypeId><Send><Source><User><Id>kate</Id></User>"
                                      + "</Source>" + data("Operation", "Send") + "</Send>");
        final String rule = event("", "<TypeId>Update-InboxRule</TypeId><Update><After><Object><Name>FW</Name>"
                                      + "</Object></After>" + data("Operation", "New-InboxRule")
                                      + data("MoveToFolder", "Archive") + "</Update>");
        final Map<String, Object> expected = Map.of("records", 2, "typed", List.of(
                Map.of("select", Map.of("text", "Send"), "paths", List.of(), "data_share_at_most", 0.5),
                Map.of("select", Map.of("text", "New-InboxRule"), "paths", List.of(), "data_share_at_most", 0.5)));

        final EvalScore score = new EvalScore("c");
        score.scoreEvents(expecting(expected), List.of(send, rule), List.of(true, true), null);

        assertThat((List<String>) score.toMap().get("problems")).containsExactly(
                "the events of {text=New-InboxRule} (TypeIds [Update-InboxRule]) hold 0.67 of their EventDetail "
                + "values in Data (2 of 3), at most 0.5 expected");
        assertThatThrownBy(() -> new EvalScore("c").scoreEvents(expecting(Map.of("records", 1,
                "data_share_at_most", 60)), List.of(send), List.of(true), null))
                .hasMessageContaining("data_share_at_most 60 is not a number from 0 to 1");
    }

    @Test
    void typedAtLeastCountsTheDistinctTypedLeafPathsTheSelectedEventsHoldBetweenThem() {
        // Two moves: the first types the source folder's Id and Path and the destination's Path, the second the
        // destination's Id; Data (named as a typed element too), the TypeId and the Description don't count.
        final String first = event("", "<TypeId>Move-MailItems</TypeId><Description>Moved</Description><Move><Source>"
                                       + "<Folder><Id>F1</Id><Path>\\Inbox</Path></Folder></Source><Destination>"
                                       + "<Folder><Path>\\Deleted Items</Path>" + data("Id", "F2") + "</Folder>"
                                       + "</Destination>" + data("Operation", "MoveToDeletedItems") + "</Move>");
        final String second = event("", "<TypeId>Move-MailItems</TypeId><Move><Source><Folder><Id>F1</Id></Folder>"
                                        + "</Source><Destination><Folder><Id>F2</Id></Folder></Destination>"
                                        + data("Operation", "MoveToDeletedItems") + "</Move>");
        final String send = event("", "<TypeId>Send-Mail</TypeId><Send><Source><User><Id>kate</Id></User></Source>"
                                      + "<Destination><User><Id>anja</Id></User></Destination>"
                                      + data("Operation", "Send") + "</Send>");

        final EvalScore enough = new EvalScore("c");
        enough.scoreEvents(expecting(Map.of("records", 3, "typed", List.of(Map.of("select",
                Map.of("text", "MoveToDeletedItems"), "paths", List.of(), "typed_at_least", 4)))),
                List.of(first, second, send), List.of(true, true, true), null);

        assertThat((List<String>) enough.toMap().get("problems")).isEmpty();
        assertThat((List<Map<String, Object>>) enough.toMap().get("typed_checks")).containsExactly(Map.of(
                "check", "typed paths {text=MoveToDeletedItems} at least 4", "passed", true));
        // Reported by TypeId, as the shares are.
        assertThat(enough.toMap().get("typed_paths")).isEqualTo(Map.of("Move-MailItems", 4, "Send-Mail", 2));

        final EvalScore tooFew = new EvalScore("c");
        tooFew.scoreEvents(expecting(Map.of("records", 3, "typed", List.of(Map.of("select",
                Map.of("text", "MoveToDeletedItems"), "paths", List.of(), "typed_at_least", 5)))),
                List.of(first, second, send), List.of(true, true, true), null);

        assertThat(tooFew.stage1()).isFalse();
        assertThat((List<String>) tooFew.toMap().get("problems")).containsExactly(
                "the events of {text=MoveToDeletedItems} (TypeIds [Move-MailItems]) hold 4 distinct typed EventDetail "
                + "leaf paths (Data doesn't count) [Move/Destination/Folder/Id, Move/Destination/Folder/Path, "
                + "Move/Source/Folder/Id, Move/Source/Folder/Path], at least 5 expected");
    }

    @Test
    void typedAtLeastMustBeAWholeNumberOfAtLeastOne() {
        final String send = event("", "<TypeId>Send-Mail</TypeId><Send><Source><User><Id>kate</Id></User>"
                                      + "</Source>" + data("Operation", "Send") + "</Send>");
        for (final Object given : List.of(0, 2.5, "3")) {
            assertThatThrownBy(() -> new EvalScore("c").scoreEvents(expecting(Map.of("records", 1, "typed",
                    List.of(Map.of("select", Map.of("text", "Send"), "paths", List.of(), "typed_at_least", given)))),
                    List.of(send), List.of(true), null))
                    .hasMessageContaining("typed_at_least " + given + " is not a whole number of at least 1");
        }
    }

    @Test
    void case56FailsAChat182StyleTranslationAndPassesOneTypedAsItsReferenceIs() {
        // Test chat 182 (2026-11-21): the Messaging group's events with almost nothing typed, the actor as View/User,
        // the Operation as Data of Update/After/Object, everything else Data; which case 56 passed before it scored
        // typed paths.
        final Map<String, Object> case56 = (Map<String, Object>) EvalCases.load("56_jsonl_azure_audit_aggregated")
                .get("expected");
        final Map<String, Object> expected = new HashMap<>();
        expected.put("records", 6);
        expected.put("typed", case56.get("typed"));

        final EvalScore chat182 = new EvalScore("56");
        chat182.scoreEvents(expecting(expected), chat182Events(), List.of(true, true, true, true, true, true), null);

        assertThat(chat182.stage1()).isFalse();
        // The share is reported, no longer capped (2026-11-22): how many elements are typed is.
        assertThat((Map<String, Object>) chat182.toMap().get("data_share"))
                .hasEntrySatisfying("share", share -> assertThat((Double) share).isGreaterThan(0.8));
        assertThat((List<Map<String, Object>>) chat182.toMap().get("typed_checks"))
                .filteredOn(check -> Boolean.FALSE.equals(check.get("passed")))
                .extracting(check -> String.valueOf(check.get("check")))
                .noneMatch(check -> check.startsWith("data share"))
                .contains("typed paths {text=MailItemsAccessed} at least 4", "typed paths {text=Send} at least 5",
                        "typed paths {text=TeamMemberAdded} at least 4")
                .anyMatch(check -> check.startsWith("typed {text=MailItemsAccessed} View/Folder/Path"))
                .anyMatch(check -> check.startsWith("typed {text=Send} Send/Payload/Email/Subject"))
                .anyMatch(check -> check.startsWith("typed {text=New-InboxRule} Create/Configuration/Name"))
                .anyMatch(check -> check.startsWith("typed {text=MoveToDeletedItems} Move/Source/Folder/Path"))
                .anyMatch(check -> check.startsWith("typed {text=TeamMemberAdded} Authorise/AddGroups/Group/Name"))
                .anyMatch(check -> check.startsWith("typed {text=TeamMemberAdded} Authorise/User/Id"));

        final EvalScore reference = new EvalScore("56");
        reference.scoreEvents(expecting(expected), referenceEvents(), List.of(true, true, true, true, true, true),
                null);

        assertThat((List<String>) reference.toMap().get("problems")).isEmpty();
        assertThat(reference.stage1()).isTrue();
        assertThat((Map<String, Object>) reference.toMap().get("data_share"))
                .hasEntrySatisfying("share", share -> assertThat((Double) share).isLessThan(0.4));

        // Haiku eval run 1 (2026-11-22): only what the schema requires placed, every other field kept as Data;
        // each event type types too few elements.
        final EvalScore run1 = new EvalScore("56");
        run1.scoreEvents(expecting(expected), run1Events(), List.of(true, true, true, true, true, true), null);

        assertThat((List<Map<String, Object>>) run1.toMap().get("typed_checks"))
                .filteredOn(check -> String.valueOf(check.get("check")).startsWith("typed paths"))
                .hasSize(6)
                .allSatisfy(check -> assertThat(check).containsEntry("passed", false));
    }

    // Case 56's records translated as Haiku's build in eval run 1 translated them (its rules' typed entries).
    private static List<String> run1Events() {
        return List.of(
                event("", "<TypeId>View-MailItems</TypeId><View><Object><Id>5a6f815d</Id></Object>"
                          + data("Operation", "MailItemsAccessed") + data("FolderPath", "\\Drafts") + "</View>"),
                event("", "<TypeId>Send-Email</TypeId><Send><Source><User><Id>kate.jones@contoso.example</Id>"
                          + "</User></Source><Destination><User><Id>anja.michel@contoso.example</Id></User>"
                          + "</Destination>" + data("Operation", "Send") + data("Subject", "Re: roster") + "</Send>"),
                event("", "<TypeId>Create-InboxRule</TypeId><Create><Object><Id>357dcfb8</Id></Object>"
                          + data("Operation", "New-InboxRule") + "</Create>"),
                event("", "<TypeId>Move-ToDeletedItems</TypeId><Move><Source><Folder><Id>AAMkF0B8</Id></Folder>"
                          + "</Source><Destination><Folder><Id>AAMkEEEF</Id></Folder></Destination>"
                          + data("Operation", "MoveToDeletedItems") + data("FolderPath", "\\Inbox") + "</Move>"),
                event("", "<TypeId>Send-TeamsMessage</TypeId><Send><Source><User><Id>anja.michel@contoso.example</Id>"
                          + "</User></Source><Destination><User><Id>kate.jones@contoso.example</Id></User>"
                          + "</Destination>" + data("Operation", "MessageSent") + "</Send>"),
                event("", "<TypeId>Authorise-TeamMemberAdded</TypeId><Authorise><Action>Modify</Action><Group>"
                          + "<Id>0b9a8c7d</Id></Group>" + data("Operation", "TeamMemberAdded") + "</Authorise>"));
    }

    // Case 56's records translated as test chat 182 translated them.
    private static List<String> chat182Events() {
        return List.of(
                event("", "<TypeId>View-MailItems</TypeId><View><User><Id>li.wei@contoso.example</Id>"
                          + "<EmailAddress>li.wei@contoso.example</EmailAddress></User>"
                          + data("Operation", "MailItemsAccessed") + data("MailboxOwnerUPN", "li.wei@contoso.example")
                          + data("ClientInfoString", "Client=REST;Outlook") + data("LogonType", "1")
                          + data("ExternalAccess", "false") + data("MailboxGuid", "5a6f815d")
                          + data("MailAccessType", "Sync") + data("IsThrottled", "False")
                          + data("FolderId", "AAMk32B9") + data("FolderPath", "\\Drafts")
                          + data("InternetMessageId", "<37fd1f4c@contoso.example>") + data("SizeInBytes", "315880")
                          + data("InternetMessageId", "<0fa6dea5@contoso.example>") + data("SizeInBytes", "55909")
                          + data("OperationCount", "3") + data("RecordType", "25") + data("UserType", "0")
                          + "</View>"),
                event("", "<TypeId>Send-Mail</TypeId><Send><Source><User><Id>kate.jones@contoso.example</Id></User>"
                          + "</Source><Destination><User><Id>anja.michel@contoso.example</Id></User><User>"
                          + "<Id>accounts@fabrikam.example</Id></User></Destination><Payload><Email>"
                          + data("Subject", "Re: roster") + data("SizeInBytes", "483614")
                          + data("Id", "AAMk9328") + data("InternetMessageId", "<f8ce0925@contoso.example>")
                          + data("ParentFolderId", "AAMk90A1") + data("ParentFolderPath", "\\Sent Items")
                          + data("BodyContentType", "HTML") + "</Email></Payload>"
                          + data("Operation", "Send") + data("MailboxOwnerUPN", "kate.jones@contoso.example")
                          + data("ClientInfoString", "Client=OWA") + data("LogonType", "1")
                          + data("ExternalAccess", "false") + data("MailboxGuid", "3ddbd4ed")
                          + data("RecordType", "50") + data("UserType", "0") + "</Send>"),
                event("", "<TypeId>Update-InboxRule</TypeId><Update><After><Object>"
                          + data("Operation", "New-InboxRule") + data("MailboxOwnerUPN", "sam.taylor@contoso.example")
                          + data("Name", "FW to personal") + data("SubjectContainsWords", "invoice;payment")
                          + data("MoveToFolder", "Conversation History") + data("StopProcessingRules", "True")
                          + data("ClientInfoString", "Client=REST;Outlook") + data("MailboxGuid", "357dcfb8")
                          + "</Object></After></Update>"),
                event("", "<TypeId>Move-MailToDeletedItems</TypeId><Update><After><Object>"
                          + data("Operation", "MoveToDeletedItems") + data("FolderPath", "\\Inbox")
                          + data("DestFolderPath", "\\Deleted Items") + data("FolderId", "AAMkF0B8")
                          + data("DestFolderId", "AAMkEEEF") + data("Subject", "FW: Change freeze & CAB notes")
                          + data("InternetMessageId", "<0ca2b8f1@contoso.example>")
                          + "</Object></After></Update>"),
                event("", "<TypeId>Send-TeamsMessage</TypeId><Send><Source><User>"
                          + "<Id>anja.michel@contoso.example</Id></User></Source>"
                          + data("Operation", "MessageSent") + data("Recipients", "kate.jones@contoso.example")
                          + data("TeamName", "Ops") + data("ChannelName", "General") + data("MessageId", "6100214")
                          + data("ChatThreadId", "19:b66f@thread.tacv2") + data("CommunicationType", "Team")
                          + "</Send>"),
                event("", "<TypeId>Update-TeamMemberAdded</TypeId><Update><After><Object>"
                          + data("Operation", "TeamMemberAdded") + data("TeamName", "Finance")
                          + data("TeamGuid", "0b9a8c7d") + data("ChannelName", "General")
                          + data("MemberUPN", "li.wei@contoso.example") + data("MemberDisplayName", "Li Wei")
                          + data("MemberRole", "2") + data("MemberUPN", "svc-batch@contoso.example")
                          + "</Object></After></Update>"));
    }

    // Case 56's records translated as its reference mapping translates them.
    private static List<String> referenceEvents() {
        return List.of(
                event("", "<TypeId>View-MailItems</TypeId><Description>Mail items were accessed</Description><View>"
                          + "<Folder><Id>AAMk32B9</Id><Path>\\Drafts</Path><Type>Mail folder</Type></Folder>"
                          + "<Email><Id>&lt;37fd1f4c@contoso.example&gt;</Id></Email><User>"
                          + "<EmailAddress>li.wei@contoso.example</EmailAddress></User>"
                          + data("Operation", "MailItemsAccessed") + data("MailAccessType", "Sync") + "</View>"),
                event("", "<TypeId>Send-Mail</TypeId><Description>A message was sent</Description><Send><Source>"
                          + "<User><EmailAddress>kate.jones@contoso.example</EmailAddress></User><Device>"
                          + "<IPAddress>203.0.113.57</IPAddress></Device></Source>"
                          + "<Destination><User><EmailAddress>anja.michel@contoso.example</EmailAddress></User><User>"
                          + "<EmailAddress>accounts@fabrikam.example</EmailAddress></User></Destination><Payload>"
                          + "<Email><Type>Email</Type><Id>&lt;f8ce0925@contoso.example&gt;</Id><From>"
                          + "<EmailAddress>kate.jones@contoso.example</EmailAddress></From>"
                          + "<Subject>Re: roster</Subject>"
                          + data("SizeInBytes", "483614") + "</Email></Payload>" + data("Operation", "Send")
                          + "</Send>"),
                event("", "<TypeId>Create-InboxRule</TypeId><Description>An inbox rule was created</Description>"
                          + "<Create><Configuration><Type>Inbox rule</Type><Name>FW to personal</Name>"
                          + data("SubjectContainsWords", "invoice;payment") + "</Configuration>"
                          + data("Operation", "New-InboxRule") + "</Create>"),
                event("", "<TypeId>Move-MailItems</TypeId><Description>Messages were moved to Deleted Items"
                          + "</Description><Move><Source><Folder><Id>AAMkF0B8</Id><Path>\\Inbox</Path></Folder>"
                          + "</Source><Destination><Folder><Id>AAMkEEEF</Id>"
                          + "<Path>\\Deleted Items</Path></Folder></Destination>"
                          + data("Operation", "MoveToDeletedItems") + "</Move>"),
                event("", "<TypeId>Send-TeamsMessage</TypeId><Description>A message was posted in Teams</Description>"
                          + "<Send><Source><User><EmailAddress>anja.michel@contoso.example</EmailAddress></User>"
                          + "</Source><Destination><User><EmailAddress>kate.jones@contoso.example</EmailAddress>"
                          + "</User></Destination><Payload><Chat><Id>6100214</Id><Room>General</Room></Chat></Payload>"
                          + data("Operation", "MessageSent") + data("team", "Ops") + "</Send>"),
                event("", "<TypeId>Add-TeamMember</TypeId><Description>A member was added to a team</Description>"
                          + "<Authorise><User><Id>li.wei@contoso.example</Id><Name>Li Wei</Name></User>"
                          + "<Action>Modify</Action><AddGroups><Group><Id>0b9a8c7d</Id><Name>Finance</Name></Group>"
                          + "</AddGroups>" + data("Operation", "TeamMemberAdded") + "<Data Name=\"member\">"
                          + data("UPN", "li.wei@contoso.example") + "</Data><Data Name=\"member\">"
                          + data("UPN", "svc-batch@contoso.example") + "</Data></Authorise>"));
    }

    // A Data element of the name and value (the value's XML escaped).
    private static String data(final String name, final String value) {
        return "<Data Name=\"" + name + "\" Value=\"" + value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;") + "\"/>";
    }

    // A catalogue as the source notes' JSON block holds it: a logon recognised two ways, a logoff, an unlock from the
    // documentation with no record in the sample, a dropped heartbeat.
    private static Map<String, Object> catalogue() {
        final List<Map<String, Object>> types = new ArrayList<>();
        types.add(eventType("Authenticate-Logon", "documented", List.of(
                Map.of("field", "eventType", "equals", "user.session.start"),
                Map.of("field", "type", "equals", "login")), "user_confirmed", false));
        types.add(eventType("Authenticate-Logoff", "documented", List.of(
                Map.of("field", "type", "one_of", List.of("logout", "timeout"))), "user_confirmed", false));
        types.add(eventType("Unlock-Account", "documented", List.of(
                Map.of("field", "type", "equals", "unlock")), "documentation", false));
        types.add(eventType("Process-Heartbeat", "documented", List.of(
                Map.of("field", "type", "equals", "heartbeat")), "user_confirmed", true));
        final Map<String, Object> notes = new LinkedHashMap<>();
        notes.put("event_types", types);
        notes.put("catch_all", Map.of("status", "confirmed"));
        notes.put("scope", Map.of("source_type_field", "event>log_type", "source_types", List.of("Integration")));
        return notes;
    }

    // An event type recognised one way for each condition given.
    private static Map<String, Object> eventType(final String typeId,
                                                 final String description,
                                                 final List<Map<String, Object>> ways,
                                                 final String basis,
                                                 final boolean drop) {
        final List<Map<String, Object>> recognisedBy = new ArrayList<>();
        ways.forEach(condition -> recognisedBy.add(Map.of("when", List.of(condition), "names", List.of())));
        final Map<String, Object> type = new LinkedHashMap<>();
        type.put("type_id", typeId);
        type.put("title", typeId);
        type.put("description", description);
        type.put("recognised_by", recognisedBy);
        type.put("drop", drop);
        type.put("basis", basis);
        type.put("status", "confirmed");
        return type;
    }

    // The catalogue's check: every type's records counted (none for the documented unlock), and the overlaps given.
    private static Map<String, Object> check(final List<Map<String, Object>> overlaps) {
        return Map.of("counts", Map.of("Authenticate-Logon", Map.of("records", 2, "by_way", List.of(1, 1)),
                        "Authenticate-Logoff", Map.of("records", 1, "by_way", List.of(1)),
                        "Process-Heartbeat", Map.of("records", 3, "by_way", List.of(3))),
                "overlaps", overlaps, "uncatalogued", Map.of("records", 0, "shapes", List.of()));
    }

    // An event of a TypeId, its Data under Authenticate.
    private static String typed(final String typeId, final String data) {
        return event("", "<TypeId>" + typeId + "</TypeId><Description>d</Description><Authenticate>"
                         + "<Action>Logon</Action>" + data
                         + "</Authenticate>");
    }

    // A record no event type recognised, translated as Unknown.
    private static String unknown(final String typeId) {
        return event("", "<TypeId>Unknown-" + typeId.toUpperCase(Locale.ROOT).charAt(0)
                         + typeId.substring(1) + "</TypeId><Unknown><Data Name=\"x\" Value=\"y\"/></Unknown>");
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

    @Test
    void aQuestionAnsweredWithTheExpectedRowsPasses() {
        final EvalScore score = new EvalScore("q");

        score.scoreQuestion(question(true), List.of(
                new EvalScore.ToolUse("describe_data_source", false, Map.of(), "fields"),
                new EvalScore.ToolUse("esql_query", true, Map.of(), "Error: Unknown column [usr]"),
                new EvalScore.ToolUse("esql_query", false, Map.of("rows", List.of(
                        List.of(300, "alice", "box.com"), List.of("bob", 200.0, "github.com")),
                        "truncated", true, "total_groups", 12), "ok")));

        assertThat(score.passed()).as(score.summary()).isTrue();
        assertThat(score.toMap()).containsKey("question_checks");
        assertThat((List<String>) score.toMap().get("notes")).contains("esql_query: 1 of 2 call(s) refused");
    }

    @Test
    void aQuestionsAnswerIsAnAcceptedCallsRowsInOrder() {
        final EvalScore wrongOrder = new EvalScore("q");
        wrongOrder.scoreQuestion(question(true), List.of(new EvalScore.ToolUse("esql_query", false, Map.of("rows",
                List.of(List.of("bob", "github.com", 200), List.of("alice", "box.com", 300)), "total_groups", 12),
                "ok")));
        assertThat(wrongOrder.passed()).isFalse();
        assertThat(wrongOrder.summary()).contains("row 1 was [bob, github.com, 200]");

        // In any order when the case says so.
        final EvalScore anyOrder = new EvalScore("q");
        anyOrder.scoreQuestion(question(false), List.of(new EvalScore.ToolUse("esql_query", false, Map.of("rows",
                List.of(List.of("bob", "github.com", 200), List.of("alice", "box.com", 300)), "total_groups", 12),
                "ok")));
        assertThat(anyOrder.passed()).as(anyOrder.summary()).isTrue();
    }

    @Test
    void aQuestionFailsWithoutTheToolOrWhenEveryCallIsRefused() {
        final EvalScore other = new EvalScore("q");
        other.scoreQuestion(question(true), List.of(new EvalScore.ToolUse("run_stroomql", false, Map.of(), "ok")));
        assertThat(other.passed()).isFalse();
        assertThat(other.summary()).contains("esql_query was not used");

        final EvalScore refused = new EvalScore("q");
        refused.scoreQuestion(question(true), List.of(new EvalScore.ToolUse("esql_query", true, Map.of(),
                "Error: FROM isn't allowed")));
        assertThat(refused.summary())
                .contains("every esql_query call was refused, the last: Error: FROM isn't allowed");

        final EvalScore total = new EvalScore("q");
        total.scoreQuestion(question(true), List.of(new EvalScore.ToolUse("esql_query", false, Map.of("rows",
                List.of(List.of("alice", "box.com", 300), List.of("bob", "github.com", 200)), "truncated", true),
                "ok")));
        assertThat(total.summary()).contains("the total groups were null, expected 12");
    }

    @Test
    void anEarlierAcceptedCallThatMatchesIsTheAnswerWhateverCameAfter() {
        // As case 62 on Haiku: the answer from the second call, then a count of the pairs, which gives no
        // total_groups and one row.
        final EvalScore score = new EvalScore("q");
        score.scoreQuestion(question(true), List.of(
                new EvalScore.ToolUse("esql_query", false, Map.of("rows", List.of(List.of(9021, "allowed")),
                        "truncated", false), "ok"),
                new EvalScore.ToolUse("esql_query", false, Map.of("rows", List.of(
                        List.of("alice", "box.com", 300), List.of("bob", "github.com", 200)),
                        "truncated", true, "total_groups", 12), "ok"),
                new EvalScore.ToolUse("esql_query", true, Map.of(), "Error: Unknown column [pair]"),
                new EvalScore.ToolUse("esql_query", false, Map.of("rows", List.of(List.of(12)),
                        "truncated", false), "ok")));

        assertThat(score.passed()).as(score.summary()).isTrue();
        assertThat((List<String>) score.toMap().get("notes"))
                .contains("esql_query: 1 of 4 call(s) refused", "answer rows from esql_query call 2 of 4");
    }

    @Test
    void whenNoCallMatchesTheProblemIsSaidOfTheClosestCall() {
        // The second call holds one of the two rows; the last, a check afterwards, none.
        final EvalScore score = new EvalScore("q");
        score.scoreQuestion(question(true), List.of(
                new EvalScore.ToolUse("esql_query", false, Map.of("rows", List.of(List.of("carol", "x.com", 1))),
                        "ok"),
                new EvalScore.ToolUse("esql_query", false, Map.of("rows", List.of(
                        List.of("alice", "box.com", 300), List.of("bob", "github.com", 201)),
                        "total_groups", 12), "ok"),
                new EvalScore.ToolUse("esql_query", false, Map.of("rows", List.of(List.of(12))), "ok")));

        assertThat(score.passed()).isFalse();
        assertThat(score.summary()).contains("the answer's rows row 2 was [bob, github.com, 201]");
        assertThat((List<String>) score.toMap().get("notes"))
                .contains("no esql_query call matched; the closest, call 2 of 3, held 1 of 2 expected rows");
    }

    @Test
    void theTotalGroupsMayComeFromAnotherAcceptedCall() {
        // The answer call cut short without a total; a later call over the same groups gives it.
        final EvalScore score = new EvalScore("q");
        score.scoreQuestion(question(true), List.of(
                new EvalScore.ToolUse("esql_query", false, Map.of("rows", List.of(
                        List.of("alice", "box.com", 300), List.of("bob", "github.com", 200)),
                        "truncated", true), "ok"),
                new EvalScore.ToolUse("esql_query", false, Map.of("rows", List.of(List.of("alice", "box.com", 300)),
                        "truncated", true, "total_groups", 12), "ok")));
        assertThat(score.passed()).as(score.summary()).isTrue();

        // None gives the expected total: every total given is said.
        final EvalScore wrong = new EvalScore("q");
        wrong.scoreQuestion(question(true), List.of(
                new EvalScore.ToolUse("esql_query", false, Map.of("rows", List.of(
                        List.of("alice", "box.com", 300), List.of("bob", "github.com", 200)),
                        "truncated", true, "total_groups", 11), "ok"),
                new EvalScore.ToolUse("esql_query", false, Map.of("rows", List.of(List.of(1)),
                        "truncated", true, "total_groups", 40), "ok")));
        assertThat(wrong.passed()).isFalse();
        assertThat(wrong.summary()).contains("the total groups were [11, 40], expected 12");
    }

    @Test
    void anAnswerWorkedOutInTheReplyAndNotTheQueryFails() {
        // As case 64 on Haiku: the bytes summed for every user, the MB and the cut-off left to the reply.
        final Map<String, Object> mb = Map.of("kind", "question", "expected", Map.of("answer", Map.of(
                "ordered", true, "rows", List.of(List.of("erin", 397.1), List.of("carol", 245.2)))));
        final EvalScore score = new EvalScore("q");
        score.scoreQuestion(mb, List.of(
                new EvalScore.ToolUse("esql_query", false, Map.of("rows", List.of(List.of(1446, "files.example.com")),
                        "truncated", false), "ok"),
                new EvalScore.ToolUse("esql_query", false, Map.of("rows", List.of(
                        List.of(397114256, 826, "erin"), List.of(245201352, 1017, "carol"),
                        List.of(111835605, 902, "dave")), "truncated", false), "ok")));

        assertThat(score.passed()).isFalse();
        assertThat(score.summary()).contains("the answer's rows were 3, expected 2");
        assertThat((List<String>) score.toMap().get("notes")).noneMatch(note -> note.startsWith("answer rows from"));

        // Converted and cut in the query, it passes.
        final EvalScore inQuery = new EvalScore("q");
        inQuery.scoreQuestion(mb, List.of(new EvalScore.ToolUse("esql_query", false, Map.of("rows", List.of(
                List.of("erin", 397.1), List.of("carol", 245.2)), "truncated", false), "ok")));
        assertThat(inQuery.passed()).as(inQuery.summary()).isTrue();
    }

    @Test
    void valuesMatchAsDaysAndRoundedNumbers() {
        assertThat(EvalScore.mismatch(List.of(List.of("2026-01-02", "box.com", 52.8)),
                List.of(List.of("2026-01-02T00:00:00.000Z", "box.com", 52.83)), true, 0.001)).isNull();
        assertThat(EvalScore.mismatch(List.of(List.of("2026-01-02", 52.8)),
                List.of(List.of("2026-01-02T00:00:00.000Z", 52.9)), true, 0.001)).isNotNull();
        assertThat(EvalScore.mismatch(List.of(List.of(1000)), List.of(List.of(1000), List.of(5)), false, 0.001))
                .startsWith("were 2, expected 1");
    }

    @Test
    void aJoinDoneByHandAndNotInTheQueryFails() {
        // As a by-hand anti-join: the leavers read from HR first, then their names written into the query.
        final Map<String, Object> leavers = Map.of("kind", "question", "expected", Map.of(
                "tools_used", List.of("run_stroomql"),
                "answer", Map.of("tool", "run_stroomql", "query", "\\bjoin\\b|\\(\\s*from\\b",
                        "rows", List.of(List.of("dave", 3), List.of("gina", 1)))));
        final List<List<Object>> rows = List.of(List.of("dave", "3"), List.of("gina", "1"));

        final EvalScore byHand = new EvalScore("q");
        byHand.scoreQuestion(leavers, List.of(
                new EvalScore.ToolUse("run_stroomql", false, Map.of("query", "from HR where Status = Leaver "
                                                                             + "select Username",
                        "rows", List.of(List.of("dave"), List.of("frank"), List.of("gina"), List.of("ivan"))), "ok"),
                new EvalScore.ToolUse("run_stroomql", false, Map.of("query", "from \"Staff activity\" "
                                                                             + "where UserId in (dave, gina) "
                                                                             + "group by UserId select UserId, "
                                                                             + "count()", "rows", rows), "ok")));
        assertThat(byHand.passed()).isFalse();
        assertThat(byHand.summary()).contains("the answer's query doesn't match /\\bjoin\\b|\\(\\s*from\\b/: "
                                              + "from \"Staff activity\" where UserId in (dave, gina)");

        // The same rows from a subquery pass, whatever the case of its words.
        final EvalScore inQuery = new EvalScore("q");
        inQuery.scoreQuestion(leavers, List.of(new EvalScore.ToolUse("run_stroomql", false, Map.of("query",
                "from \"Staff activity\" where UserId in (FROM HR where Status = Leaver select Username) "
                + "group by UserId select UserId, count()", "rows", rows), "ok")));
        assertThat(inQuery.passed()).as(inQuery.summary()).isTrue();
    }

    @Test
    void theAnswerRowsMayComeFromATableCallOnTheFileTheQueryWrote() {
        // As case 72 on Sonnet: a join grouped by user and action, its logon rows then kept with table.
        final List<List<Object>> grouped = List.of(List.of("gina", "Read", "6"), List.of("dave", "Logon", "3"),
                List.of("frank", "Read", "5"), List.of("gina", "Logon", "1"));
        final List<List<Object>> logons = List.of(List.of("dave", "Logon", "3"), List.of("gina", "Logon", "1"));
        final String join = "from \"Staff activity\" join HR as hr on UserId = hr.Username where hr.Status = "
                            + "Leaver group by UserId, Action select UserId, Action, count() as N";

        final EvalScore score = new EvalScore("q");
        score.scoreQuestion(leavers(), List.of(
                stroomQl("from HR select Username", "/outputs/queries/q1.md", List.of(List.of("dave"))),
                stroomQl(join, "/outputs/queries/q2.md", grouped),
                table("/outputs/queries/q2.md", logons)));

        assertThat(score.passed()).as(score.summary()).isTrue();
        assertThat((List<String>) score.toMap().get("notes"))
                .contains("answer rows from a table call on run_stroomql call 2 of 2");
    }

    @Test
    void aTableCallOnAnotherFileOrBeforeTheQueryGivesNoAnswerRows() {
        final List<List<Object>> grouped = List.of(List.of("dave", "Logon", "3"), List.of("frank", "Read", "5"),
                List.of("gina", "Logon", "1"));
        final List<List<Object>> logons = List.of(List.of("dave", "Logon", "3"), List.of("gina", "Logon", "1"));
        final String join = "from \"Staff activity\" join HR as hr on UserId = hr.Username select UserId";

        final EvalScore otherFile = new EvalScore("q");
        otherFile.scoreQuestion(leavers(), List.of(stroomQl(join, "/outputs/queries/q2.md", grouped),
                table("/outputs/queries/other.md", logons)));
        assertThat(otherFile.passed()).isFalse();
        assertThat(otherFile.summary()).contains("the answer's rows were 3, expected 2");

        final EvalScore before = new EvalScore("q");
        before.scoreQuestion(leavers(), List.of(table("/outputs/queries/q2.md", logons),
                stroomQl(join, "/outputs/queries/q2.md", grouped)));
        assertThat(before.passed()).isFalse();

        // A later query that wrote the same file: the table call after it is that query's.
        final EvalScore rewritten = new EvalScore("q");
        rewritten.scoreQuestion(leavers(), List.of(stroomQl(join, "/outputs/queries/q2.md", grouped),
                stroomQl("from \"Staff activity\" select UserId", "/outputs/queries/q2.md", grouped),
                table("/outputs/queries/q2.md", logons)));
        assertThat(rewritten.passed()).isFalse();
        assertThat(rewritten.summary()).contains("the answer's query doesn't match");
    }

    @Test
    void theLatestTableCallOnTheFileIsTheOneThatCounts() {
        final List<List<Object>> grouped = List.of(List.of("dave", "Logon", "3"), List.of("frank", "Read", "5"),
                List.of("gina", "Logon", "1"));
        final List<List<Object>> logons = List.of(List.of("dave", "Logon", "3"), List.of("gina", "Logon", "1"));
        final String join = "from \"Staff activity\" join HR as hr on UserId = hr.Username select UserId";

        final EvalScore score = new EvalScore("q");
        score.scoreQuestion(leavers(), List.of(stroomQl(join, "/outputs/queries/q2.md", grouped),
                table("/outputs/queries/q2.md", logons),
                table("/outputs/queries/q2.md", List.of(List.of("frank", "Read", "5")))));

        assertThat(score.passed()).isFalse();
        assertThat(score.summary()).contains("the answer's rows were 1, expected 2");
    }

    @Test
    void aJoinDoneByHandFailsWhateverATableCallOnItGives() {
        final EvalScore score = new EvalScore("q");
        score.scoreQuestion(leavers(), List.of(
                stroomQl("from \"Staff activity\" where Action = Logon group by UserId select UserId, count()",
                        "/outputs/queries/q1.md", List.of(List.of("dave", "3"), List.of("bob", "1"))),
                table("/outputs/queries/q1.md", List.of(List.of("dave", "3"), List.of("gina", "1")))));

        assertThat(score.passed()).isFalse();
        assertThat(score.summary()).contains("the answer's query doesn't match");
        assertThat((List<String>) score.toMap().get("notes"))
                .contains("answer rows from a table call on run_stroomql call 1 of 1");
    }

    @Test
    void anAnswerFromRowsCutAtTheRowLimitFails() {
        // Case 75's question: the exports by department, over more rows than a query may return.
        final Map<String, Object> exports = Map.of("kind", "question", "expected", Map.of("answer", Map.of(
                "tool", "run_stroomql", "uncapped", true, "rows", List.of(List.of("Engineering", 100)))));
        final EvalScore.ToolUse capped = new EvalScore.ToolUse("run_stroomql", false, Map.of("query",
                "from \"Staff activity\" where Action = Export select UserId, Files", "capped", true,
                "rows", List.of(List.of("user00001", "3"))), "ok");

        final EvalScore byHand = new EvalScore("q");
        byHand.scoreQuestion(exports, List.of(capped));
        assertThat(byHand.passed()).isFalse();
        assertThat(byHand.summary()).contains("the answer's rows were cut at the row limit");
        assertThat((List<String>) byHand.toMap().get("notes"))
                .contains("run_stroomql: 1 of 1 accepted call(s) cut at the row limit");

        // A capped call on the way to a whole answer is noted, not failed.
        final EvalScore joined = new EvalScore("q");
        joined.scoreQuestion(exports, List.of(capped, new EvalScore.ToolUse("run_stroomql", false, Map.of("query",
                "from \"Staff activity\" join HR as hr on UserId = hr.Username", "capped", false,
                "rows", List.of(List.of("Engineering", "100"))), "ok")));
        assertThat(joined.passed()).as(joined.summary()).isTrue();
        assertThat((List<String>) joined.toMap().get("notes"))
                .contains("run_stroomql: 1 of 2 accepted call(s) cut at the row limit");
    }

    @Test
    void aTableResultIsReadAsItsMarkdownTable() {
        final Map<String, Object> table = AbstractEvalTest.markdownTable("""
                2 of 11 rows matched; the result has 2 row(s).

                | UserId | Action | N |
                | --- | --- | --- |
                | dave | Logon | 3 |
                | gina | Logon | 1 |
                """);

        assertThat(table).containsEntry("columns", List.of("UserId", "Action", "N"))
                .containsEntry("rows", List.of(List.of("dave", "Logon", "3"), List.of("gina", "Logon", "1")));
        assertThat(AbstractEvalTest.markdownTable("No rows matched.")).containsEntry("rows", List.of());
    }

    // Case 72's question: the leavers who logged on, and how often, joined in the query.
    private static Map<String, Object> leavers() {
        return Map.of("kind", "question", "expected", Map.of(
                "tools_used", List.of("run_stroomql"),
                "answer", Map.of("tool", "run_stroomql", "query", "\\bjoin\\b|\\(\\s*from\\b",
                        "rows", List.of(List.of("dave", 3), List.of("gina", 1)))));
    }

    private static EvalScore.ToolUse stroomQl(final String query, final String out, final List<List<Object>> rows) {
        return new EvalScore.ToolUse("run_stroomql", false, Map.of("query", query, "out", out, "rows", rows), "ok");
    }

    private static EvalScore.ToolUse table(final String path, final List<List<Object>> rows) {
        return new EvalScore.ToolUse("table", false, Map.of("path", path, "rows", rows), "ok");
    }

    @Test
    void theAnswerCallMustHoldTheValuesTheCaseGives() {
        // A sequence on a Lucene index runs in Stroom as a windowed join, not EQL.
        final Map<String, Object> sequence = Map.of("kind", "question", "expected", Map.of("answer", Map.of(
                "tool", "sequence_query", "result", Map.of("engine", "StroomQL"),
                "rows", List.of(List.of("alice")))));

        final EvalScore eql = new EvalScore("q");
        eql.scoreQuestion(sequence, List.of(new EvalScore.ToolUse("sequence_query", false, Map.of("engine", "EQL",
                "rows", List.of(List.of("alice", "2026-01-06T09:00:00.000Z"))), "ok")));
        assertThat(eql.passed()).isFalse();
        assertThat(eql.summary()).contains("the answer's engine was EQL, expected StroomQL");

        final EvalScore windowed = new EvalScore("q");
        windowed.scoreQuestion(sequence, List.of(new EvalScore.ToolUse("sequence_query", false, Map.of("engine",
                "StroomQL", "rows", List.of(List.of("alice", "2026-01-06T09:00:00.000Z"))), "ok")));
        assertThat(windowed.passed()).as(windowed.summary()).isTrue();
    }

    private static Map<String, Object> question(final boolean ordered) {
        return Map.of("kind", "question", "expected", Map.of("tools_used", List.of("esql_query"), "answer",
                Map.of("ordered", ordered, "total_groups", 12, "rows", List.of(
                        List.of("alice", "box.com", 300), List.of("bob", "github.com", 200)))));
    }
}
