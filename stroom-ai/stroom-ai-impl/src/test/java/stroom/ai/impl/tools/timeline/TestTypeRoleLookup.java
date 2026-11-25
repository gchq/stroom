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

import stroom.ai.impl.tools.catalogue.TimelineRole;
import stroom.ai.impl.tools.catalogue.TimelineRoles;
import stroom.ai.impl.tools.instructions.InstructionRules.TypeRoleRule;
import stroom.ai.impl.tools.timeline.EventXmlClassifier.Classified;
import stroom.ai.impl.tools.timeline.EventXmlClassifier.Route;
import stroom.ai.impl.tools.timeline.TimelineClassification.RoleOverride;
import stroom.ai.impl.tools.timeline.TimelineSessions.Role;

import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/// An event type's timeline role, decided once per type: the call's or skill's, the catalogue of the stream's
/// translation, an AGENTS doc's, else the type's own.
class TestTypeRoleLookup {

    private static final Map<String, TimelineRoles.Role> CATALOGUE = Map.of(
            "vpn-up", new TimelineRoles.Role("vpn-up", TimelineRole.ACTIVE, "authenticate", "given"),
            "doc-read", new TimelineRoles.Role("doc-read", TimelineRole.ACTION, "view", "schema"),
            "noise", new TimelineRoles.Role("noise", TimelineRole.IGNORE, "event", "given"),
            "win-focus", new TimelineRoles.Role("win-focus", TimelineRole.FOCUS, "event", "given"));

    @Test
    void theCatalogueOfTheStreamsTranslationGivesATypesRoleAndKind() {
        final TypeRoleLookup lookup = new TypeRoleLookup(Map.of(), streamId -> "9".equals(streamId)
                ? CATALOGUE
                : Map.of(), Map.of());

        assertThat(lookup.decide(0, "9", code("vpn-up")).orElseThrow().role()).isEqualTo(Role.IDLE_END);
        final Classified read = lookup.decide(0, "9", code("doc-read")).orElseThrow();
        assertThat(read.role()).isEqualTo(Role.ACTION);
        assertThat(read.kind()).isEqualTo("view");
        assertThat(lookup.decide(0, "9", code("noise"))).isEmpty();
        // Another stream's translation has no catalogue: the type keeps its own (an action of its code).
        assertThat(lookup.decide(0, "10", code("vpn-up")).orElseThrow().route()).isEqualTo(Route.EVENT_CODE);
        assertThat(lookup.origins().get(0)).containsOnlyKeys(TypeRoleLookup.FROM_CATALOGUE,
                TypeRoleLookup.FROM_CODE);
        assertThat(lookup.settles(0, "9", "VPN-UP")).isTrue();
        assertThat(lookup.settles(0, "10", "vpn-up")).isFalse();
        assertThat(lookup.settles(0, "9", null)).isFalse();
    }

    @Test
    void theCallOrSkillWinsThenTheCatalogueThenTheAgentsDocThenTheSchema() {
        final TypeRoleLookup lookup = new TypeRoleLookup(
                Map.of(0, Map.of("vpn-up", new RoleOverride(Role.FOCUS, "call"), "Authenticate/Logon",
                        new RoleOverride(null, "skill"))),
                streamId -> CATALOGUE,
                Map.of(0, Map.of("doc-read", new TypeRoleRule(TimelineRole.IDLE, "/"), "View",
                        new TypeRoleRule(TimelineRole.OPEN, "/Indexes"))));

        assertThat(lookup.decide(0, "9", code("vpn-up")).orElseThrow().role()).isEqualTo(Role.FOCUS);
        // The skill ignores the schema's logon.
        assertThat(lookup.decide(0, "9", element("Authenticate", "Logon", null))).isEmpty();
        // The catalogue's before the AGENTS doc's.
        assertThat(lookup.decide(0, "9", code("doc-read")).orElseThrow().role()).isEqualTo(Role.ACTION);
        // The AGENTS doc's before the schema's (an element and no TypeId).
        assertThat(lookup.decide(0, "9", element("View", null, null)).orElseThrow().role()).isEqualTo(Role.OPEN);
        // The schema's, where nothing else says.
        assertThat(lookup.decide(0, "9", element("Process", "Terminate", null)).orElseThrow().role())
                .isEqualTo(Role.CLOSE);
        assertThat(lookup.origins().get(0)).containsOnlyKeys(TypeRoleLookup.FROM_CALL, TypeRoleLookup.FROM_SKILL,
                TypeRoleLookup.FROM_CATALOGUE, TypeRoleLookup.FROM_AGENTS, TypeRoleLookup.FROM_SCHEMA);
        // Another source has none of the call's.
        assertThat(lookup.decide(1, "9", code("vpn-up")).orElseThrow().role()).isEqualTo(Role.IDLE_END);
    }

    @Test
    void aTypeIsDecidedOncePerSourceAndTranslationNeverPerEvent() {
        final AtomicInteger asked = new AtomicInteger();
        final Map<String, TypeRoleRule> agents = new CountingMap(asked);
        final TypeRoleLookup lookup = new TypeRoleLookup(Map.of(), streamId -> CATALOGUE, Map.of(0, agents));

        for (int i = 0; i < 1_000; i++) {
            lookup.decide(0, "9", element("View", null, "page-view"));
        }

        assertThat(asked).hasValue(1);
        assertThat(lookup.origins().get(0).get(TypeRoleLookup.FROM_SCHEMA)).containsExactly("page-view");
    }

    @Test
    void withNothingGivenATypeKeepsItsOwnAndAnUnclassifiedOneIsAnAction() {
        final TypeRoleLookup lookup = TypeRoleLookup.of(Map.of());
        final Classified none = new Classified(null, null, null, Role.ACTION, "event", null, null, null, Map.of(),
                Route.NONE);

        assertThat(lookup.decide(0, null, none)).contains(none);
        assertThat(lookup.decide(0, null, code("x"))).isPresent();
        assertThat(lookup.origins().get(0)).containsOnlyKeys(TypeRoleLookup.FROM_NONE, TypeRoleLookup.FROM_CODE);
    }

    // ------------------------------------------------------------------------------------------------

    private static Classified code(final String code) {
        return new Classified(null, null, code, Role.ACTION, code, null, null, null, Map.of(), Route.EVENT_CODE);
    }

    private static Classified element(final String element, final String action, final String typeId) {
        return EventXmlClassifier.of(element, action, typeId, null, null, null, Map.of(), Route.INDEX_FIELDS);
    }


    // --------------------------------------------------------------------------------


    // An AGENTS doc's roles that count how often they are read.
    private static final class CountingMap extends AbstractMap<String, TypeRoleRule> {

        private final AtomicInteger asked;

        private CountingMap(final AtomicInteger asked) {
            this.asked = asked;
        }

        @Override
        public Set<Entry<String, TypeRoleRule>> entrySet() {
            asked.incrementAndGet();
            return Set.of(Map.entry("Other", new TypeRoleRule(TimelineRole.FOCUS, "/")));
        }
    }
}
