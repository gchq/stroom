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

package stroom.widget.util.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TestColourContrast {

    private static final String WHITE = "rgb(255, 255, 255)";
    private static final String BLACK = "rgb(0, 0, 0)";

    @Test
    void ratio_blackOnWhite_isTheMost() {
        assertThat(ColourContrast.ratio(BLACK, List.of(WHITE)))
                .isCloseTo(21.0, within(0.001));
    }

    @Test
    void ratio_sameColours_isTheLeast() {
        assertThat(ColourContrast.ratio(WHITE, List.of(WHITE)))
                .isCloseTo(1.0, within(0.001));
    }

    @Test
    void ratio_eitherWay_isTheSame() {
        assertThat(ColourContrast.ratio(WHITE, List.of("rgb(255, 0, 0)")))
                .isCloseTo(ColourContrast.ratio("rgb(255, 0, 0)", List.of(WHITE)), within(0.0001));
    }

    @Test
    void ratio_greysEitherSideOfTheMinimum() {
        // #767676 is the lightest grey that meets 4.5:1 on white; #777777 just misses it
        assertThat(ColourContrast.ratio("rgb(118, 118, 118)", List.of(WHITE)))
                .isGreaterThanOrEqualTo(ColourContrast.MINIMUM);
        assertThat(ColourContrast.ratio("rgb(119, 119, 119)", List.of(WHITE)))
                .isLessThan(ColourContrast.MINIMUM);
    }

    @Test
    void ratio_seeThroughBackground_isLaidOverTheOneBelow() {
        // Half black on white is mid grey, as is a transparent layer over it
        final Double layered = ColourContrast.ratio(WHITE,
                List.of("rgba(0, 0, 0, 0)", "rgba(0, 0, 0, 0.5)", WHITE));
        final Double grey = ColourContrast.ratio(WHITE, List.of("rgb(127.5, 127.5, 127.5)"));
        assertThat(layered)
                .isCloseTo(grey, within(0.0001));
    }

    @Test
    void ratio_seeThroughText_isLaidOverTheBackground() {
        // Half see-through black text on white reads as grey
        assertThat(ColourContrast.ratio("rgba(0, 0, 0, 0.5)", List.of(WHITE)))
                .isCloseTo(ColourContrast.ratio("rgb(127.5, 127.5, 127.5)", List.of(WHITE)), within(0.0001));
    }

    @Test
    void ratio_noOpaqueBackground_isUnknown() {
        assertThat(ColourContrast.ratio(BLACK, List.of("rgba(255, 255, 255, 0.5)")))
                .isNull();
        assertThat(ColourContrast.ratio(BLACK, List.of("transparent")))
                .isNull();
        assertThat(ColourContrast.ratio(BLACK, List.of()))
                .isNull();
    }

    @Test
    void ratio_unreadableColour_isUnknown() {
        assertThat(ColourContrast.ratio("red", List.of(WHITE)))
                .isNull();
        assertThat(ColourContrast.ratio(BLACK, List.of("color(srgb 1 1 1)")))
                .isNull();
        assertThat(ColourContrast.ratio(null, List.of(WHITE)))
                .isNull();
        assertThat(ColourContrast.ratio("rgb(1, 2)", List.of(WHITE)))
                .isNull();
        assertThat(ColourContrast.ratio("rgb(a, b, c)", List.of(WHITE)))
                .isNull();
    }

    @Test
    void parse_spaceSeparatedWithPercentAlpha() {
        assertThat(ColourContrast.parse("rgb(10 20 30 / 50%)"))
                .containsExactly(10, 20, 30, 0.5);
        assertThat(ColourContrast.parse("rgba(10, 20, 30, 0.25)"))
                .containsExactly(10, 20, 30, 0.25);
        assertThat(ColourContrast.parse("rgb(100%, 0%, 0%)"))
                .containsExactly(255, 0, 0, 1);
    }

    @Test
    void format_roundsDown() {
        assertThat(ColourContrast.format(21))
                .isEqualTo("21.0:1");
        assertThat(ColourContrast.format(4.48))
                .isEqualTo("4.4:1");
        assertThat(ColourContrast.format(4.5))
                .isEqualTo("4.5:1");
        assertThat(ColourContrast.format(1))
                .isEqualTo("1.0:1");
    }
}
