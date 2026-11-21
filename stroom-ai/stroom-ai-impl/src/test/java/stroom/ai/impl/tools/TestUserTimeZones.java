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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestUserTimeZones {

    private static final ZoneId CANBERRA = ZoneId.of("Australia/Canberra");
    private static final ToolCaller MCP_CLIENT = new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "client");

    @Test
    void defaultInAChatIsTheDevicesZone() {
        final ToolTimeZone zone = UserTimeZones.resolve(null, chat("Australia/Canberra"));

        assertThat(zone.zone()).isEqualTo(CANBERRA);
        assertThat(zone.source()).isEqualTo(Source.BROWSER);
        assertThat(zone.stated()).startsWith("Times are in Australia/Canberra (+1")
                .endsWith("), the user's device's time zone.");
        assertThat(UserTimeZones.resolve("", chat("+05:30")).zone()).isEqualTo(ZoneOffset.ofHoursMinutes(5, 30));
    }

    @Test
    void defaultOverMcpIsUtcAndSaysSo() {
        final ToolTimeZone zone = UserTimeZones.resolve(null, mcp());

        assertThat(zone.isUtc()).isTrue();
        assertThat(zone.source()).isEqualTo(Source.FALLBACK);
        assertThat(zone.note()).isEqualTo(UserTimeZones.DEVICE_NOT_KNOWN);
        assertThat(zone.stated()).isEqualTo("Times are in UTC. " + UserTimeZones.DEVICE_NOT_KNOWN);
        assertThat(zone.structured()).containsEntry("time_zone", "UTC")
                .containsEntry("time_zone_source", "fallback")
                .containsEntry("time_zone_note", UserTimeZones.DEVICE_NOT_KNOWN);
    }

    @Test
    void defaultInAChatWhoseDevicesZoneIsntKnownIsUtcAndSaysSo() {
        assertThat(UserTimeZones.resolve(null, chat(null)).note()).isEqualTo(UserTimeZones.DEVICE_NOT_KNOWN);
        assertThat(UserTimeZones.resolve(null, chat("Not/AZone")).isUtc()).isTrue();
    }

    @Test
    void localIsTheDevicesZoneOrUtcSayingSo() {
        assertThat(UserTimeZones.resolve("LOCAL", chat("Australia/Canberra")).zone()).isEqualTo(CANBERRA);
        assertThat(UserTimeZones.resolve("local", mcp()).note()).isEqualTo(UserTimeZones.DEVICE_NOT_KNOWN);
    }

    @Test
    void aGivenZoneWinsOverTheDevices() {
        final ToolTimeZone zone = UserTimeZones.resolve("Europe/London", chat("Australia/Canberra"));

        assertThat(zone.zone()).isEqualTo(ZoneId.of("Europe/London"));
        assertThat(zone.source()).isEqualTo(Source.GIVEN);
        assertThat(UserTimeZones.resolve("UTC", chat("Australia/Canberra")).isUtc()).isTrue();
    }

    @Test
    void givenUtcAndGmtAreUtc() {
        assertThat(UserTimeZones.resolve("UTC", mcp()).isUtc()).isTrue();
        assertThat(UserTimeZones.resolve("gmt", mcp()).isUtc()).isTrue();
        assertThat(UserTimeZones.resolve("Z", mcp()).isUtc()).isTrue();
        assertThat(UserTimeZones.resolve("+00:00", mcp()).isUtc()).isTrue();
        assertThat(UserTimeZones.resolve("UTC", mcp()).stated()).isEqualTo("Times are in UTC.");
    }

    @Test
    void givenZoneIdInAnyCase() {
        assertThat(UserTimeZones.resolve("Australia/Canberra", mcp()).zone()).isEqualTo(CANBERRA);
        assertThat(UserTimeZones.resolve("australia/canberra", mcp()).zone()).isEqualTo(CANBERRA);
        assertThat(UserTimeZones.resolve("Australia/Canberra", mcp()).source()).isEqualTo(Source.GIVEN);
    }

    @Test
    void givenOffsets() {
        assertThat(UserTimeZones.resolve("+11:00", mcp()).zone()).isEqualTo(ZoneOffset.ofHours(11));
        assertThat(UserTimeZones.resolve("-05:30", mcp()).zone()).isEqualTo(ZoneOffset.ofHoursMinutes(-5, -30));
        assertThat(UserTimeZones.resolve("+11", mcp()).zone()).isEqualTo(ZoneOffset.ofHours(11));
        assertThat(UserTimeZones.resolve("+11:00", mcp()).stated()).isEqualTo("Times are in +11:00.");
        assertThat(UserTimeZones.resolve("UTC+11:00", mcp()).zone().normalized()).isEqualTo(ZoneOffset.ofHours(11));
    }

    @Test
    void invalidZoneIsRefusedSayingWhatToGive() {
        assertThatThrownBy(() -> UserTimeZones.resolve("Mars/Olympus", mcp()))
                .isInstanceOf(ToolRefusal.class)
                .hasMessage("unknown time zone 'Mars/Olympus': give local, UTC, a zone id such as Europe/London, "
                            + "or an offset such as +01:00");
        assertThatThrownBy(() -> UserTimeZones.resolve("+25:00", mcp()))
                .isInstanceOf(ToolRefusal.class);
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

    private static ToolCallContext chat(final String browserZone) {
        return ToolCallContext.builder(ToolSession.chat(1)).localZoneId(browserZone).build();
    }

    private static ToolCallContext mcp() {
        return ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP, "s1", MCP_CLIENT)).build();
    }
}
