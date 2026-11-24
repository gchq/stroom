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

package stroom.ai.impl.tools.timeline;

import stroom.ai.impl.tools.timeline.EventXmlClassifier.Classified;
import stroom.ai.impl.tools.timeline.EventXmlClassifier.Route;
import stroom.ai.impl.tools.timeline.TimelineSessions.By;
import stroom.ai.impl.tools.timeline.TimelineSessions.Event;
import stroom.ai.impl.tools.timeline.TimelineSessions.FlagName;
import stroom.ai.impl.tools.timeline.TimelineSessions.Options;
import stroom.ai.impl.tools.timeline.TimelineSessions.Role;
import stroom.ai.impl.tools.timeline.TimelineSessions.Timeline;
import stroom.ai.impl.tools.timeline.TimelineSpans.Built;
import stroom.ai.impl.tools.timeline.TimelineSpans.Kind;
import stroom.ai.impl.tools.timeline.TimelineSpans.Span;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/// The spans beyond the focus sessions: pairs from a start to its end, and activity periods of the actions in
/// neither, by lane and label.
class TestTimelineSpans {

    private static final long CAP = 30 * 60_000L;
    private static final long PERIOD_END = at("18:00");

    private final List<Event> events = new ArrayList<>();
    private final Map<String, Classified> classified = new LinkedHashMap<>();
    private int ids;

    @Test
    void anEndClosesTheLatestOpenStartOfItsKey() {
        process("09:00", "Execute", "11", "WINWORD.EXE");
        process("09:10", "Execute", "12", "EXCEL.EXE");
        process("09:20", "Execute", "13", "WINWORD.EXE");
        process("09:30", "Terminate", "11", "WINWORD.EXE");
        process("09:40", "Terminate", "13", "WINWORD.EXE");
        process("09:50", "Terminate", "12", "EXCEL.EXE");

        final Built built = build(PairRule.SCHEMA);

        assertThat(built.spans()).filteredOn(span -> span.kind() == Kind.PAIR)
                .extracting(Span::label, span -> iso(span.start()), span -> iso(span.end()), Span::lane)
                .containsExactly(tuple("WINWORD.EXE", "09:00", "09:30", "ws-1"),
                        tuple("EXCEL.EXE", "09:10", "09:50", "ws-1"),
                        tuple("WINWORD.EXE", "09:20", "09:40", "ws-1"));
        assertThat(built.pairs().getFirst().spans()).isEqualTo(3);
        assertThat(built.combined().count(FlagName.UNPAIRED_START)).isZero();
    }

    @Test
    void eachSpanSaysWhatHappenedAndItsLabelLeadsWithIt() {
        // Chat 195: a span's label was only its object, so it was unclear whether it was opened, focused or written.
        final PairRule focus = new PairRule(0, "Focus-Window", "Unfocus-Window", "Window", null,
                PairRule.Origin.INFERRED, 1);
        typed("09:00", "Focus-Window", Map.of("Window", "w1"), "Forecast FY27.xlsx");
        typed("09:20", "Unfocus-Window", Map.of("Window", "w1"), "Forecast FY27.xlsx");
        process("10:00", "Execute", "11", "notepad.exe");
        process("10:30", "Terminate", "11", "notepad.exe");
        process("11:00", "Execute", "12", "calc.exe");
        typed("12:00", "Read-File", Map.of(), "Budget.xlsx");
        typed("12:01", "Read-File", Map.of(), "Budget.xlsx");
        typed("12:02", "Write-File", Map.of(), "Budget.xlsx");
        typed("12:03", "Read-File", Map.of(), "Budget.xlsx");

        final List<PairRule> pairs = new ArrayList<>(List.of(focus));
        pairs.addAll(PairRule.SCHEMA);
        final Built built = build(pairs);

        assertThat(built.spans()).extracting(Span::kind, Span::shownLabel, Span::action)
                .containsExactly(
                        // The source's own type names, as one action.
                        tuple(Kind.PAIR, "Focus-Window · Forecast FY27.xlsx", "Focus-Window to Unfocus-Window"),
                        // The schema's, of one element.
                        tuple(Kind.PAIR, "Process Execute · notepad.exe", "Process Execute to Terminate"),
                        tuple(Kind.PAIR, "Process Execute · calc.exe", "Process Execute (no end seen)"),
                        // An activity period's types with their counts, the most first.
                        tuple(Kind.ACTIVITY, "Read-File/Write-File · Budget.xlsx", "Read-File ×3, Write-File ×1"));
        assertThat(built.spans().getFirst().types()).containsExactly(Map.entry("Focus-Window", 1),
                Map.entry("Unfocus-Window", 1));
        // The label without its lead is what pairs and characterisation match on.
        assertThat(built.spans().getFirst().label()).isEqualTo("Forecast FY27.xlsx");
    }

    @Test
    void aLeadNamesTwoTypesAtMostAndTypesOfNoNameAreLeftOut() {
        final Map<String, Integer> types = new LinkedHashMap<>();
        types.put("Read-File", 1);
        types.put("Write-File", 4);
        types.put("Delete-File", 2);

        assertThat(TimelineSpans.typesText(types)).isEqualTo("Write-File ×4, Delete-File ×2, Read-File ×1");
        assertThat(TimelineSpans.lead(types)).isEqualTo("Write-File/Delete-File/…");
        assertThat(TimelineSpans.lead(Map.of())).isNull();
        assertThat(TimelineSpans.typesText(Map.of())).isEmpty();
        // An event the schema classifies with no Action is named by its element; one unclassified by its kind.
        final Event event = new Event(at("09:00"), 0, "x", Role.ACTION, "view", "ws-1", null, null, null, null,
                null, null, null);
        assertThat(TimelineSpans.typeName(event, null)).isEqualTo("view");
        assertThat(TimelineSpans.typeName(event, new Classified("Search", null, null, Role.ACTION, "view", null,
                null, null, Map.of(), Route.XML))).isEqualTo("Search");
    }

    @Test
    void anUnpairedStartRunsUntilTheLaneGoesIdleAndBothUnpairedAreFlagged() {
        final PairRule tunnel = new PairRule(0, "Tun-Ascend", "Tun-Recede", "Tunnel", null, PairRule.Origin.INFERRED,
                1);
        typed("09:00", "Tun-Ascend", Map.of("Tunnel", "t1"), "vpn");
        action("09:10", "chrome.exe", null);
        action("09:25", "chrome.exe", null);
        // Nothing for over the cap: the lane went idle at 09:25 plus the cap.
        action("11:00", "chrome.exe", null);
        typed("11:05", "Tun-Recede", Map.of("Tunnel", "t9"), "vpn");

        final Built built = build(List.of(tunnel));

        assertThat(built.spans()).filteredOn(span -> span.kind() == Kind.PAIR).singleElement()
                .satisfies(span -> {
                    assertThat(iso(span.start())).isEqualTo("09:00");
                    assertThat(iso(span.end())).isEqualTo("09:55");
                    assertThat(span.mark()).startsWith("no end seen");
                });
        assertThat(built.combined().count(FlagName.UNPAIRED_START)).isEqualTo(1);
        assertThat(built.combined().count(FlagName.UNPAIRED_END)).isEqualTo(1);
    }

    @Test
    void actionsInNoSessionMakeActivityPeriodsSplitWhereTheLabelChangesOrTheLaneGoesIdle() {
        web("09:00", "https://research.example.org/a");
        web("09:05", "https://research.example.org/b");
        web("09:20", "https://news.example.com/");
        web("09:22", "https://news.example.com/x");
        // Past the cap: a new period, of one event (a marker).
        web("10:30", "https://news.example.com/y");
        lock("10:40");
        web("10:45", "https://news.example.com/z");

        final Built built = build(List.of());

        assertThat(built.spans()).filteredOn(span -> span.kind() == Kind.ACTIVITY)
                .extracting(Span::label, span -> iso(span.start()), span -> iso(span.end()), Span::lane)
                .containsExactly(tuple("research.example.org", "09:00", "09:20", "ws-1"),
                        tuple("news.example.com", "09:20", "09:22", "ws-1"),
                        tuple("news.example.com", "10:30", "10:40", "ws-1"),
                        tuple("news.example.com", "10:45", "10:45", "ws-1"));
        assertThat(built.unattached()).isZero();
        assertThat(built.actions()).allSatisfy(action -> assertThat(action.by()).isEqualTo(By.ACTIVITY));
        // As sessions, for the characterisation and the daily totals.
        assertThat(built.combined().sessions()).hasSize(4);
        assertThat(built.combined().daily()).extracting(daily -> daily.process())
                .containsExactlyInAnyOrder("research.example.org", "news.example.com");
    }

    @Test
    void anActionWithNoHostIsInItsSourcesLane() {
        events.add(new Event(at("09:00"), 1, "e" + ++ids, Role.ACTION, "message", null, null, null, null, null,
                null, null, null));

        final Built built = build(List.of());

        assertThat(built.spans()).singleElement().satisfies(span -> {
            assertThat(span.lane()).isEqualTo("Messages");
            assertThat(span.label()).isEqualTo("message");
        });
    }

    @Test
    void focusSessionsWinTheirActionsStayInThem() {
        events.add(new Event(at("09:00"), 0, "e" + ++ids, Role.FOCUS, null, "ws-1", "WINWORD.EXE", "Q3.docx",
                null, null, null, null, null));
        action("09:05", "WINWORD.EXE", null);
        web("09:06", "https://research.example.org/a");

        final Built built = build(List.of());

        assertThat(built.spans()).extracting(Span::kind).containsExactly(Kind.FOCUS);
        assertThat(built.actions()).extracting(action -> action.by()).containsOnly(By.PROCESS, By.TIME);
    }

    @Test
    void anActionInAPairsSpanWithItsLabelIsThePairs() {
        final PairRule session = new PairRule(0, "Sess-Begin", "Sess-End", "Session", null, PairRule.Origin.SKILL,
                1);
        typed("09:00", "Sess-Begin", Map.of("Session", "s1"), "https://research.example.org/");
        web("09:10", "https://research.example.org/pricing");
        web("09:12", "https://news.example.com/");
        typed("09:30", "Sess-End", Map.of("Session", "s1"), "https://research.example.org/");

        final Built built = build(List.of(session));

        assertThat(built.actions()).filteredOn(action -> "view".equals(action.event().kind()))
                .extracting(action -> action.event().object(), action -> action.by())
                .containsExactly(tuple("https://research.example.org/pricing", By.PAIR),
                        tuple("https://news.example.com/", By.ACTIVITY));
        assertThat(built.spans()).extracting(Span::kind, Span::label)
                .containsExactly(tuple(Kind.PAIR, "research.example.org"), tuple(Kind.ACTIVITY, "news.example.com"));
        // The pair's own start and end are its, so no action is left in nothing.
        assertThat(built.unattached()).isZero();
    }

    @Test
    void aSessionIdSeveralTypesShareBoundsASpanSplitWhereTheUserIsIdle() {
        // Five sessions, each a checkout, an action and a close sharing a session id; no start or end of their own
        // the schema knows, and no pair matched by it.
        final String[][] sessions = {
                {"09:00", "09:05", "09:20", "701"},
                {"10:00", "10:10", "10:15", "702"},
                {"13:00", "13:02", "13:25", "703"},
                {"14:00", "14:05", "14:10", "704"},
                {"15:00", "15:05", "15:10", "705"}};
        for (final String[] session : sessions) {
            typed(session[0], "vault-checkout", Map.of("Data[SessionId]", session[3]), "srv-db-01");
            typed(session[1], "vault-command", Map.of("Data[SessionId]", session[3]), "srv-db-01");
            typed(session[2], "vault-close", Map.of("Data[SessionId]", session[3]), "srv-db-01");
        }
        // The last session's id again after a long idle: a stretch of its own, not the session's.
        typed("16:00", "vault-command", Map.of("Data[SessionId]", "703"), "srv-db-01");
        typed("16:05", "vault-command", Map.of("Data[SessionId]", "703"), "srv-db-01");

        final List<KeySpans.KeyRule> keys = KeySpans.find(typedEvents(), List.of(), idle());
        final Built built = build(List.of(), keys);

        assertThat(keys).containsExactly(new KeySpans.KeyRule(0, "Data[SessionId]", false));
        assertThat(built.spans()).filteredOn(span -> span.kind() == Kind.KEY)
                .extracting(Span::label, span -> iso(span.start()), span -> iso(span.end()), Span::isItem)
                .containsExactly(tuple("SessionId 701", "09:00", "09:20", true),
                        tuple("SessionId 702", "10:00", "10:15", true),
                        tuple("SessionId 703", "13:00", "13:25", true),
                        tuple("SessionId 704", "14:00", "14:10", true),
                        tuple("SessionId 705", "15:00", "15:10", true),
                        tuple("SessionId 703", "16:00", "16:05", true));
        // They overlap the rest: the actions are still in activity periods.
        assertThat(built.count(Kind.ACTIVITY)).isPositive();
        assertThat(built.spans()).filteredOn(span -> span.kind() == Kind.KEY)
                .allSatisfy(span -> assertThat(span.itemId()).isEqualTo("p" + span.number()));
    }

    @Test
    void aKeyAPairIsMatchedByOrThatRecursAllDayIsntSessionLikeAndAGivenKeyIsLabelledByItsValue() {
        for (int day = 0; day < 4; day++) {
            final String hour = "%02d".formatted(9 + day * 2);
            typed(hour + ":00", "focus-in", Map.of("Data[Application]", "EXCEL.EXE", "Data[WindowHandle]",
                    "0x" + day), "Budget.xlsx");
            typed(hour + ":20", "focus-out", Map.of("Data[Application]", "EXCEL.EXE", "Data[WindowHandle]",
                    "0x" + day), "Budget.xlsx");
            typed(hour + ":25", "focus-in", Map.of("Data[Application]", "WINWORD.EXE", "Data[WindowHandle]",
                    "1x" + day), "Report.docx");
            typed(hour + ":40", "focus-out", Map.of("Data[Application]", "WINWORD.EXE", "Data[WindowHandle]",
                    "1x" + day), "Report.docx");
        }
        final PairRule focus = new PairRule(0, "focus-in", "focus-out", "Data[WindowHandle]", null,
                PairRule.Origin.INFERRED, 1);

        // The window handle is the pair's; an application recurs in a stretch a time, every time.
        assertThat(KeySpans.find(typedEvents(), List.of(focus), idle())).isEmpty();
        final Built built = build(List.of(focus), List.of(new KeySpans.KeyRule(0, "Data[Application]", true)));
        assertThat(built.spans()).filteredOn(span -> span.kind() == Kind.KEY).extracting(Span::label)
                .contains("EXCEL.EXE", "WINWORD.EXE");
        // A pair other than a session's is an item of the characterisation; a logon's isn't.
        assertThat(built.spans()).filteredOn(span -> span.kind() == Kind.PAIR).isNotEmpty()
                .allSatisfy(span -> assertThat(span.isItem()).isTrue());
        assertThat(TimelineSpans.sessionLevel(EventXmlClassifier.of("Authenticate", "Connect", null, null, null,
                null, Map.of(), Route.XML))).isTrue();
        assertThat(TimelineSpans.sessionLevel(EventXmlClassifier.of("Authenticate", "Other", null, null, null,
                null, Map.of(), Route.XML))).isFalse();
    }

    @Test
    void labelsAreTheApplicationElseTheObjectsPlaceElseTheKind() {
        assertThat(TimelineSpans.objectLabel("https://Research.Example.org/x?y=1")).isEqualTo("research.example.org");
        assertThat(TimelineSpans.objectLabel("C:\\Users\\j\\Q3 Proposal.docx")).isEqualTo("Q3 Proposal.docx");
        assertThat(TimelineSpans.objectLabel("Budget meeting")).isEqualTo("Budget meeting");
        assertThat(TimelineSpans.objectLabel(" ")).isNull();
    }

    // ------------------------------------------------------------------------------------------------

    private Built build(final List<PairRule> pairs) {
        return build(pairs, List.of());
    }

    private Built build(final List<PairRule> pairs, final List<KeySpans.KeyRule> keys) {
        final Options options = new Options(CAP, PERIOD_END, PERIOD_END, ZoneOffset.UTC, List.of("Endpoint",
                "Messages"), Set.of());
        final Timeline timeline = TimelineSessions.build(events, options);
        return TimelineSpans.build(timeline, events, classified, pairs, keys, options);
    }

    private List<PairInference.Typed> typedEvents() {
        return events.stream()
                .filter(event -> classified.containsKey(event.id()))
                .map(event -> new PairInference.Typed(event.id(), event.source(), event.time(), event.host(),
                        classified.get(event.id()).type(), classified.get(event.id()).schemaType(),
                        classified.get(event.id()).keys()))
                .toList();
    }

    private KeySpans.Idle idle() {
        return new KeySpans.Idle(events.stream().map(Event::time).toList(), CAP);
    }

    private void process(final String time, final String action, final String pid, final String command) {
        final Classified c = EventXmlClassifier.of("Process", action, null, command, null, null, Map.of("ProcessId",
                pid, "Command", command), Route.XML);
        final Event event = new Event(at(time), 0, "e" + ++ids, c.role(), c.kind(), "ws-1", command, null, pid,
                null, null, null, null);
        events.add(event);
        classified.put(event.id(), c);
    }

    private void typed(final String time, final String typeId, final Map<String, String> keys, final String label) {
        final Classified c = new Classified(null, null, typeId, Role.ACTION, typeId, null, label, null, keys,
                Route.XML);
        final Event event = new Event(at(time), 0, "e" + ++ids, Role.ACTION, typeId, "ws-1", null, null, null,
                label, null, null, null);
        events.add(event);
        classified.put(event.id(), c);
    }

    private void action(final String time, final String process, final String object) {
        events.add(new Event(at(time), 0, "e" + ++ids, Role.ACTION, "view", "ws-1", process, null, null, object,
                null, null, null));
    }

    private void web(final String time, final String url) {
        action(time, null, url);
    }

    private void lock(final String time) {
        events.add(new Event(at(time), 0, "e" + ++ids, Role.IDLE_START, null, "ws-1", null, null, null, null,
                null, null, null));
    }

    private static long at(final String time) {
        return Instant.parse("2026-11-10T" + time + ":00Z").toEpochMilli();
    }

    private static String iso(final long ms) {
        return Instant.ofEpochMilli(ms).toString().substring(11, 16);
    }
}
