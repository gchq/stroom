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

class TestTabLabelCap {

    @Test
    void labelsThatFitAreNotCut() {
        assertThat(TabLabelCap.cap(new double[]{400, 200}, 600)).isEqualTo(TabLabelCap.NONE);
        assertThat(TabLabelCap.cap(new double[]{400, 200}, 1000)).isEqualTo(TabLabelCap.NONE);
    }

    @Test
    void theWidestIsCutFirst() {
        // The 400 is cut to 300; the 200 keeps its width
        assertThat(TabLabelCap.cap(new double[]{400, 200}, 500)).isEqualTo(300);
    }

    @Test
    void onceAsNarrowAsTheNextTheyAreCutAlike() {
        // The 400 is cut to 200, then both to 150
        assertThat(TabLabelCap.cap(new double[]{400, 200}, 300)).isEqualTo(150);
        // In any order: the 100 keeps its width, the others share 300
        assertThat(TabLabelCap.cap(new double[]{200, 400, 100}, 400)).isEqualTo(150);
    }

    @Test
    void aShortLabelKeepsItsWidth() {
        // The 50 isn't cut; the others share 450 - 50
        assertThat(TabLabelCap.cap(new double[]{50, 300, 300}, 450)).isEqualTo(200);
    }

    @Test
    void noRoomGivesNoWidth() {
        assertThat(TabLabelCap.cap(new double[]{400, 200}, 0)).isEqualTo(0);
        assertThat(TabLabelCap.cap(new double[]{400, 200}, -50)).isEqualTo(0);
    }

    @Test
    void noLabelsFit() {
        assertThat(TabLabelCap.cap(new double[]{}, 0)).isEqualTo(TabLabelCap.NONE);
    }

    @Test
    void theWidthsGivenAreLeftAsTheyWere() {
        final double[] widths = {400, 200};
        TabLabelCap.cap(widths, 300);
        assertThat(widths).containsExactly(400, 200);
    }
}
