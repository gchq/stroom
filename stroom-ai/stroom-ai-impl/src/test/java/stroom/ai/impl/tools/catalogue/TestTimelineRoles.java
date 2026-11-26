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

package stroom.ai.impl.tools.catalogue;

import stroom.ai.impl.tools.generation.ModelReader;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// The timeline roles of a catalogue's event types: filled in from the schema's table where the element and Action
/// settle one, asked for otherwise, a given one kept, and read back by TypeId.
class TestTimelineRoles {

    @Test
    void theSchemasTableSettlesAuthenticateAndProcessActionsAndOtherElements() {
        assertThat(TimelineRoles.bySchema("Authenticate", "Logon")).contains(TimelineRole.ACTIVE);
        assertThat(TimelineRoles.bySchema("authenticate", "screenunlock")).contains(TimelineRole.ACTIVE);
        assertThat(TimelineRoles.bySchema("Authenticate", "ScreenLock")).contains(TimelineRole.IDLE);
        assertThat(TimelineRoles.bySchema("Process", "Execute")).contains(TimelineRole.OPEN);
        assertThat(TimelineRoles.bySchema("Process", "Terminate")).contains(TimelineRole.CLOSE);
        assertThat(TimelineRoles.bySchema("View", null)).contains(TimelineRole.ACTION);
        assertThat(TimelineRoles.bySchema("Network/Permit", null)).contains(TimelineRole.ACTION);
    }

    @Test
    void whatTheTableDoesntNameIsNotSettled() {
        // Unknown, no element, an element of a fork the schema doesn't have, and Actions the table doesn't name.
        assertThat(TimelineRoles.bySchema("Unknown", null)).isEmpty();
        assertThat(TimelineRoles.bySchema(null, null)).isEmpty();
        assertThat(TimelineRoles.bySchema("  ", "Logon")).isEmpty();
        assertThat(TimelineRoles.bySchema("Telemetry", null)).isEmpty();
        assertThat(TimelineRoles.bySchema("Authenticate", "ChangePassword")).isEmpty();
        assertThat(TimelineRoles.bySchema("Authenticate", null)).isEmpty();
        assertThat(TimelineRoles.bySchema("Process", "Other")).isEmpty();
    }

    @Test
    void theKindOfAnActionIsItsElementAndAction() {
        assertThat(TimelineRoles.kind("View", null)).isEqualTo("view");
        assertThat(TimelineRoles.kind("Network/Permit", " Other ")).isEqualTo("network other");
        assertThat(TimelineRoles.kind(null, null)).isEqualTo("event");
    }

    @Test
    void aSchemaRoleIsFilledInAndFilledInAgainWhenTheElementChanges() {
        final EventType logon = type("Logon-User", "Authenticate", "Logon");

        final EventType settled = TimelineRoles.settled(logon);

        assertThat(settled.timelineRole()).isEqualTo("active");
        assertThat(settled.timelineRoleFrom()).isEqualTo(TimelineRoles.FROM_SCHEMA);
        // Its element changed by a proposal: its schema role follows.
        final EventType changed = new EventType("Logon-User", "t", null, "Process", "Execute", null, null, null,
                null, List.of(), false, EventType.AGENT_KNOWLEDGE, "high", null, EventType.PROPOSED, null, List.of(),
                settled.timelineRole(), settled.timelineRoleFrom());
        assertThat(TimelineRoles.settled(changed).timelineRole()).isEqualTo("open");
        // Changed to Unknown: no role the schema settles, so none.
        final EventType unknown = changed.withTimelineRole("open", TimelineRoles.FROM_SCHEMA);
        assertThat(TimelineRoles.settled(new EventType("Logon-User", "t", null, "Unknown", null, null, null, null,
                null, List.of(), false, EventType.AGENT_KNOWLEDGE, "high", null, EventType.PROPOSED, null, List.of(),
                unknown.timelineRole(), unknown.timelineRoleFrom())).timelineRole()).isNull();
    }

    @Test
    void aGivenRoleIsKeptOverTheSchemas() {
        final EventType focus = type("View-Window", "View", null).withTimelineRole("focus", TimelineRoles.FROM_GIVEN);

        assertThat(TimelineRoles.settled(focus).timelineRole()).isEqualTo("focus");
        assertThat(TimelineRoles.settled(focus).timelineRoleFrom()).isEqualTo(TimelineRoles.FROM_GIVEN);
    }

    @Test
    void theUnsettledAreThoseWithNeitherASchemaRoleNorAGivenOneButNotDropped() {
        final EventType focus = type("Focus-Window", "Unknown", null);
        final EventType vendor = type("Authenticate-Other", "Authenticate", "Other");
        final EventType given = type("Gained-Focus", "Unknown", null).withTimelineRole("focus",
                TimelineRoles.FROM_GIVEN);
        final EventType view = type("View-File", "View", null);
        final EventType dropped = new EventType("Heartbeat", "t", null, "Unknown", null, null, null, null, null,
                List.of(), true, EventType.USER_CONFIRMED, "high", null, EventType.CONFIRMED);

        assertThat(TimelineRoles.unsettled(List.of(focus, vendor, given, view, dropped)))
                .extracting(EventType::typeId)
                .containsExactly("Focus-Window", "Authenticate-Other");
    }

    @Test
    void theRolesAreReadByTypeIdFilledFromTheSchemaWhereTheCatalogueHasNone() {
        final Catalogue catalogue = new Catalogue(List.of(type("Logon-User", "Authenticate", "Logon"),
                type("Focus-Window", "Unknown", null).withTimelineRole("focus", TimelineRoles.FROM_GIVEN),
                type("View-File", "View", null), type("Odd-Thing", "Unknown", null)), null, null);

        final Map<String, TimelineRoles.Role> roles = TimelineRoles.byTypeId(catalogue);

        assertThat(roles.keySet()).containsExactly("Logon-User", "Focus-Window", "View-File");
        assertThat(roles.get("Logon-User").role()).isEqualTo(TimelineRole.ACTIVE);
        assertThat(roles.get("Focus-Window").from()).isEqualTo(TimelineRoles.FROM_GIVEN);
        assertThat(roles.get("View-File").kind()).isEqualTo("view");
        assertThat(TimelineRoles.byTypeId(null)).isEmpty();
    }

    @Test
    void aSetSaysItsSchemaRolesAndAsksForTheRest() {
        final String said = TimelineRoles.said(List.of(type("Logon-User", "Authenticate", "Logon"),
                type("View-File", "View", null), type("Focus-Window", "Unknown", null)));

        assertThat(said).contains("from the schema: Logon-User active, View-File action (view)")
                .contains("Not settled by the schema: Focus-Window")
                .contains("focus, active, idle, open, close, action, ignore")
                .contains("window gaining focus");
        assertThat(TimelineRoles.said(List.of())).isEmpty();
    }

    @Test
    void aSetsFormAsksForTheRolesTheSchemaLeavesAndShowsThoseGiven() {
        // Seen in eval case 109 on Sonnet: the roles were asked only in the table the agent was to show, it didn't,
        // and the user accepted the set in its form without seeing the question.
        final List<String> asked = TimelineRoles.askedInForm(List.of(type("Logon-User", "Authenticate", "Logon"),
                type("Switch-Session", "Authenticate", "Other"), type("Odd-Thing", "Unknown", null),
                type("Gained-Focus", "Unknown", null).withTimelineRole("active", TimelineRoles.FROM_GIVEN)));

        assertThat(asked).containsExactly(
                "Switch-Session, Odd-Thing: the schema doesn't settle them; say each one's role in your answer "
                + "(focus, active, idle, open, close, action, ignore, period_start <kind>, period_end <kind>), or "
                + "leave it for the timeline to judge",
                "Given: Gained-Focus active (yours to change in your answer)");
        assertThat(TimelineRoles.askedInForm(List.of(type("Switch-Session", "Authenticate", "Other"))))
                .containsExactly("Switch-Session: the schema doesn't settle it; say its role in your answer "
                                 + "(focus, active, idle, open, close, action, ignore, period_start <kind>, "
                                 + "period_end <kind>), or leave it for the timeline to judge");
        // Every role the schema's: nothing to ask.
        assertThat(TimelineRoles.askedInForm(List.of(type("Logon-User", "Authenticate", "Logon")))).isEmpty();
        assertThat(TimelineRoles.askedInForm(List.of())).isEmpty();
        // A dropped event type writes no events: no role asked.
        assertThat(TimelineRoles.askedInForm(List.of(new EventType("Heartbeat", "t", null, "Unknown", null, null,
                null, null, null, List.of(), true, EventType.USER_CONFIRMED, "high", null,
                EventType.CONFIRMED)))).isEmpty();
    }

    @Test
    void theRoleIsWrittenToJsonOnlyWhenItHasOneAndReadBack() {
        final EventType plain = type("View-File", "View", null);
        assertThat(plain.toJson()).doesNotContainKeys(EventType.TIMELINE_ROLE, EventType.TIMELINE_ROLE_FROM);

        final EventType focus = plain.withTimelineRole("focus", TimelineRoles.FROM_GIVEN);
        final Map<String, Object> json = focus.toJson();
        assertThat(json).containsEntry(EventType.TIMELINE_ROLE, "focus")
                .containsEntry(EventType.TIMELINE_ROLE_FROM, TimelineRoles.FROM_GIVEN);
        assertThat(EventType.read(new ModelReader(), json, List.of())).isEqualTo(focus);
    }

    @Test
    void aRoleGivenWithoutItsSourceIsGivenAndAnUnknownRoleIsAnError() {
        final Map<String, Object> json = new LinkedHashMap<>(type("View-File", "View", null).toJson());
        json.put(EventType.TIMELINE_ROLE, "focus");
        assertThat(EventType.read(new ModelReader(), json, List.of()).timelineRoleFrom())
                .isEqualTo(TimelineRoles.FROM_GIVEN);

        json.put(EventType.TIMELINE_ROLE, "sometimes");
        final ModelReader reader = new ModelReader();
        assertThat(EventType.read(reader, json, List.of())).isNull();
        assertThat(reader.errors()).isNotEmpty();
    }

    @Test
    void aConnectStartsAndADisconnectEndsARemoteSessionByTheSchema() {
        assertThat(TimelineRoles.bySchema("Authenticate", "Connect")).contains(TimelineRole.PERIOD_START);
        assertThat(TimelineRoles.bySchema("Authenticate", "reconnect")).contains(TimelineRole.PERIOD_START);
        assertThat(TimelineRoles.bySchema("Authenticate", "Disconnect")).contains(TimelineRole.PERIOD_END);
        assertThat(TimelineRoles.periodBySchema("Authenticate", "Connect"))
                .isEqualTo(new TimelinePeriod(TimelinePeriod.REMOTE_SESSION, null));
        // A logon and a lock stay attention's, with no period.
        assertThat(TimelineRoles.bySchema("Authenticate", "Logon")).contains(TimelineRole.ACTIVE);
        assertThat(TimelineRoles.periodBySchema("Authenticate", "Logon")).isNull();
        assertThat(TimelineRoles.bySchema("Authenticate", "ScreenLock")).contains(TimelineRole.IDLE);
        // ElevatePrivilege names no end, so it stays asked.
        assertThat(TimelineRoles.bySchema("Authenticate", "ElevatePrivilege")).isEmpty();
    }

    @Test
    void aConnectFilledInAsActiveBeforeIsFilledInAgainAsARemoteSessionAndAGivenOneKept() {
        // As a catalogue written before 2026-11-26 holds it: active, from the schema.
        final EventType before = type("Connect-Vpn", "Authenticate", "Connect")
                .withTimelineRole("active", TimelineRoles.FROM_SCHEMA);

        final EventType settled = TimelineRoles.settled(before);

        assertThat(settled.timelineRole()).isEqualTo("period_start");
        assertThat(settled.timelineRoleFrom()).isEqualTo(TimelineRoles.FROM_SCHEMA);
        assertThat(settled.timelinePeriod().kind()).isEqualTo(TimelinePeriod.REMOTE_SESSION);
        // The user's own active is kept.
        final EventType given = before.withTimelineRole("active", TimelineRoles.FROM_GIVEN);
        assertThat(TimelineRoles.settled(given).timelineRole()).isEqualTo("active");
        assertThat(TimelineRoles.settled(given).timelinePeriod()).isNull();
    }

    @Test
    void aPeriodRoleIsReadByTypeIdWithItsKindAndKey() {
        final Catalogue catalogue = new Catalogue(List.of(type("Connect-Vpn", "Authenticate", "Connect"),
                type("Enter-Door", "Unknown", null).withTimelineRole("period_start", TimelineRoles.FROM_GIVEN,
                        new TimelinePeriod("on_site", "Location/Building")),
                // A period role with no kind (written by hand) is no role.
                type("Odd-Thing", "Unknown", null).withTimelineRole("period_end", TimelineRoles.FROM_GIVEN)), null,
                null);

        final Map<String, TimelineRoles.Role> roles = TimelineRoles.byTypeId(catalogue);

        assertThat(roles.keySet()).containsExactly("Connect-Vpn", "Enter-Door");
        assertThat(roles.get("Connect-Vpn").period().kind()).isEqualTo(TimelinePeriod.REMOTE_SESSION);
        assertThat(roles.get("Enter-Door").role()).isEqualTo(TimelineRole.PERIOD_START);
        assertThat(roles.get("Enter-Door").period()).isEqualTo(new TimelinePeriod("on_site", "Location/Building"));
    }

    @Test
    void aSetSaysItsPeriodRolesWithTheirKindsAndHowToGiveOne() {
        final List<EventType> set = List.of(type("Connect-Vpn", "Authenticate", "Connect"),
                type("Enter-Door", "Unknown", null).withTimelineRole("period_start", TimelineRoles.FROM_GIVEN,
                        new TimelinePeriod("on_site", null)));

        assertThat(TimelineRoles.said(set)).contains("Connect-Vpn period_start (remote_session)")
                .contains("Given: Enter-Door period_start (on_site)")
                .contains("as period_start or period_end with its kind (remote_session, on_site, elevated, "
                          + "device_attached, call, or your own)");
        assertThat(TimelineRoles.askedInForm(set))
                .containsExactly("Given: Enter-Door period_start on_site (yours to change in your answer)");
    }

    @Test
    void aPeriodRoleIsWrittenWithItsKindAndKeyAndReadBackEitherWay() {
        final EventType door = type("Enter-Door", "Unknown", null).withTimelineRole("period_start",
                TimelineRoles.FROM_GIVEN, new TimelinePeriod("on_site", "Location/Building"));
        final Map<String, Object> json = door.toJson();
        assertThat(json).containsEntry(EventType.TIMELINE_ROLE, "period_start")
                .containsEntry(EventType.TIMELINE_PERIOD, "on_site")
                .containsEntry(EventType.TIMELINE_PERIOD_KEY, "Location/Building");
        assertThat(EventType.read(new ModelReader(), json, List.of())).isEqualTo(door);

        // The role's text form, as a proposal may give it.
        final Map<String, Object> text = new LinkedHashMap<>(type("Leave-Door", "Unknown", null).toJson());
        text.put(EventType.TIMELINE_ROLE, "period_end On Site");
        final EventType read = EventType.read(new ModelReader(), text, List.of());
        assertThat(read.timelineRole()).isEqualTo("period_end");
        assertThat(read.timelinePeriod()).isEqualTo(new TimelinePeriod("on_site", null));

        // A period role with no kind is refused; a kind on another role is dropped.
        final Map<String, Object> noKind = new LinkedHashMap<>(type("Leave-Door", "Unknown", null).toJson());
        noKind.put(EventType.TIMELINE_ROLE, "period_end");
        final ModelReader reader = new ModelReader();
        assertThat(EventType.read(reader, noKind, List.of())).isNull();
        assertThat(reader.errors()).isNotEmpty();
        noKind.put(EventType.TIMELINE_ROLE, "action");
        noKind.put(EventType.TIMELINE_PERIOD, "on_site");
        assertThat(EventType.read(new ModelReader(), noKind, List.of()).timelinePeriod()).isNull();
    }

    @Test
    void theCatalogueRecordsTheFeedsItWasCheckedAgainst() {
        final Catalogue catalogue = new Catalogue(List.of(type("View-File", "View", null)), null, null)
                .withCheckedFeeds(List.of("ACME-A", "ACME-B"));

        final Map<String, Object> json = catalogue.toJson();

        assertThat(json).containsEntry(Catalogue.CHECKED_FEEDS, List.of("ACME-A", "ACME-B"));
        assertThat(Catalogue.fromJson(json).checkedFeeds()).containsExactly("ACME-A", "ACME-B");
        assertThat(new Catalogue(List.of(), null, null).toJson()).doesNotContainKey(Catalogue.CHECKED_FEEDS);
        // Kept through the catalogue's own changes.
        assertThat(catalogue.withEventTypes(List.of()).confirmedByUser(Set.of()).checkedFeeds())
                .containsExactly("ACME-A", "ACME-B");
    }

    private static EventType type(final String typeId, final String element, final String action) {
        return new EventType(typeId, typeId, null, element, action, null, null, null, null, List.of(), false,
                EventType.AGENT_KNOWLEDGE, "high", null, EventType.PROPOSED);
    }
}
