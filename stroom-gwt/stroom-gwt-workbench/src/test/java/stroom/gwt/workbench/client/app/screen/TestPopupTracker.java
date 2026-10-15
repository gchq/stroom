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


package stroom.gwt.workbench.client.app.screen;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestPopupTracker {

    @Test
    void testShowAndHide() {
        final PopupTracker<String> tracker = new PopupTracker<>();

        tracker.onShow("dialog");
        tracker.onShow("alert");
        assertThat(tracker.getOpenPopups()).containsExactly("alert", "dialog");

        tracker.onHide("alert");
        assertThat(tracker.getOpenPopups()).containsExactly("dialog");

        tracker.onHide("unknown");
        assertThat(tracker.getOpenPopups()).containsExactly("dialog");
    }

    @Test
    void testShowTwiceHides() {
        // As PopupManager, which toggles a popup shown twice
        final PopupTracker<String> tracker = new PopupTracker<>();

        tracker.onShow("menu");
        tracker.onShow("menu");

        assertThat(tracker.getOpenPopups()).isEmpty();
    }

    @Test
    void testOpenPopupsIsACopy() {
        final PopupTracker<String> tracker = new PopupTracker<>();
        tracker.onShow("dialog");

        // The harness hides the popups while iterating, which removes them from the tracker
        for (final String popup : tracker.getOpenPopups()) {
            tracker.onHide(popup);
        }

        assertThat(tracker.getOpenPopups()).isEmpty();
    }
}
