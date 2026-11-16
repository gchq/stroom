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
    void testParse() {
        assertThat(TextScale.parse(" 1.26")).isEqualTo(LARGE);
        assertThat(TextScale.parse("1")).isEqualTo(TextScale.MEDIUM);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "large", "0", "-2", "NaN", "Infinity"})
    void testParse_notAScaleIsMedium(final String value) {
        assertThat(TextScale.parse(value)).isEqualTo(TextScale.MEDIUM);
    }
}
