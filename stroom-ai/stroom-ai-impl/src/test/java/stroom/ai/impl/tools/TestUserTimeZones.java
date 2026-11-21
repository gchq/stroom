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

package stroom.ai.impl.tools;

import stroom.ai.impl.tools.ToolTimeZone.Source;
import stroom.query.api.UserTimeZone;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestUserTimeZones {

    private static final ZoneId CANBERRA = ZoneId.of("Australia/Canberra");
    private static final ToolCaller MCP_CLIENT = new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "client");

    @Test
    void defaultIsTheUsersPreferredZoneId() {
        final ToolTimeZone zone = zones(UserTimeZone.fromId("Australia/Canberra")).resolve(null, mcp());

        assertThat(zone.zone()).isEqualTo(CANBERRA);
        assertThat(zone.source()).isEqualTo(Source.PREFERENCE);
        assertThat(zone.stated()).startsWith("Times are in Australia/Canberra (+1")
                .endsWith("), the user's Stroom time zone.");
    }

    @Test
    void defaultIsTheUsersPreferredOffset() {
        final ToolTimeZone zone = zones(UserTimeZone.fromOffset(5, 30)).resolve("", mcp());

        assertThat(zone.zone()).isEqualTo(ZoneOffset.ofHoursMinutes(5, 30));
        assertThat(zone.label()).isEqualTo("+05:30");
    }

    @Test
    void defaultIsUtcWithoutAPreference() {
        final ToolTimeZone zone = UserTimeZones.UTC_ONLY.resolve(null, chat(null));

        assertThat(zone.isUtc()).isTrue();
        assertThat(zone.stated()).isEqualTo("Times are in UTC, the user's Stroom time zone.");
        assertThat(zones(UserTimeZone.utc()).resolve(null, mcp()).isUtc()).isTrue();
    }

    @Test
    void localPreferenceInAChatIsTheBrowsersZone() {
        final ToolTimeZone zone = zones(UserTimeZone.local()).resolve(null, chat("Australia/Canberra"));

        assertThat(zone.zone()).isEqualTo(CANBERRA);
        assertThat(zone.source()).isEqualTo(Source.BROWSER);
        assertThat(zone.stated()).endsWith("), the user's browser's time zone.");
    }

    @Test
    void localPreferenceOverMcpIsUtcAndSaysSo() {
        final ToolTimeZone zone = zones(UserTimeZone.local()).resolve(null, mcp());

        assertThat(zone.isUtc()).isTrue();
        assertThat(zone.note()).isEqualTo(UserTimeZones.BROWSER_NOT_KNOWN);
        assertThat(zone.stated()).isEqualTo("Times are in UTC. " + UserTimeZones.BROWSER_NOT_KNOWN);
        assertThat(zone.structured()).containsEntry("time_zone", "UTC")
                .containsEntry("time_zone_note", UserTimeZones.BROWSER_NOT_KNOWN);
    }

    @Test
    void localInAChatIsTheBrowsersZoneWhateverThePreference() {
        final ToolTimeZone zone = zones(UserTimeZone.fromId("Europe/London")).resolve("local",
                chat("Australia/Canberra"));

        assertThat(zone.zone()).isEqualTo(CANBERRA);
    }

    @Test
    void localOverMcpIsTheUsersPreferredZone() {
        assertThat(zones(UserTimeZone.fromId("Europe/London")).resolve("LOCAL", mcp()).zone())
                .isEqualTo(ZoneId.of("Europe/London"));
        assertThat(zones(UserTimeZone.local()).resolve("local", mcp()).note())
                .isEqualTo(UserTimeZones.BROWSER_NOT_KNOWN);
    }

    @Test
    void givenUtcAndGmtAreUtc() {
        final UserTimeZones zones = zones(UserTimeZone.fromId("Australia/Canberra"));

        assertThat(zones.resolve("UTC", mcp()).isUtc()).isTrue();
        assertThat(zones.resolve("gmt", mcp()).isUtc()).isTrue();
        assertThat(zones.resolve("Z", mcp()).isUtc()).isTrue();
        assertThat(zones.resolve("+00:00", mcp()).isUtc()).isTrue();
        assertThat(zones.resolve("UTC", mcp()).stated()).isEqualTo("Times are in UTC.");
    }

    @Test
    void givenZoneIdInAnyCase() {
        final UserTimeZones zones = UserTimeZones.UTC_ONLY;

        assertThat(zones.resolve("Australia/Canberra", mcp()).zone()).isEqualTo(CANBERRA);
        assertThat(zones.resolve("australia/canberra", mcp()).zone()).isEqualTo(CANBERRA);
        assertThat(zones.resolve("Australia/Canberra", mcp()).source()).isEqualTo(Source.GIVEN);
    }

    @Test
    void givenOffsets() {
        final UserTimeZones zones = UserTimeZones.UTC_ONLY;

        assertThat(zones.resolve("+11:00", mcp()).zone()).isEqualTo(ZoneOffset.ofHours(11));
        assertThat(zones.resolve("-05:30", mcp()).zone()).isEqualTo(ZoneOffset.ofHoursMinutes(-5, -30));
        assertThat(zones.resolve("+11", mcp()).zone()).isEqualTo(ZoneOffset.ofHours(11));
        assertThat(zones.resolve("+11:00", mcp()).stated()).isEqualTo("Times are in +11:00.");
        assertThat(zones.resolve("UTC+11:00", mcp()).zone().normalized()).isEqualTo(ZoneOffset.ofHours(11));
    }

    @Test
    void invalidZoneIsRefusedSayingWhatToGive() {
        assertThatThrownBy(() -> UserTimeZones.UTC_ONLY.resolve("Mars/Olympus", mcp()))
                .isInstanceOf(ToolRefusal.class)
                .hasMessage("unknown time zone 'Mars/Olympus': give local, UTC, a zone id such as Europe/London, "
                            + "or an offset such as +01:00");
        assertThatThrownBy(() -> UserTimeZones.UTC_ONLY.resolve("+25:00", mcp()))
                .isInstanceOf(ToolRefusal.class);
    }

    @Test
    void unreadablePreferredZoneIsUtcAndSaysSo() {
        final ToolTimeZone zone = zones(UserTimeZone.fromId("Not/AZone")).resolve(null, mcp());

        assertThat(zone.isUtc()).isTrue();
        assertThat(zone.note()).contains("can't be read");
    }

    @Test
    void zoneAsStroomUserTimeZone() {
        assertThat(new ToolTimeZone(CANBERRA, Source.GIVEN, null).userTimeZone())
                .isEqualTo(UserTimeZone.fromId("Australia/Canberra"));
        assertThat(new ToolTimeZone(ZoneOffset.ofHoursMinutes(-5, -30), Source.GIVEN, null).userTimeZone())
                .isEqualTo(UserTimeZone.fromOffset(-5, -30));
        assertThat(ToolTimeZone.UTC.userTimeZone()).isEqualTo(UserTimeZone.utc());
        assertThat(ToolTimeZone.UTC.dateTimeSettings(1L).getReferenceTime()).isEqualTo(1L);
    }

    private static UserTimeZones zones(final UserTimeZone preference) {
        return new UserTimeZones(() -> Optional.of(preference));
    }

    private static ToolCallContext chat(final String browserZone) {
        return ToolCallContext.builder(ToolSession.chat(1)).localZoneId(browserZone).build();
    }

    private static ToolCallContext mcp() {
        return ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP, "s1", MCP_CLIENT)).build();
    }
}
