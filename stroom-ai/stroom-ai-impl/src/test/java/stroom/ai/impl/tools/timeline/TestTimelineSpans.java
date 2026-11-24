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
    void labelsAreTheApplicationElseTheObjectsPlaceElseTheKind() {
        assertThat(TimelineSpans.objectLabel("https://Research.Example.org/x?y=1")).isEqualTo("research.example.org");
        assertThat(TimelineSpans.objectLabel("C:\\Users\\j\\Q3 Proposal.docx")).isEqualTo("Q3 Proposal.docx");
        assertThat(TimelineSpans.objectLabel("Budget meeting")).isEqualTo("Budget meeting");
        assertThat(TimelineSpans.objectLabel(" ")).isNull();
    }

    // ------------------------------------------------------------------------------------------------

    private Built build(final List<PairRule> pairs) {
        final Options options = new Options(CAP, PERIOD_END, PERIOD_END, ZoneOffset.UTC, List.of("Endpoint",
                "Messages"), Set.of());
        final Timeline timeline = TimelineSessions.build(events, options);
        return TimelineSpans.build(timeline, events, classified, pairs, options);
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
