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

import stroom.ai.impl.tools.ModelSideCalls;
import stroom.ai.impl.tools.timeline.EventCodes.CodeRole;
import stroom.ai.impl.tools.timeline.TimelineSessions.Role;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// An index's own event codes classified once by the model, its answer checked in code.
class TestEventCodes {

    @Test
    void theModelsAnswerIsCheckedEveryCodeAskedGetsARole() {
        final List<String> asked = new ArrayList<>();
        final ModelSideCalls model = stub("""
                Here you are: {"4624": {"role": "active"}, "4634": {"role": "idle"},
                "1102": {"role": "delete the logs", "kind": "audit log cleared"}}""", asked);
        final Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("4624", 10);
        counts.put("4634", 9);
        counts.put("1102", 1);
        counts.put("9999", 1);

        final Map<String, CodeRole> roles = EventCodes.ask(model, "Windows", counts, List.of()).orElseThrow();

        assertThat(roles).containsEntry("4624", new CodeRole("active", "4624", false))
                .containsEntry("4634", new CodeRole("idle", "4634", false))
                // A role that isn't one is an action; a code left out is an action of its own name.
                .containsEntry("1102", new CodeRole("action", "audit log cleared", false))
                .containsEntry("9999", new CodeRole("action", "9999", false));
        assertThat(asked.getFirst()).contains("Source: Windows").contains("\"4624\":10");
    }

    @Test
    void aPeriodsKindIsReadFromItsRoleOrItsKindAndAPeriodWithNoKindIsAnAction() {
        final ModelSideCalls model = stub("""
                {"20": {"role": "period_start", "kind": "Remote Session"}, "21": {"role": "period_end call"},
                "22": {"role": "period_end"}}""", new ArrayList<>());
        final Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("20", 3);
        counts.put("21", 2);
        counts.put("22", 1);

        final Map<String, CodeRole> roles = EventCodes.ask(model, "VPN", counts, List.of()).orElseThrow();

        assertThat(roles).containsEntry("20", new CodeRole("period_start", "remote_session", false))
                .containsEntry("21", new CodeRole("period_end", "call", false))
                .containsEntry("22", new CodeRole("action", "22", false));
        assertThat(EventCodes.role(roles.get("20").text())).isEqualTo(Role.PERIOD_START);
        assertThat(EventCodes.period(roles.get("21").text()).kind()).isEqualTo("call");
        assertThat(EventCodes.lines(roles, 5)).contains("20: period_start remote_session");
        assertThat(EventCodes.INSTRUCTIONS).contains("period_start <kind>");
    }

    @Test
    void aPeriodsKeyIsTakenOnlyOfTheFieldsGivenAtMostThreeWithTheirCase() {
        // Seen in eval case 119 (2026-11-27): the badge codes classified as on-site periods with no key, so an enter
        // at one site could be closed by a leave at another.
        final List<String> asked = new ArrayList<>();
        final ModelSideCalls model = stub("""
                {"badge.enter": {"role": "period_start on_site by Site, Zone"},
                 "badge.leave": {"role": "period_end on_site by Site, Nowhere"},
                 "vpn.up": {"role": "period_start remote_session by A, B, C, D"}}""", asked);
        final Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("badge.enter", 3);
        counts.put("badge.leave", 3);
        counts.put("vpn.up", 1);

        final Map<String, CodeRole> roles = EventCodes.ask(model, "Site access", counts, List.of("A", "B", "C", "D",
                "Site", "Zone")).orElseThrow();

        assertThat(roles.get("badge.enter")).isEqualTo(new CodeRole("period_start", "on_site", false, "Site, Zone"));
        assertThat(roles.get("badge.enter").text()).isEqualTo("period_start on_site by Site, Zone");
        assertThat(EventCodes.period(roles.get("badge.enter").text()).key()).isEqualTo("Site, Zone");
        // A field the index hasn't, or more than three: no key, the kind's own.
        assertThat(roles.get("badge.leave").key()).isNull();
        assertThat(roles.get("vpn.up").key()).isNull();
        assertThat(String.valueOf(asked)).contains("Its fields: [\"A\",\"B\",\"C\",\"D\",\"Site\",\"Zone\"]");
        assertThat(EventCodes.INSTRUCTIONS).contains("by <field>, <field>");
    }

    @Test
    void anAnswerThatCantBeReadOrNoModelGivesNothing() {
        assertThat(EventCodes.ask(stub("no idea", new ArrayList<>()), "Windows", Map.of("1", 1), List.of()))
                .isEmpty();
        assertThat(EventCodes.ask(ModelSideCalls.NONE, "Windows", Map.of("1", 1), List.of())).isEmpty();
    }

    @Test
    void codesNothingClassifiedAreGivenToTheCallingModelCommonestFirstWithTheRolesAndWhy() {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("lock", 11);
        counts.put("focus", 55);
        counts.put("clipboard", 127);
        counts.put("unlock", 11);

        final String text = EventCodes.unclassified("User activity", "EventType", counts, ModelSideCalls.NONE);

        assertThat(text).startsWith("Source 'User activity': its events have no Events XML or element field to go "
                                    + "by, and their codes (EventType) couldn't be classified (no model: this "
                                    + "session has no model a tool can call)")
                .contains("call again with the source's typeRoles, {\"<code>\": \"<role>\"}",
                        "\"idle\" (the user went idle", "\"focus\" (a window",
                        "A code left out is an action of its own name.")
                .contains("the commonest first: clipboard 127, focus 55, lock 11, unlock 11.")
                .endsWith("offer them as an AGENTS doc's '## Event types' lines (- <code>: <role>) where the index is, "
                          + "with propose_instructions.");
        // A model that answered, but not readably.
        assertThat(EventCodes.unclassified("Windows", "event.code", Map.of("4800", 1), stub("?", new ArrayList<>())))
                .contains("(the model's answer couldn't be read)");
        // At most the commonest codes asked of a model, the rest counted.
        final Map<String, Integer> many = new LinkedHashMap<>();
        for (int i = 0; i < EventCodes.MAX_CODES + 2; i++) {
            many.put("c" + i, 1_000 - i);
        }
        assertThat(EventCodes.unclassified("Windows", "event.code", many, ModelSideCalls.NONE))
                .contains("c99 901").doesNotContain("c100 ").contains(" and 2 more. ");
    }

    @Test
    void rolesAreTheTimelinesOwn() {
        assertThat(EventCodes.role("active")).isEqualTo(Role.IDLE_END);
        assertThat(EventCodes.role("Idle")).isEqualTo(Role.IDLE_START);
        assertThat(EventCodes.role("open")).isEqualTo(Role.OPEN);
        assertThat(EventCodes.role("close")).isEqualTo(Role.CLOSE);
        assertThat(EventCodes.role("action")).isEqualTo(Role.ACTION);
        assertThat(EventCodes.role("ignore")).isNull();
        assertThat(EventCodes.lines(Map.of("1102", new CodeRole("action", "log cleared", false)), 5))
                .containsExactly("1102: action (log cleared)");
    }

    private static ModelSideCalls stub(final String answer, final List<String> asked) {
        return new ModelSideCalls() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public String via() {
                return "a stub";
            }

            @Override
            public Answer ask(final Question question) {
                assertThat(question.system()).isEqualTo(EventCodes.INSTRUCTIONS);
                asked.add(question.user());
                return new Answer(answer, 1, 1);
            }
        };
    }
}
