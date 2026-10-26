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

package stroom.visualisation.shared;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// The vectors here are also checked by the content pack's `src/shared/colour.test.js`, so that a
/// visualisation shown by an older Stroom (which colours values itself) gives the same colours.
class TestVisColourList {

    @Test
    void testHashIsFnv1a() {
        assertThat(VisColourMaths.hash("")).isEqualTo(2166136261L);
        assertThat(VisColourMaths.hash("a")).isEqualTo(3826002220L);
        assertThat(VisColourMaths.hash("Host\u0000web-01")).isEqualTo(1867768847L);
        assertThat(VisColourMaths.hash("Status\u0000200 OK")).isEqualTo(1270965377L);
        assertThat(VisColourMaths.hash("Ünïcödé")).isEqualTo(4191047478L);
    }

    @Test
    void testPreferredColourElseNextFree() {
        final VisColourList list = new VisColourList();
        assertThat(list.colours("Host", List.of("web-01", "web-02", "db-01"), VisPalette.VIVID, true))
                .containsExactly("#e86bd1", "#38c6f4", "#ff8c42");
        assertThat(list.colours("Status", List.of("200 OK", "301 Moved", "404 Not Found", "500 Error"),
                VisPalette.VIVID, true))
                .containsExactly("#3d8bfd", "#20c997", "#f0507a", "#9b7bff");
        // A value keeps its colour, and a new one takes a free colour
        assertThat(list.colours("Host", List.of("db-01", "web-03"), VisPalette.VIVID, true))
                .containsExactly("#ff8c42", "#e9b434");
    }

    @Test
    void testFieldAndValueMakeAValue() {
        final VisColourList list = new VisColourList();
        final String status200 = list.colours("Status", List.of("200"), VisPalette.VIVID, false).get(0);
        final String count200 = list.colours("Count", List.of("200"), VisPalette.VIVID, false).get(0);
        assertThat(count200).isNotEqualTo(status200);
    }

    @Test
    void testNoColourGivenTwiceUntilAllAreGiven() {
        final VisColourList list = new VisColourList();
        final int size = VisPalette.VIVID.getColours(false).size();
        final List<String> values = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            values.add("value " + i);
        }
        final List<String> colours = list.colours("Field", values, VisPalette.VIVID, false);
        assertThat(new HashSet<>(colours)).hasSize(size);
        // One more shares its preferred colour
        assertThat(VisPalette.VIVID.getColours(false))
                .contains(list.colours("Field", List.of("one more"), VisPalette.VIVID, false).get(0));
    }

    @Test
    void testPaletteChangeKeepsPlaces() {
        final VisColourList list = new VisColourList();
        final String vivid = list.colours("Host", List.of("web-01"), VisPalette.VIVID, true).get(0);
        final String light = list.colours("Host", List.of("web-01"), VisPalette.VIVID, false).get(0);
        assertThat(VisPalette.VIVID.getBaseColours(true).indexOf(vivid))
                .isEqualTo(VisPalette.VIVID.getBaseColours(false).indexOf(light));
    }

    @Test
    void testPalettesWithShadesAsTheContentPacksAre() {
        assertThat(VisPalette.VIVID.getColours(false)).containsExactly("#1f6fd6", "#c25400", "#0b8460", "#d0305f",
                "#7a52e0", "#8f6a00", "#0a7fa3", "#b0369a", "#4a7f12", "#d23b3b", "#de6e8f", "#c872b8", "#164e96",
                "#883b00", "#085c43", "#922243", "#55399d", "#644a00", "#075972", "#7b266c", "#34590d", "#10386b",
                "#612a00", "#064230", "#681830", "#3d2970", "#581b4d");
        assertThat(VisPalette.VIVID.getColours(true)).hasSize(32);
        assertThat(VisPalette.COLOUR_BLIND_SAFE.getColours(false)).hasSize(22);
        assertThat(VisPalette.COLOUR_BLIND_SAFE.getColours(true)).containsExactly("#56b4e9", "#e69f00", "#009e73",
                "#cc79a7", "#f0e442", "#d55e00", "#bbbbbb", "#7f8ef0", "#89cbf0", "#eebc4d", "#4dbb9d", "#dba1c1",
                "#f5ec7b", "#e28e4d", "#cfcfcf", "#a5b0f5", "#3c7ea3", "#a16f00", "#a8a02e", "#838383", "#5963a8",
                "#abdaf4", "#80cfb9", "#f8f2a1", "#eaaf80", "#787221");
    }

    @Test
    void testEveryColourHasContrastAgainstItsTheme() {
        for (final VisPalette palette : VisPalette.values()) {
            for (final String colour : palette.getColours(false)) {
                assertThat(VisColourMaths.contrast(colour, VisPalette.LIGHT_BACKGROUND))
                        .as(palette + " " + colour + " light").isGreaterThanOrEqualTo(3);
            }
            for (final String colour : palette.getColours(true)) {
                assertThat(VisColourMaths.contrast(colour, VisPalette.DARK_BACKGROUND))
                        .as(palette + " " + colour + " dark").isGreaterThanOrEqualTo(3);
            }
        }
    }

    @Test
    void testFromDisplayValue() {
        assertThat(VisPalette.fromDisplayValue("Colour-blind safe")).isEqualTo(VisPalette.COLOUR_BLIND_SAFE);
        assertThat(VisPalette.fromDisplayValue("Vivid")).isEqualTo(VisPalette.VIVID);
        // The default, for a user who hasn't chosen
        assertThat(VisPalette.fromDisplayValue(null)).isEqualTo(VisPalette.VIVID);
        assertThat(VisPalette.fromDisplayValue("Nope")).isEqualTo(VisPalette.VIVID);
    }

    @Test
    void testMix() {
        assertThat(VisColourMaths.mix("#000000", "#ffffff", 0.5)).isEqualTo("#808080");
        assertThat(VisColourMaths.mix("#3d8bfd", "#000000", 0)).isEqualTo("#3d8bfd");
    }
}
