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

package stroom.ai.impl.tools.compare;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestPeerGroups {

    private static final String EVENT = "<?xml version=\"1.1\" encoding=\"UTF-8\"?>"
                                        + "<Events xmlns=\"event-logging:3\"><Event><EventSource><User><Id>jsmith</Id>"
                                        + "<UserDetails><Organisation> Finance </Organisation><Unit>Payroll</Unit>"
                                        + "</UserDetails></User></EventSource><EventDetail><Process><User>"
                                        + "<UserDetails><Organisation>Elsewhere</Organisation></UserDetails>"
                                        + "</User></Process></EventDetail></Event></Events>";

    @Test
    void readsTheSourceUsersElement() {
        assertThat(PeerGroups.groupOf(EVENT, "Organisation")).isEqualTo("Finance");
        assertThat(PeerGroups.groupOf(EVENT, "Unit")).isEqualTo("Payroll");
    }

    @Test
    void anElementNotThereOrXmlNotReadable() {
        assertThat(PeerGroups.groupOf(EVENT, "Role")).isNull();
        assertThat(PeerGroups.groupOf("<Events><Event>", "Organisation")).isNull();
        assertThat(PeerGroups.groupOf("<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><Events/>",
                "Organisation")).isNull();
    }

    @Test
    void onlyWhereSomeoneWorks() {
        assertThat(PeerGroups.ELEMENTS).containsExactlyInAnyOrder("Organisation", "Unit", "Group", "Role");
        assertThat(CompareActivityTool.peerElement(null)).isEqualTo("Organisation");
        assertThat(CompareActivityTool.peerElement("unit")).isEqualTo("Unit");
        assertThatThrownBy(() -> CompareActivityTool.peerElement("Nationality"))
                .hasMessageContaining("never by a personal characteristic");
    }
}
