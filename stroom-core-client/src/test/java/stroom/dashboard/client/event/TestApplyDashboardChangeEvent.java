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
        assertThat(ApplyDashboardChangeEvent.decide(true, "abc", "abc", false)).isEqualTo(Decision.APPLY);
        // Hashes are hex, in either case.
        assertThat(ApplyDashboardChangeEvent.decide(true, "ABC", "abc", false)).isEqualTo(Decision.APPLY);
    }

    @Test
    void aDashboardThatCantBeEditedIsRefusedFirst() {
        assertThat(ApplyDashboardChangeEvent.decide(false, "abc", "abc", false)).isEqualTo(Decision.NOT_EDITABLE);
        assertThat(ApplyDashboardChangeEvent.decide(false, "other", "abc", true)).isEqualTo(Decision.NOT_EDITABLE);
    }

    @Test
    void aDashboardChangedSinceTheProposalIsRefused() {
        assertThat(ApplyDashboardChangeEvent.decide(true, "other", "abc", false)).isEqualTo(Decision.CHANGED);
        // Even with unsaved changes: saving them wouldn't make the proposal fit.
        assertThat(ApplyDashboardChangeEvent.decide(true, "other", "abc", true)).isEqualTo(Decision.CHANGED);
        assertThat(ApplyDashboardChangeEvent.decide(true, null, "abc", false)).isEqualTo(Decision.CHANGED);
    }

    @Test
    void unsavedChangesAreSavedFirstNeverAppliedOver() {
        assertThat(ApplyDashboardChangeEvent.decide(true, "abc", "abc", true)).isEqualTo(Decision.SAVE_FIRST);
        assertThat(ApplyDashboardChangeEvent.SAVE_FIRST)
                .startsWith("This dashboard has unsaved changes. Save them first, so you can go back to them if "
                            + "you don't want this change?");
    }
}
