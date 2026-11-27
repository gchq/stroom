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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A period's kind, its key, the key's values from an event, and a period role's text form.
class TestTimelinePeriod {

    @Test
    void aKindIsAFixedIdentifier() {
        assertThat(TimelinePeriod.kindOf(" Remote Session ")).isEqualTo("remote_session");
        assertThat(TimelinePeriod.kindOf("badge-in/out")).isEqualTo("badge_in_out");
        assertThat(TimelinePeriod.kindOf("  -- ")).isNull();
        assertThat(TimelinePeriod.kindOf(null)).isNull();
        assertThat(TimelinePeriod.kindOf("x".repeat(60))).hasSize(TimelinePeriod.MAX_KIND_CHARS);
        assertThat(new TimelinePeriod("On site", " ").key()).isNull();
        assertThat(new TimelinePeriod("on_site", null).words()).isEqualTo("on site");
    }

    @Test
    void eachWellKnownKindHasItsKeyAndACustomOneTheHost() {
        assertThat(TimelinePeriod.defaultKey(TimelinePeriod.REMOTE_SESSION)).isEqualTo("host|SessionId");
        assertThat(TimelinePeriod.defaultKey(TimelinePeriod.ON_SITE)).isEqualTo("Location/Site|Location/Building");
        assertThat(TimelinePeriod.defaultKey("printer_jam")).isEqualTo(TimelinePeriod.HOST);
        assertThat(new TimelinePeriod("on_site", "Location/Room").effectiveKey()).isEqualTo("Location/Room");
        assertThat(new TimelinePeriod("call", null).wellKnown()).isTrue();
        assertThat(new TimelinePeriod("printer_jam", null).wellKnown()).isFalse();
    }

    @Test
    void aKeysValuesAreTheFirstAlternativeOfEachPartWithOne() {
        final Map<String, String> keys = Map.of("EventSource/Door/Location/Site", "Canberra",
                "Device/Name", "Kingston 64GB", "Data[SessionId]", "71300001");

        // A path matches a key path ending in it; a Data entry by its name.
        assertThat(TimelinePeriod.values("Location/Site|Location/Building", "door-a1", keys))
                .containsExactly("Canberra");
        assertThat(TimelinePeriod.values("host, Device/Id|Device/Name", "host01", keys))
                .containsExactly("host01", "Kingston 64GB");
        assertThat(TimelinePeriod.values("SessionId", null, keys)).containsExactly("71300001");
        // A part with no value is left out; none at all is the kind alone.
        assertThat(TimelinePeriod.values("host, RunAs/Id", "host01", keys)).containsExactly("host01");
        assertThat(TimelinePeriod.values("Location/Room", null, keys)).isEmpty();
        assertThat(TimelinePeriod.parts(" host , a|b|")).containsExactly(List.of("host"), List.of("a", "b"));
    }

    @Test
    void aPeriodRolesTextGivesItsKindAndKeyAndAnotherRoleItsName() {
        assertThat(TimelinePeriod.parse("period_start remote_session"))
                .contains(new TimelinePeriod.RoleText(TimelineRole.PERIOD_START,
                        new TimelinePeriod("remote_session", null)));
        final TimelinePeriod.RoleText door = TimelinePeriod.parse("Period_End on site by Location/Building")
                .orElseThrow();
        assertThat(door.period()).isEqualTo(new TimelinePeriod("on_site", "Location/Building"));
        assertThat(door.text()).isEqualTo("period_end on_site by Location/Building");
        // No kind: no period role.
        assertThat(TimelinePeriod.parse("period_start")).isEmpty();
        assertThat(TimelinePeriod.role("period_start")).isEmpty();
        assertThat(TimelinePeriod.role("Focus")).contains(new TimelinePeriod.RoleText(TimelineRole.FOCUS, null));
        assertThat(TimelinePeriod.role("sideways")).isEmpty();
    }

    @Test
    void eachRoleSaysWhatItImplies() {
        assertThat(List.of(TimelineRole.values())).filteredOn(TimelineRole::impliesActivity)
                .containsExactly(TimelineRole.FOCUS, TimelineRole.OPEN, TimelineRole.CLOSE, TimelineRole.ACTION);
        assertThat(List.of(TimelineRole.values())).filteredOn(TimelineRole::isPeriod)
                .containsExactly(TimelineRole.PERIOD_START, TimelineRole.PERIOD_END);
    }
}
