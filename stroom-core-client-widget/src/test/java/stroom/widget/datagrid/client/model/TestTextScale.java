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

package stroom.widget.datagrid.client.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestTextScale {

    private static final double LARGE = 1.26;
    private static final double SMALL = 0.93;

    @Test
    void testToDrawn_roundsToWholePixels() {
        assertThat(TextScale.toDrawn(200, LARGE)).isEqualTo(252);
        assertThat(TextScale.toDrawn(200, SMALL)).isEqualTo(186);
        assertThat(TextScale.toDrawn(200, TextScale.MEDIUM)).isEqualTo(200);
    }

    @Test
    void testToMedium_undoesToDrawn() {
        for (int width = 0; width <= 1000; width++) {
            // Exactly when the text is bigger; when smaller, whole pixels drawn hold less, to a pixel
            assertThat(TextScale.toMedium(TextScale.toDrawn(width, LARGE), LARGE)).isEqualTo(width);
            assertThat(TextScale.toMedium(TextScale.toDrawn(width, SMALL), SMALL)).isBetween(width - 1, width + 1);
        }
    }

    @Test
    void testToDrawn_invalidScaleIsMedium() {
        assertThat(TextScale.toDrawn(200, 0)).isEqualTo(200);
        assertThat(TextScale.toDrawn(200, -1)).isEqualTo(200);
        assertThat(TextScale.toDrawn(200, Double.NaN)).isEqualTo(200);
        assertThat(TextScale.toMedium(200, 0)).isEqualTo(200);
    }

    @Test
    void testRescale_drawsTheSameMediumWidthAgain() {
        assertThat(TextScale.rescale(252, LARGE, 1.29)).isEqualTo(258);
        assertThat(TextScale.rescale(258, 1.29, TextScale.MEDIUM)).isEqualTo(200);
        assertThat(TextScale.rescale(186, SMALL, TextScale.MEDIUM)).isEqualTo(200);
        assertThat(TextScale.rescale(200, TextScale.MEDIUM, TextScale.MEDIUM)).isEqualTo(200);
    }

    @Test
    void testParse() {
        assertThat(TextScale.parse(" 1.26")).isEqualTo(LARGE);
        assertThat(TextScale.parse("1")).isEqualTo(TextScale.MEDIUM);
    }

    @Test
    void testMeasured_sameWidthIsExactlyThePreference() {
        // Medium with nothing else set: the text and its medium reference are drawn alike
        assertThat(TextScale.measured(274, 274, TextScale.MEDIUM)).isEqualTo(TextScale.MEDIUM);
        // Sub-pixel noise in the widths doesn't move it
        assertThat(TextScale.measured(274.0000001, 274, TextScale.MEDIUM)).isEqualTo(TextScale.MEDIUM);
    }

    @Test
    void testMeasured_widerTextRoundsUpToHundredths() {
        // Text spacing (WCAG 1.4.12) at medium: about a quarter wider
        assertThat(TextScale.measured(342.3, 274, TextScale.MEDIUM)).isEqualTo(1.25);
        assertThat(TextScale.measured(343, 274, TextScale.MEDIUM)).isEqualTo(1.26);
        // Large, where letters grow more than the font
        assertThat(TextScale.measured(352, 274, LARGE)).isEqualTo(1.29);
    }

    @Test
    void testMeasured_neverBelowThePreference() {
        // Small, where letters shrink less than the font: the preference's 0.93 stands
        assertThat(TextScale.measured(250, 274, SMALL)).isEqualTo(SMALL);
        // Large in a font that grows less than Linux Chrome's Roboto
        assertThat(TextScale.measured(335, 274, LARGE)).isEqualTo(LARGE);
    }

    @Test
    void testMeasured_nothingMeasuredIsThePreference() {
        // Not laid out yet (e.g. hidden), or the preference unset
        assertThat(TextScale.measured(0, 0, LARGE)).isEqualTo(LARGE);
        assertThat(TextScale.measured(300, 0, LARGE)).isEqualTo(LARGE);
        assertThat(TextScale.measured(Double.NaN, 274, SMALL)).isEqualTo(SMALL);
        assertThat(TextScale.measured(274, 274, Double.NaN)).isEqualTo(TextScale.MEDIUM);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "large", "0", "-2", "NaN", "Infinity"})
    void testParse_notAScaleIsMedium(final String value) {
        assertThat(TextScale.parse(value)).isEqualTo(TextScale.MEDIUM);
    }
}
