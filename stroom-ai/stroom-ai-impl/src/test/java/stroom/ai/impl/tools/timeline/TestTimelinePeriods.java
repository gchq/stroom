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

import stroom.ai.impl.tools.catalogue.TimelinePeriod;
import stroom.ai.impl.tools.timeline.EventXmlClassifier.Classified;
import stroom.ai.impl.tools.timeline.EventXmlClassifier.Route;
import stroom.ai.impl.tools.timeline.TimelinePeriods.Period;
import stroom.ai.impl.tools.timeline.TimelineSessions.Event;
import stroom.ai.impl.tools.timeline.TimelineSessions.Options;
import stroom.ai.impl.tools.timeline.TimelineSessions.Role;

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

/// A timeline's periods: paired by kind and key across hosts and sources, unpaired either way, a repeated start.
class TestTimelinePeriods {

    private static final Options OPTIONS = new Options(30 * 60_000L, at("18:00"), at("18:00"), ZoneOffset.UTC,
            List.of("VPN", "Doors"), Set.of());

    private final List<Event> events = new ArrayList<>();
    private final Map<String, Classified> classified = new LinkedHashMap<>();
    private int ids;

    @Test
    void aStartAndItsEndPairByKindAndKeyAcrossHostsAndSources() {
        // Entered by door A-1, left by A-4: one site.
        period(1, Role.PERIOD_START, "door-a1", "08:55", "on_site", Map.of("EventSource/Door/Location/Site",
                "Canberra"));
        period(0, Role.PERIOD_START, "host01", "09:00", "remote_session", Map.of());
        period(0, Role.PERIOD_END, "host01", "10:30", "remote_session", Map.of());
        period(1, Role.PERIOD_END, "door-a4", "17:05", "on_site", Map.of("EventSource/Door/Location/Site",
                "Canberra"));

        final List<Period> periods = build();

        assertThat(periods).extracting(Period::id, Period::kind, Period::keyText, Period::startSeen,
                        Period::endSeen)
                .containsExactly(
                        tuple("pr1", "on_site", "Canberra", TimelinePeriods.SEEN, TimelinePeriods.SEEN),
                        tuple("pr2", "remote_session", "host01", TimelinePeriods.SEEN, TimelinePeriods.SEEN));
        assertThat(periods.getFirst().start()).isEqualTo(at("08:55"));
        assertThat(periods.getFirst().end()).isEqualTo(at("17:05"));
        assertThat(periods.getFirst().hosts()).containsExactly("door-a1", "door-a4");
        assertThat(periods.getFirst().eventIds()).hasSize(2);
        assertThat(periods.get(1).label()).isEqualTo("remote session, host01");
        assertThat(periods.get(1).shown()).isEqualTo("remote session, host01");
        assertThat(periods.get(1).uncertain()).isFalse();
    }

    @Test
    void aStartWithNoEndClosesAtTheNextStartElseThePeriodsEndAndAnEndWithNoStartOpensAtItsStart() {
        period(0, Role.PERIOD_END, "host01", "08:30", "remote_session", Map.of());
        period(0, Role.PERIOD_START, "host01", "09:00", "remote_session", Map.of());
        period(0, Role.PERIOD_START, "host01", "11:00", "remote_session", Map.of());
        // A repeated end opens at the last one's end, not the period's start.
        period(0, Role.PERIOD_END, "host02", "12:00", "remote_session", Map.of());
        period(0, Role.PERIOD_END, "host02", "12:05", "remote_session", Map.of());

        final List<Period> periods = build();

        assertThat(periods).extracting(Period::keyText, Period::start, Period::end, Period::startSeen,
                        Period::endSeen)
                .containsExactly(
                        tuple("host01", at("06:00"), at("08:30"), TimelinePeriods.START_NOT_SEEN,
                                TimelinePeriods.SEEN),
                        tuple("host02", at("06:00"), at("12:00"), TimelinePeriods.START_NOT_SEEN,
                                TimelinePeriods.SEEN),
                        tuple("host01", at("09:00"), at("11:00"), TimelinePeriods.SEEN,
                                TimelinePeriods.END_NOT_SEEN),
                        tuple("host01", at("11:00"), at("18:00"), TimelinePeriods.SEEN,
                                TimelinePeriods.END_NOT_SEEN),
                        tuple("host02", at("12:00"), at("12:05"), TimelinePeriods.START_NOT_SEEN,
                                TimelinePeriods.SEEN));
        assertThat(periods.getFirst().startEvent()).isNull();
        assertThat(periods.get(2).endEvent()).isNull();
        assertThat(periods).allMatch(Period::uncertain);
    }

    @Test
    void aRolesOwnKeyPairsAndAKindAloneIsOneAtATime() {
        // By the session id the role names, so two sessions on one host are two periods.
        period(0, Role.PERIOD_START, "host01", "09:00", new TimelinePeriod("remote_session", "SessionId"),
                Map.of("Data[SessionId]", "s1"));
        period(0, Role.PERIOD_START, "host01", "09:10", new TimelinePeriod("remote_session", "SessionId"),
                Map.of("Data[SessionId]", "s2"));
        period(0, Role.PERIOD_END, "host01", "09:20", new TimelinePeriod("remote_session", "SessionId"),
                Map.of("Data[SessionId]", "s1"));
        // A call with no meeting id: the kind alone.
        period(1, Role.PERIOD_START, "host01", "10:00", "call", Map.of());
        period(1, Role.PERIOD_END, "host02", "10:30", "call", Map.of());

        final List<Period> periods = build();

        assertThat(periods).extracting(Period::kind, Period::keyText, Period::endSeen)
                .containsExactly(
                        tuple("remote_session", "s1", TimelinePeriods.SEEN),
                        tuple("remote_session", "s2", TimelinePeriods.END_NOT_SEEN),
                        tuple("call", "", TimelinePeriods.SEEN));
        assertThat(periods.get(2).label()).isEqualTo("call");
        assertThat(TimelinePeriods.during(periods, at("09:15"), at("09:16"))).extracting(Period::keyText)
                .containsExactly("s1", "s2");
    }

    @Test
    void otherEventsMakeNoPeriod() {
        events.add(new Event(at("09:00"), 0, "x", Role.IDLE_END, null, "host01", null, null, null, null, null, null,
                null));

        assertThat(build()).isEmpty();
    }

    private List<Period> build() {
        return TimelinePeriods.build(events, classified, at("06:00"), at("18:00"), OPTIONS);
    }

    private void period(final int source, final Role role, final String host, final String time, final String kind,
                        final Map<String, String> keys) {
        period(source, role, host, time, new TimelinePeriod(kind, null), keys);
    }

    private void period(final int source, final Role role, final String host, final String time,
                        final TimelinePeriod period, final Map<String, String> keys) {
        final String id = "e" + ids++;
        events.add(new Event(at(time), source, id, role, period.kind(), host, null, null, null, null, null,
                "7", String.valueOf(ids)));
        classified.put(id, new Classified(null, null, role == Role.PERIOD_START
                ? "Start-It"
                : "End-It", role, period.kind(), null, null, host, keys, Route.XML, period));
    }

    private static long at(final String time) {
        return Instant.parse("2026-11-10T" + time + ":00Z").toEpochMilli();
    }
}
