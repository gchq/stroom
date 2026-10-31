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
