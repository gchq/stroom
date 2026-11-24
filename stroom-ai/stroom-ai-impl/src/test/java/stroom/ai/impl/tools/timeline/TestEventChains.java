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

import stroom.ai.impl.tools.timeline.EventChains.Entry;
import stroom.ai.impl.tools.timeline.EventChains.Parsed;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// A timeline skill's Event chains section: read, and added to without repeating a line.
class TestEventChains {

    private static final String SKILL = """
            # User activity timeline

            Builds a timeline.

            ## Steps

            1. Build it.

            ## Output

            A timeline.
            """;

    @Test
    void pairsAndRolesAreReadAndOtherBulletsAreSaid() {
        final Parsed parsed = EventChains.parse("""
                - Proxy: pair Tun-Ascend -> Tun-Recede by Tunnel/Id
                - `Endpoint`: pair Process/Execute -> Process/Terminate by ProcessId label Command
                - Windows: Lock-Workstation is idle
                - any: Heartbeat is ignore (not the user's)
                - Windows: lock and unlock are a pair
                Some prose, not a bullet.
                """);

        assertThat(parsed.entries()).containsExactly(
                Entry.pair("Proxy", "Tun-Ascend", "Tun-Recede", "Tunnel/Id", null),
                Entry.pair("Endpoint", "Process/Execute", "Process/Terminate", "ProcessId", "Command"),
                Entry.role("Windows", "Lock-Workstation", "idle"),
                Entry.role("any", "Heartbeat", "ignore"));
        assertThat(parsed.unread()).containsExactly("- Windows: lock and unlock are a pair");
        assertThat(parsed.entries().get(3).appliesTo("Anything")).isTrue();
        assertThat(parsed.entries().get(0).appliesTo("proxy")).isTrue();
        assertThat(parsed.bySource()).containsOnlyKeys("Proxy", "Endpoint", "Windows", "any");
    }

    @Test
    void aSkillWithoutTheSectionGetsItBeforeItsOutput() {
        final String changed = EventChains.withEntries(SKILL, List.of(Entry.pair("Proxy", "Tun-Ascend",
                "Tun-Recede", "Tunnel/Id", null)));

        assertThat(changed).contains("""
                1. Build it.

                ## Event chains

                - Proxy: pair Tun-Ascend -> Tun-Recede by Tunnel/Id

                ## Output
                """);
        assertThat(EventChains.parse(changed.substring(changed.indexOf("## Event chains") + 15))
                .entries()).hasSize(1);
    }

    @Test
    void aLineTheSectionHasIsntAddedAgain() {
        final String once = EventChains.withEntries(SKILL, List.of(Entry.role("Windows", "Lock-Workstation",
                "idle")));
        final String twice = EventChains.withEntries(once, List.of(Entry.role("Windows", "Lock-Workstation",
                "idle"), Entry.pair("Proxy", "A", "B", null, null)));

        assertThat(twice.split("Lock-Workstation", -1)).hasSize(2);
        assertThat(twice).contains("- Windows: Lock-Workstation is idle\n- Proxy: pair A -> B\n\n## Output");
    }

    @Test
    void aSkillWithNoOutputGetsTheSectionAtItsEnd() {
        final String changed = EventChains.withEntries("# A skill\n\nDoes it.\n\n## Steps\n\n1. Go.\n",
                List.of(Entry.role("Proxy", "Ping", "ignore")));

        assertThat(changed).endsWith("1. Go.\n\n## Event chains\n\n- Proxy: Ping is ignore\n");
    }
}
