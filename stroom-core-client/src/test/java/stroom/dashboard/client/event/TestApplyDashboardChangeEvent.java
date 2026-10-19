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

import static org.assertj.core.api.Assertions.assertThat;

class TestApplyDashboardChangeEvent {

    @Test
    void aChangeIsAppliedOnlyToAnEditableUnchangedDashboard() {
        assertThat(ApplyDashboardChangeEvent.decide(true, "abc", "abc")).isEqualTo(Decision.APPLY);
        // Hashes are hex, in either case.
        assertThat(ApplyDashboardChangeEvent.decide(true, "ABC", "abc")).isEqualTo(Decision.APPLY);
    }

    @Test
    void aDashboardThatCantBeEditedIsRefusedFirst() {
        assertThat(ApplyDashboardChangeEvent.decide(false, "abc", "abc")).isEqualTo(Decision.NOT_EDITABLE);
        assertThat(ApplyDashboardChangeEvent.decide(false, "other", "abc")).isEqualTo(Decision.NOT_EDITABLE);
    }

    @Test
    void aDashboardChangedSinceTheProposalIsRefused() {
        assertThat(ApplyDashboardChangeEvent.decide(true, "other", "abc")).isEqualTo(Decision.CHANGED);
        assertThat(ApplyDashboardChangeEvent.decide(true, null, "abc")).isEqualTo(Decision.CHANGED);
    }

    @Test
    void aChangeIsAppliedOverUnsavedChangesWithoutAskingToSave() {
        // The hash is of the dashboard as the user has it, unsaved changes included, so a change proposed against
        // them is applied as it is: there is no decision to save first.
        assertThat(Decision.values()).containsExactly(Decision.NOT_EDITABLE, Decision.CHANGED, Decision.APPLY);
    }
}
