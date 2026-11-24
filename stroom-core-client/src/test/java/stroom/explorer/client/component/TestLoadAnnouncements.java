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

package stroom.explorer.client.component;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestLoadAnnouncements {

    private final LoadAnnouncements announcements = new LoadAnnouncements();

    @Test
    void aSlowLoadIsAnnouncedAndSoIsItsFinishing() {
        assertThat(announcements.start()).isTrue();
        assertThat(announcements.delayElapsed()).isTrue();
        assertThat(announcements.finish()).isTrue();
    }

    @Test
    void aQuickLoadSaysNothing() {
        assertThat(announcements.start()).isTrue();
        assertThat(announcements.finish()).isFalse();
        // The delay is up after it finished
        assertThat(announcements.delayElapsed()).isFalse();
    }

    @Test
    void loadingAgainWhileLoadingIsOneLoad() {
        assertThat(announcements.start()).isTrue();
        assertThat(announcements.start()).isFalse();
        assertThat(announcements.delayElapsed()).isTrue();
        assertThat(announcements.delayElapsed()).isFalse();
        assertThat(announcements.finish()).isTrue();
    }

    @Test
    void theDelayRunsUntilTheLoadIsAnnouncedOrFinished() {
        assertThat(announcements.isWaiting()).isFalse();
        announcements.start();
        assertThat(announcements.isWaiting()).isTrue();
        announcements.delayElapsed();
        assertThat(announcements.isWaiting()).isFalse();
        announcements.finish();
        announcements.start();
        announcements.finish();
        assertThat(announcements.isWaiting()).isFalse();
    }

    @Test
    void eachLoadIsItsOwn() {
        announcements.start();
        announcements.delayElapsed();
        announcements.finish();

        assertThat(announcements.start()).isTrue();
        assertThat(announcements.finish()).isFalse();
    }
}
