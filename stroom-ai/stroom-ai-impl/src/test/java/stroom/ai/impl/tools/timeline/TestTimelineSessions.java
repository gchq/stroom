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

import stroom.ai.impl.tools.timeline.TimelineSessions.Action;
import stroom.ai.impl.tools.timeline.TimelineSessions.By;
import stroom.ai.impl.tools.timeline.TimelineSessions.Daily;
import stroom.ai.impl.tools.timeline.TimelineSessions.Event;
import stroom.ai.impl.tools.timeline.TimelineSessions.FlagName;
import stroom.ai.impl.tools.timeline.TimelineSessions.Options;
import stroom.ai.impl.tools.timeline.TimelineSessions.Role;
import stroom.ai.impl.tools.timeline.TimelineSessions.Session;
import stroom.ai.impl.tools.timeline.TimelineSessions.Timeline;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/// The activity timeline's sessions ([TimelineSessions]): intervals by focus, idle roles and the cap, the period's
/// end, hosts as lanes, actions attached by process, by time and without a host, each day's totals in the user's
/// zone, and every data-quality flag on planted gaps.
class TestTimelineSessions {

    private static final long CAP = 30 * 60_000L;
    private static final int ENDPOINT = 0;
    private static final int WINDOWS = 1;
    private static final int PROXY = 2;

    private final List<Event> events = new ArrayList<>();
    private int ids;

    @Test
    void aFocusStartsAnIntervalThatTheNextFocusOrAnIdleStartEndsAndHostsAreLanesOfTheirOwn() {
        focus("ws-01", "WINWORD.EXE", "09:00", "Q3 Proposal.docx - Word");
        focus("ws-02", "OUTLOOK.EXE", "09:05", "Inbox");
        focus("ws-01", "chrome.exe", "09:10", "Search - Chrome");
        focus("ws-01", "WINWORD.EXE", "09:25", "Q3 Proposal.docx - Word");
        idle(Role.IDLE_START, "ws-02", "09:20");
        idle(Role.IDLE_START, "ws-01", "09:40");

        final Timeline timeline = build("12:00");

        assertThat(timeline.sessions()).extracting(Session::id, Session::host, Session::process, Session::endReason)
                .containsExactly(
                        tuple("s1", "ws-01", "WINWORD.EXE", "focus"),
                        tuple("s2", "ws-02", "OUTLOOK.EXE", "idle"),
                        tuple("s3", "ws-01", "chrome.exe", "focus"),
                        tuple("s4", "ws-01", "WINWORD.EXE", "idle"));
        assertThat(timeline.sessions().getFirst().activeMs()).isEqualTo(10 * 60_000L);
        assertThat(timeline.sessions().get(3).end()).isEqualTo(at("09:40"));
        assertThat(timeline.sessions().getFirst().titles()).containsExactly("Q3 Proposal.docx - Word");
        assertThat(timeline.sessions()).noneMatch(Session::uncertain);
        assertThat(timeline.flags()).isEmpty();
    }

    @Test
    void anIdleEndResumesTheApplicationFocusedBeforeTheIdleStart() {
        focus("ws-01", "WINWORD.EXE", "09:00", "Report");
        idle(Role.IDLE_START, "ws-01", "09:30");
        idle(Role.IDLE_END, "ws-01", "10:00");
        focus("ws-01", "chrome.exe", "10:20", "News");
        idle(Role.IDLE_START, "ws-01", "10:30");

        final Timeline timeline = build("12:00");

        assertThat(timeline.sessions()).extracting(Session::process, Session::start, Session::end, Session::startMark)
                .containsExactly(
                        tuple("WINWORD.EXE", at("09:00"), at("09:30"), null),
                        tuple("WINWORD.EXE", at("10:00"), at("10:20"),
                                "resumed after idle"),
                        tuple("chrome.exe", at("10:20"), at("10:30"), null));
        assertThat(timeline.flags()).isEmpty();
    }

    @Test
    void theCapEndsAnIntervalWithNoEventsAndMarksItsEndUncertain() {
        focus("ws-01", "WINWORD.EXE", "09:00", "Report");
        action("ws-01", "WINWORD.EXE", "09:10", "clipboard copy", null);
        focus("ws-01", "chrome.exe", "11:00", "News");
        idle(Role.IDLE_START, "ws-01", "11:05");

        final Timeline timeline = build("12:00");

        final Session word = timeline.sessions().getFirst();
        // The last sign of activity was at 09:10: the interval ends 30 minutes later, uncertain.
        assertThat(word.end()).isEqualTo(at("09:40"));
        assertThat(word.endReason()).isEqualTo("cap");
        assertThat(word.endMark()).isEqualTo("ended by the idle cap: no event for 30 minutes");
        assertThat(timeline.count(FlagName.ENDED_BY_CAP)).isEqualTo(1);
        // Another cap, as a call or skill may give.
        assertThat(TimelineSessions.build(events, options("12:00", 10 * 60_000L, Set.of())).sessions().getFirst()
                .end()).isEqualTo(at("09:20"));
    }

    @Test
    void anIntervalStillRunningAtThePeriodsEndEndsThereAndSaysSo() {
        focus("ws-01", "WINWORD.EXE", "11:50", "Report");

        final Session session = build("12:00").sessions().getFirst();

        assertThat(session.end()).isEqualTo(at("12:00"));
        assertThat(session.endReason()).isEqualTo("period end");
        assertThat(session.endMark()).isEqualTo("still running at the period's end");
    }

    @Test
    void actionsAttachByProcessOrByTimeAndThoseWithNoHostByTimeToAnyHostsInterval() {
        focus("ws-01", "WINWORD.EXE", "09:00", "Report");
        action("ws-01", "WINWORD.EXE", "09:05", "clipboard copy", null);
        action("ws-01", "explorer.exe", "09:10", "file copy", "C:\\Users\\jsmith\\Report.docx");
        actionNoHost("09:15", "web access", "https://example.org/pricing");
        idle(Role.IDLE_START, "ws-01", "09:30");
        actionNoHost("10:00", "web access", "https://example.org/news");
        action("ws-02", "notepad.exe", "09:20", "file copy", "notes.txt");
        idle(Role.IDLE_START, "ws-02", "09:21");

        final Timeline timeline = build("12:00");

        assertThat(timeline.actions()).extracting(action -> action.event().kind() + "@" + action.session() + "/"
                                                           + action.by())
                .containsExactly("clipboard copy@1/process", "file copy@1/time", "web access@1/time",
                        "file copy@0/null", "web access@0/null");
        assertThat(timeline.unattached()).extracting(action -> action.event().object())
                .containsExactly("notes.txt", "https://example.org/news");
        // An action on an application with no session running starts none: only a focus does (eval case 78, the
        // process field holding a source's name, 'proxy'). It is left to the activity periods.
        assertThat(timeline.sessions()).extracting(Session::process).containsExactly("WINWORD.EXE");
        assertThat(timeline.sessions().getFirst().actionsByKind()).containsEntry("clipboard copy", 1)
                .containsEntry("file copy", 1).containsEntry("web access", 1);
        final Action copy = timeline.actions().get(1);
        assertThat(copy.by()).isEqualTo(By.TIME);
    }

    @Test
    void eachDaysActiveTimeIsInTheUsersZoneSplitAtItsMidnight() {
        // 12:30 to 13:30 UTC is 23:30 to 00:30 in Canberra (11 hours ahead in November).
        focus("ws-01", "WINWORD.EXE", "12:30", "Report");
        action("ws-01", "WINWORD.EXE", "13:00", "clipboard copy", null);
        idle(Role.IDLE_START, "ws-01", "13:30");

        final List<Daily> canberra = TimelineSessions.build(events, new Options(CAP, at("23:00"), at("23:00"),
                ZoneId.of("Australia/Canberra"), names(), Set.of())).daily();
        final List<Daily> utc = build("23:00").daily();

        assertThat(canberra).extracting(Daily::day, Daily::activeMs, Daily::sessions).containsExactly(
                tuple(LocalDate.of(2026, 11, 10), 30 * 60_000L, 1),
                tuple(LocalDate.of(2026, 11, 11), 30 * 60_000L, 0));
        assertThat(utc).extracting(Daily::day, Daily::activeMs).containsExactly(
                tuple(LocalDate.of(2026, 11, 10), 60 * 60_000L));
    }

    @Test
    void unpairedWindowBoundsAreFlaggedAndTheIntervalsStillMade() {
        window(Role.OPEN, "ws-01", "WINWORD.EXE", "08:55", "w1");
        focus("ws-01", "WINWORD.EXE", "09:00", "Report");
        window(Role.CLOSE, "ws-01", "EXCEL.EXE", "09:05", "w2");
        window(Role.OPEN, "ws-01", "chrome.exe", "09:06", "w3");
        window(Role.CLOSE, "ws-01", "WINWORD.EXE", "09:20", "w1");
        idle(Role.IDLE_START, "ws-01", "09:30");

        final Timeline timeline = build("12:00");

        assertThat(timeline.count(FlagName.CLOSE_WITHOUT_OPEN)).isEqualTo(1);
        assertThat(timeline.count(FlagName.OPEN_WITHOUT_CLOSE)).isEqualTo(1);
        assertThat(timeline.flags().stream().filter(flag -> flag.name() == FlagName.OPEN_WITHOUT_CLOSE)
                .findFirst().orElseThrow().examples()).singleElement().asString().contains("chrome.exe on ws-01");
        assertThat(timeline.windows()).extracting(window -> window.process() + ":" + (window.opened() != null) + ","
                                                            + (window.closed() != null))
                .containsExactlyInAnyOrder("EXCEL.EXE:false,true", "WINWORD.EXE:true,true", "chrome.exe:true,false");
        assertThat(timeline.sessions()).hasSize(1);
    }

    @Test
    void anIdleStartWithActivityAfterItAndNoIdleEndIsFlaggedAsIsAnIdleEndWithNoStart() {
        focus("ws-01", "WINWORD.EXE", "09:00", "Report");
        idle(Role.IDLE_START, "ws-01", "09:30");
        // The unlock was dropped: the next focus comes while locked.
        focus("ws-01", "chrome.exe", "10:00", "News");
        idle(Role.IDLE_END, "ws-01", "10:10");
        idle(Role.IDLE_START, "ws-01", "10:20");

        final Timeline timeline = build("12:00");

        assertThat(timeline.count(FlagName.IDLE_START_WITHOUT_END)).isEqualTo(1);
        assertThat(timeline.count(FlagName.IDLE_END_WITHOUT_START)).isEqualTo(1);
        assertThat(timeline.sessions()).extracting(Session::process).containsExactly("WINWORD.EXE", "chrome.exe");
    }

    @Test
    void aFocusOnAnApplicationWhoseWindowNeverOpenedIsFlaggedOnlyForASourceThatHasOpens() {
        window(Role.OPEN, "ws-01", "WINWORD.EXE", "08:55", "w1");
        focus("ws-01", "WINWORD.EXE", "09:00", "Report");
        focus("ws-01", "EXCEL.EXE", "09:10", "Budget");
        focus("ws-01", "EXCEL.EXE", "09:20", "Budget");
        idle(Role.IDLE_START, "ws-01", "09:30");

        assertThat(TimelineSessions.build(events, options("12:00", CAP, Set.of(ENDPOINT)))
                .count(FlagName.FOCUS_WITHOUT_OPEN)).isEqualTo(1);
        assertThat(build("12:00").count(FlagName.FOCUS_WITHOUT_OPEN)).isZero();
    }

    @Test
    void twoApplicationsFocusedAtOnceOnAHostAreFlagged() {
        focus("ws-01", "WINWORD.EXE", "09:00", "Report");
        events.add(new Event(at("09:00") + 500, ENDPOINT, "e" + ids++, Role.FOCUS, null, "ws-01", "EXCEL.EXE",
                "Budget", null, null, null, null, null));
        idle(Role.IDLE_START, "ws-01", "09:30");

        assertThat(build("12:00").count(FlagName.TWO_FOCUSED)).isEqualTo(1);
    }

    @Test
    void aSourceSilentOnAHostWhileAnotherShowsTheUserActiveThereIsFlaggedButNotOneWithOnlyIdleRoles() {
        focus("ws-01", "WINWORD.EXE", "09:00", "Report");
        idle(Role.IDLE_END, "ws-01", "08:59");
        // The endpoint's focus events stop at 09:00; the proxy shows browsing from the host until 10:30.
        for (int minute = 0; minute <= 90; minute += 10) {
            proxy("ws-01", at("09:00") + (minute + 5) * 60_000L, "https://example.org/p" + minute);
        }
        focus("ws-01", "chrome.exe", "11:00", "News");

        final Timeline timeline = build("12:00");

        assertThat(timeline.count(FlagName.SOURCE_SILENCE)).isEqualTo(1);
        assertThat(timeline.flags().stream().filter(flag -> flag.name() == FlagName.SOURCE_SILENCE).findFirst()
                .orElseThrow().examples().getFirst()).startsWith("Endpoint has no events on ws-01 from")
                .contains("while Proxy shows the user active there");
    }

    @Test
    void anIdleEndBeforeAnyActivityIsThePeriodsStartNotAnUnpairedBound() {
        // A logon first thing: the user was idle since before the period, so nothing is missing.
        idle(Role.IDLE_END, "ws-01", "08:30");
        focus("ws-01", "WINWORD.EXE", "08:31", "Report");
        idle(Role.IDLE_START, "ws-01", "09:00");

        assertThat(build("12:00").flags()).isEmpty();
    }

    @Test
    void aLockedHostOrASourceThatReportsNoFocusIsNeverASilentSource() {
        focus("ws-01", "chrome.exe", "09:00", "News");
        proxy("ws-01", at("09:10"), "https://example.org/a");
        idle(Role.IDLE_START, "ws-01", "09:15");
        // Locked from 09:15 to 10:15: the proxy's background traffic then isn't the user, nor the endpoint silent.
        for (int minute = 20; minute <= 70; minute += 10) {
            proxy("ws-01", at("09:00") + minute * 60_000L, "https://example.org/sync");
        }
        idle(Role.IDLE_END, "ws-01", "10:15");
        focus("ws-01", "WINWORD.EXE", "10:16", "Report");
        // The proxy has no events from 10:16 to 11:30 while the endpoint is active: it reports no focus, so is
        // sparse by nature.
        for (final String time : List.of("10:30", "10:45", "11:00", "11:15")) {
            action("ws-01", "WINWORD.EXE", time, "save", null);
        }
        idle(Role.IDLE_START, "ws-01", "11:30");

        assertThat(build("12:00").count(FlagName.SOURCE_SILENCE)).isZero();
    }

    @Test
    void eventsOutOfOrderInTheirStreamAndFutureDatedOnesAreFlagged() {
        events.add(new Event(at("09:00"), ENDPOINT, "a", Role.FOCUS, null, "ws-01", "WINWORD.EXE", null, null, null,
                null, "7", "1"));
        events.add(new Event(at("09:10"), ENDPOINT, "b", Role.FOCUS, null, "ws-01", "EXCEL.EXE", null, null, null,
                null, "7", "2"));
        // Event 3 is dated before event 2 by more than a minute.
        events.add(new Event(at("09:05"), ENDPOINT, "c", Role.FOCUS, null, "ws-01", "chrome.exe", null, null, null,
                null, "7", "3"));
        // After the timeline was made.
        events.add(new Event(at("13:00"), ENDPOINT, "d", Role.FOCUS, null, "ws-01", "chrome.exe", null, null, null,
                null, "7", "4"));

        final Timeline timeline = TimelineSessions.build(events, new Options(CAP, at("14:00"), at("12:00"),
                ZoneOffset.UTC, names(), Set.of()));

        assertThat(timeline.count(FlagName.OUT_OF_ORDER)).isEqualTo(1);
        assertThat(timeline.count(FlagName.FUTURE_DATED)).isEqualTo(1);
    }

    @Test
    void twoSourcesDisagreeingOnAHostsClockAreFlaggedFromTheObjectsBothSaw() {
        focus("ws-01", "explorer.exe", "09:00", "Files");
        for (int i = 0; i < 3; i++) {
            final long time = at("09:00") + i * 20 * 60_000L;
            events.add(new Event(time, ENDPOINT, "e" + ids++, Role.ACTION, "file copy", "ws-01", "explorer.exe",
                    null, null, "File" + i + ".docx", null, null, null));
            // The Windows log has each copy 5 minutes later.
            events.add(new Event(time + 5 * 60_000L, WINDOWS, "e" + ids++, Role.ACTION, "file copy", "ws-01", null,
                    null, null, "file" + i + ".docx", null, null, null));
        }

        final Timeline timeline = build("12:00");

        assertThat(timeline.count(FlagName.CLOCK_DISAGREEMENT)).isEqualTo(1);
        assertThat(timeline.flags().stream().filter(flag -> flag.name() == FlagName.CLOCK_DISAGREEMENT).findFirst()
                .orElseThrow().examples().getFirst()).isEqualTo("Windows is about 5 minutes ahead of Endpoint on "
                                                                + "ws-01 (3 objects seen by both)");
    }

    @Test
    void noEventsMakeNoSessions() {
        final Timeline timeline = build("12:00");

        assertThat(timeline.sessions()).isEmpty();
        assertThat(timeline.actions()).isEmpty();
        assertThat(timeline.flags()).isEmpty();
    }

    // ------------------------------------------------------------------------------------------------

    private Timeline build(final String end) {
        return TimelineSessions.build(events, options(end, CAP, Set.of()));
    }

    private static Options options(final String end, final long cap, final Set<Integer> withOpen) {
        return new Options(cap, at(end), at(end), ZoneOffset.UTC, names(), withOpen);
    }

    private static List<String> names() {
        return List.of("Endpoint", "Windows", "Proxy");
    }

    private void focus(final String host, final String process, final String time, final String title) {
        events.add(new Event(at(time), ENDPOINT, "e" + ids++, Role.FOCUS, null, host, process, title, null, null,
                null, null, null));
    }

    private void window(final Role role, final String host, final String process, final String time,
                        final String window) {
        events.add(new Event(at(time), ENDPOINT, "e" + ids++, role, null, host, process, null, window, null, null,
                null, null));
    }

    private void idle(final Role role, final String host, final String time) {
        events.add(new Event(at(time), WINDOWS, "e" + ids++, role, null, host, null, null, null, null, null, null,
                null));
    }

    private void action(final String host, final String process, final String time, final String kind,
                        final String object) {
        events.add(new Event(at(time), ENDPOINT, "e" + ids++, Role.ACTION, kind, host, process, null, null, object,
                null, null, null));
    }

    private void actionNoHost(final String time, final String kind, final String object) {
        events.add(new Event(at(time), PROXY, "e" + ids++, Role.ACTION, kind, null, null, null, null, object, null,
                null, null));
    }

    private void proxy(final String host, final long time, final String url) {
        events.add(new Event(time, PROXY, "e" + ids++, Role.ACTION, "web access", host, null, null, null, url, null,
                null, null));
    }

    private static long at(final String time) {
        return Instant.parse("2026-11-10T" + time + ":00Z").toEpochMilli();
    }
}
