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

package stroom.ai.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestAppliedDocumentationChanges {

    private static final String KEY = "12:docs-1";
    private static final String BASE = "# Logons\n\nOld text.\n";
    private static final String PROPOSED = "# Logons\n\nWho logged on.\n";

    private final AppliedDocumentationChanges changes = new AppliedDocumentationChanges();

    @Test
    void aChangeIsOnlyAppliedToTheTextItWasProposedAgainstInAnEditableTab() {
        assertThat(AppliedDocumentationChanges.applyProblem(false, BASE, BASE)).isNull();
        // Line endings aside.
        assertThat(AppliedDocumentationChanges.applyProblem(false, BASE.replace("\n", "\r\n"), BASE)).isNull();
        // No documentation yet.
        assertThat(AppliedDocumentationChanges.applyProblem(false, "", null)).isNull();

        assertThat(AppliedDocumentationChanges.applyProblem(false, BASE + "More.\n", BASE))
                .isEqualTo(AppliedDocumentationChanges.CHANGED);
        assertThat(AppliedDocumentationChanges.applyProblem(true, BASE, BASE))
                .isEqualTo(AppliedDocumentationChanges.NOT_EDITABLE);
    }

    @Test
    void undoingPutsBackWhatTheTabHeldWhileItHoldsTheAppliedText() {
        // Applied in part, over the tab's own text.
        changes.applied(KEY, BASE, "# Logons\n\nOld text, in part.\n");

        assertThat(changes.undoText(KEY, "# Logons\n\nOld text, in part.\n", BASE, PROPOSED)).isEqualTo(BASE);
        // Edited since.
        assertThat(changes.undoText(KEY, "# Logons\n\nEdited.\n", BASE, PROPOSED)).isNull();
        assertThat(changes.undoText(KEY, PROPOSED, BASE, PROPOSED)).isNull();
    }

    @Test
    void aChangeNotRecordedHereIsUndoneOnlyIfTheTabHoldsTheProposedText() {
        // E.g. the page has been loaded again since it was applied.
        assertThat(changes.undoText(KEY, PROPOSED, BASE, PROPOSED)).isEqualTo(BASE);
        assertThat(changes.undoText(KEY, PROPOSED, null, PROPOSED)).isEmpty();
        assertThat(changes.undoText(KEY, "Something else", BASE, PROPOSED)).isNull();
        assertThat(changes.undoText(KEY, null, BASE, null)).isNull();
    }

    @Test
    void undoApplyIsWithdrawnUntilTheChangeIsAppliedAgainOrUndone() {
        assertThat(changes.isWithdrawn(KEY)).isFalse();
        changes.withdraw(KEY);
        assertThat(changes.isWithdrawn(KEY)).isTrue();

        changes.applied(KEY, BASE, PROPOSED);
        assertThat(changes.isWithdrawn(KEY)).isFalse();

        changes.withdraw(KEY);
        changes.undone(KEY);
        assertThat(changes.isWithdrawn(KEY)).isFalse();
        // Forgotten, so only the proposed text can be undone.
        assertThat(changes.undoText(KEY, PROPOSED, BASE, PROPOSED)).isEqualTo(BASE);
    }
}
