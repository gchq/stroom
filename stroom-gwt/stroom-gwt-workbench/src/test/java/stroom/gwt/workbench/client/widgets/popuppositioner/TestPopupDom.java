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


package stroom.gwt.workbench.client.widgets.popuppositioner;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestPopupDom {

    @Test
    void testOverlaps() {
        // {left, top, right, bottom}
        assertThat(PopupDom.overlaps(rect(0, 0, 100, 100), rect(50, 50, 150, 150))).isTrue();
        assertThat(PopupDom.overlaps(rect(50, 50, 150, 150), rect(0, 0, 100, 100))).isTrue();
        // One inside the other
        assertThat(PopupDom.overlaps(rect(0, 0, 100, 100), rect(10, 10, 20, 20))).isTrue();
    }

    @Test
    void testOverlaps_disjoint() {
        assertThat(PopupDom.overlaps(rect(0, 0, 100, 100), rect(200, 0, 300, 100))).isFalse();
        assertThat(PopupDom.overlaps(rect(0, 0, 100, 100), rect(0, 200, 100, 300))).isFalse();
    }

    @Test
    void testOverlaps_withinRoundingError() {
        // Touching edges, or overlapping by no more than 1px, isn't overlapping (a popup placed
        // flush against its trigger)
        assertThat(PopupDom.overlaps(rect(0, 0, 100, 100), rect(100, 0, 200, 100))).isFalse();
        assertThat(PopupDom.overlaps(rect(0, 0, 100, 100), rect(99, 0, 200, 100))).isFalse();
        assertThat(PopupDom.overlaps(rect(0, 0, 100, 100), rect(98.5, 0, 200, 100))).isTrue();
    }

    private static double[] rect(final double left, final double top, final double right, final double bottom) {
        return new double[]{left, top, right, bottom, right - left, bottom - top};
    }
}
