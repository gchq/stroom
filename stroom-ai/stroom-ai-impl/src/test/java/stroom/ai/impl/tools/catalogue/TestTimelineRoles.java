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
