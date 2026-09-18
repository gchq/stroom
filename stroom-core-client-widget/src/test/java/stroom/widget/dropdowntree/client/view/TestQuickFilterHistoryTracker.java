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

package stroom.widget.dropdowntree.client.view;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestQuickFilterHistoryTracker {

    private final List<String> recorded = new ArrayList<>();
    private final QuickFilterHistoryTracker tracker = new QuickFilterHistoryTracker(recorded::add);

    @Test
    void testEnterAfterAcceptedResultRecords() {
        tracker.verdict("abc", true);
        tracker.commit("abc");

        assertThat(recorded).containsExactly("abc");
    }

    @Test
    void testBlurBeforeVerdictRecordsWhenVerdictArrives() {
        tracker.commit("abc");
        assertThat(recorded).isEmpty();

        tracker.verdict("abc", true);
        assertThat(recorded).containsExactly("abc");
    }

    @Test
    void testRejectedTextIsNeverRecorded() {
        tracker.commit("name:?abc");
        tracker.verdict("name:?abc", false);

        assertThat(recorded).isEmpty();
    }

    /**
     * Debounced as-you-type queries are accepted but never committed: nothing is recorded.
     */
    @Test
    void testAcceptedButNotCommittedIsNotRecorded() {
        tracker.verdict("a", true);
        tracker.verdict("ab", true);
        tracker.verdict("abc", true);

        assertThat(recorded).isEmpty();
    }

    @Test
    void testOnlyTheCommittedTextIsRecorded() {
        tracker.verdict("a", true);
        tracker.verdict("ab", true);
        tracker.commit("ab");

        assertThat(recorded).containsExactly("ab");
    }

    @Test
    void testRepeatedCommitOfSameTextRecordsOnce() {
        tracker.verdict("abc", true);
        tracker.commit("abc");
        tracker.commit("abc");
        tracker.commit(" abc ");

        assertThat(recorded).containsExactly("abc");
    }

    @Test
    void testBlankIsNeverRecorded() {
        tracker.verdict("", true);
        tracker.commit("");
        tracker.commit("   ");

        assertThat(recorded).isEmpty();
    }

    @Test
    void testChosenFromHistoryRecordsImmediatelyAndSuppressesTheFollowingBlur() {
        tracker.chosen("abc");
        tracker.commit("abc");

        assertThat(recorded).containsExactly("abc");
    }

    /**
     * The defect the audit found: clearing the box used to be treated as an acceptance of
     * whatever was last sent, so retyping a rejected filter and pressing Enter recorded it.
     */
    @Test
    void testClearDoesNotPreApproveTheOldText() {
        tracker.commit("name:?abc");
        tracker.verdict("name:?abc", false);
        tracker.cleared();
        tracker.verdict("", true);

        tracker.commit("name:?abc");

        assertThat(recorded).isEmpty();
    }

    /**
     * A verdict for stale text does not approve the newer text the user has moved on to.
     */
    @Test
    void testVerdictIsForTheTextItWasSentWith() {
        tracker.commit("abcd");
        tracker.verdict("abc", true);
        assertThat(recorded).isEmpty();

        tracker.verdict("abcd", true);
        assertThat(recorded).containsExactly("abcd");
    }

    @Test
    void testRejectionDropsThePendingCommit() {
        tracker.commit("abc");
        tracker.verdict("abc", false);
        tracker.verdict("abc", true);

        assertThat(recorded).isEmpty();
    }
}
