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

package stroom.widget.tab.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestTabLoadAnnouncements {

    @Test
    void aLongLoadOfTheSelectedTabIsAnnouncedThenItsFinish() {
        final TabLoadAnnouncements<String> announcements = new TabLoadAnnouncements<>();
        assertThat(announcements.startWaiting("a", true, true)).isTrue();
        assertThat(announcements.getWaiting()).isEqualTo("a");
        assertThat(announcements.delayElapsed("a", true, true)).isTrue();
        assertThat(announcements.getWaiting()).isNull();
        assertThat(announcements.finished("a")).isTrue();
        // Said once
        assertThat(announcements.finished("a")).isFalse();
    }

    @Test
    void aQuickLoadSaysNothing() {
        final TabLoadAnnouncements<String> announcements = new TabLoadAnnouncements<>();
        assertThat(announcements.startWaiting("a", true, true)).isTrue();
        assertThat(announcements.finished("a")).isFalse();
        assertThat(announcements.getWaiting()).isNull();
        // The delay's end, after it finished, says nothing
        assertThat(announcements.delayElapsed("a", false, true)).isFalse();
    }

    @Test
    void aTabLoadingOutOfSightSaysNothing() {
        final TabLoadAnnouncements<String> announcements = new TabLoadAnnouncements<>();
        assertThat(announcements.startWaiting("a", true, false)).isFalse();
        assertThat(announcements.getWaiting()).isNull();
        assertThat(announcements.finished("a")).isFalse();
    }

    @Test
    void aTabNotLoadingWaitsOnNothing() {
        final TabLoadAnnouncements<String> announcements = new TabLoadAnnouncements<>();
        assertThat(announcements.startWaiting("a", false, true)).isFalse();
        assertThat(announcements.getWaiting()).isNull();
    }

    @Test
    void aTabLeftBeforeTheDelayIsUpSaysNothing() {
        final TabLoadAnnouncements<String> announcements = new TabLoadAnnouncements<>();
        announcements.startWaiting("a", true, true);
        assertThat(announcements.delayElapsed("a", true, false)).isFalse();
        assertThat(announcements.finished("a")).isFalse();
    }

    @Test
    void selectingAnotherLoadingTabWaitsOnItInstead() {
        final TabLoadAnnouncements<String> announcements = new TabLoadAnnouncements<>();
        announcements.startWaiting("a", true, true);
        assertThat(announcements.startWaiting("b", true, true)).isTrue();
        assertThat(announcements.getWaiting()).isEqualTo("b");
        // A delay for the tab no longer waited on says nothing
        assertThat(announcements.delayElapsed("a", true, true)).isFalse();
        assertThat(announcements.delayElapsed("b", true, true)).isTrue();
    }

    @Test
    void aTabAnnouncedThenLeftStillSaysItHasLoaded() {
        final TabLoadAnnouncements<String> announcements = new TabLoadAnnouncements<>();
        announcements.startWaiting("a", true, true);
        announcements.delayElapsed("a", true, true);
        // Selected again while still loading: not announced twice
        assertThat(announcements.startWaiting("a", true, true)).isFalse();
        assertThat(announcements.finished("a")).isTrue();
    }

    @Test
    void aClosedTabSaysNothingMore() {
        final TabLoadAnnouncements<String> announcements = new TabLoadAnnouncements<>();
        announcements.startWaiting("a", true, true);
        announcements.delayElapsed("a", true, true);
        announcements.removed("a");
        assertThat(announcements.finished("a")).isFalse();

        announcements.startWaiting("b", true, true);
        announcements.removed("b");
        assertThat(announcements.getWaiting()).isNull();
        assertThat(announcements.delayElapsed("b", true, true)).isFalse();
    }

    @Test
    void theDelayForNoTabSaysNothing() {
        final TabLoadAnnouncements<String> announcements = new TabLoadAnnouncements<>();
        assertThat(announcements.delayElapsed(null, true, true)).isFalse();
    }
}
