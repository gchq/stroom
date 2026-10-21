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


package stroom.dashboard.client.event;

import stroom.dashboard.client.event.ApplyDashboardChangeEvent.Decision;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestApplyDashboardChangeEvent {

    @Test
    void aChangeIsAppliedOnlyToAnEditableUnchangedDashboard() {
        assertThat(ApplyDashboardChangeEvent.decide(true, "abc", "abc", null)).isEqualTo(Decision.APPLY);
        // Hashes are hex, in either case.
        assertThat(ApplyDashboardChangeEvent.decide(true, "ABC", "abc", null)).isEqualTo(Decision.APPLY);
    }

    @Test
    void aDashboardThatCantBeEditedIsRefusedFirst() {
        assertThat(ApplyDashboardChangeEvent.decide(false, "abc", "abc", null)).isEqualTo(Decision.NOT_EDITABLE);
        assertThat(ApplyDashboardChangeEvent.decide(false, "other", "abc", null)).isEqualTo(Decision.NOT_EDITABLE);
    }

    @Test
    void aDashboardChangedSinceTheProposalIsRefused() {
        assertThat(ApplyDashboardChangeEvent.decide(true, "other", "abc", null)).isEqualTo(Decision.CHANGED);
        assertThat(ApplyDashboardChangeEvent.decide(true, null, "abc", null)).isEqualTo(Decision.CHANGED);
    }

    @Test
    void aChangeProposedBeforeChangesTheChatAppliedIsAppliedOnTopOfThem() {
        // Two changes proposed against "abc": the first, applied, made it "def", and another then made it "ghi".
        final Map<String, String> appliedFrom = Map.of("def", "abc", "ghi", "def");

        assertThat(ApplyDashboardChangeEvent.decide(true, "def", "abc", appliedFrom)).isEqualTo(Decision.APPLY);
        assertThat(ApplyDashboardChangeEvent.decide(true, "ghi", "ABC", appliedFrom)).isEqualTo(Decision.APPLY);
        assertThat(ApplyDashboardChangeEvent.decide(true, "ghi", "def", appliedFrom)).isEqualTo(Decision.APPLY);
        assertThat(ApplyDashboardChangeEvent.decide(false, "def", "abc", appliedFrom))
                .isEqualTo(Decision.NOT_EDITABLE);
    }

    @Test
    void aChangeIsRefusedIfTheDashboardWasChangedSomeOtherWay() {
        final Map<String, String> appliedFrom = Map.of("def", "abc");

        // Changed by the user after the applied change, or before it.
        assertThat(ApplyDashboardChangeEvent.decide(true, "edited", "abc", appliedFrom)).isEqualTo(Decision.CHANGED);
        assertThat(ApplyDashboardChangeEvent.decide(true, "def", "other", appliedFrom)).isEqualTo(Decision.CHANGED);
        assertThat(ApplyDashboardChangeEvent.decide(true, null, "abc", appliedFrom)).isEqualTo(Decision.CHANGED);
        assertThat(ApplyDashboardChangeEvent.decide(true, "def", "abc", Map.of())).isEqualTo(Decision.CHANGED);
    }

    @Test
    void appliedChangesThatGoRoundInALoopDontStopTheDecision() {
        // A change and another that put the dashboard back.
        final Map<String, String> appliedFrom = Map.of("def", "abc", "abc", "def");

        assertThat(ApplyDashboardChangeEvent.decide(true, "abc", "other", appliedFrom)).isEqualTo(Decision.CHANGED);
        assertThat(ApplyDashboardChangeEvent.decide(true, "abc", "def", appliedFrom)).isEqualTo(Decision.APPLY);
    }

    @Test
    void aChangeIsAppliedOverUnsavedChangesWithoutAskingToSave() {
        // The hash is of the dashboard as the user has it, unsaved changes included, so a change proposed against
        // them is applied as it is: there is no decision to save first.
        assertThat(Decision.values()).containsExactly(Decision.NOT_EDITABLE, Decision.CHANGED, Decision.APPLY);
    }
}
