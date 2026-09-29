/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.floormap.shared;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestFloorMapIcon {

    /// Every icon can actually be drawn. Cheap, but the paths are generated, and a
    /// blank one would show as an invisible glyph on the map rather than as an
    /// error anywhere.
    @Test
    void testEveryIconHasAPathAndALabel() {
        assertThat(FloorMapIcon.values()).isNotEmpty();
        for (final FloorMapIcon icon : FloorMapIcon.values()) {
            assertThat(icon.getPath())
                    .as(icon.name() + " path")
                    .isNotNull()
                    .startsWith("M")
                    .hasSizeGreaterThan(10);
            assertThat(icon.getLabel())
                    .as(icon.name() + " label")
                    .isNotNull()
                    .isNotBlank();
        }
    }

    /// Paths are interpolated into trusted SVG, so nothing in one may be able to
    /// close the attribute and start another. They are compile-time constants, so
    /// this cannot fail from data — it fails when a new icon is added with a stray
    /// quote in it.
    @Test
    void testPathsCannotBreakOutOfAnAttribute() {
        for (final FloorMapIcon icon : FloorMapIcon.values()) {
            assertThat(icon.getPath())
                    .as(icon.name() + " path")
                    .doesNotContain("\"")
                    .doesNotContain("'")
                    .doesNotContain("<")
                    .doesNotContain(">");
        }
    }

    /// Two icons with the same label would be indistinguishable in the picker.
    @Test
    void testLabelsAreUnique() {
        final Set<String> labels = new HashSet<>();
        for (final FloorMapIcon icon : FloorMapIcon.values()) {
            assertThat(labels.add(icon.getLabel()))
                    .as("duplicate label " + icon.getLabel())
                    .isTrue();
        }
    }

    /// The client-requested icons are present, whatever else the set grows to hold.
    @Test
    void testTheRequestedIconsExist() {
        assertThat(FloorMapIcon.fromName("PERSON")).isNotNull();
        assertThat(FloorMapIcon.fromName("GATE")).isNotNull();
        assertThat(FloorMapIcon.fromName("PRINTER")).isNotNull();
    }

    /// A stored name resolves back to its icon, and anything unrecognised resolves
    /// to `null` rather than throwing.
    ///
    /// The lenient half matters: the name comes out of a saved document, which
    /// may have been written by a version carrying an icon this one has never heard
    /// of, or edited by hand. That must degrade to "draws its shape", not to a
    /// broken editor.
    @Test
    void testFromNameRoundTripsAndIsLenient() {
        for (final FloorMapIcon icon : FloorMapIcon.values()) {
            assertThat(FloorMapIcon.fromName(icon.name())).isSameAs(icon);
        }
        assertThat(FloorMapIcon.fromName(null)).isNull();
        assertThat(FloorMapIcon.fromName("")).isNull();
        assertThat(FloorMapIcon.fromName("NOT_AN_ICON")).isNull();
        // Case matters — the stored form is the enum's own name.
        assertThat(FloorMapIcon.fromName("person")).isNull();
    }

    /// The transform maps the icon's grid onto a glyph of the given half-size: the
    /// grid's centre lands on the origin and its corners on `±halfSize`.
    @Test
    void testTransformFitsTheGlyphBox() {
        assertThat(FloorMapIcon.transform(12))
                .isEqualTo("translate(-12.0,-12.0) scale(1.0)");
        // Half the size, half the scale, same centring.
        assertThat(FloorMapIcon.transform(6))
                .isEqualTo("translate(-6.0,-6.0) scale(0.5)");
    }

    /// A layer style built from an icon carries it, and carries nothing else —
    /// a leftover shape or image would outrank or contradict it in the renderer.
    @Test
    void testTypeStyleCarriesTheIconAlone() {
        final TypeStyle style = TypeStyle.ofIcon("gate", FloorMapIcon.GATE, "#ff0000");

        assertThat(style.getIcon()).isEqualTo("GATE");
        assertThat(style.iconOrNull()).isSameAs(FloorMapIcon.GATE);
        assertThat(style.hasIcon()).isTrue();
        assertThat(style.getShape()).isNull();
        assertThat(style.getGraphic()).isNull();
        assertThat(style.hasGraphic()).isFalse();
        assertThat(style.getColour()).isEqualTo("#ff0000");
    }

    /// An icon name no longer recognised reads as "no icon" everywhere, so the
    /// layer falls back to its shape instead of drawing nothing.
    @Test
    void testUnknownIconNameReadsAsNoIcon() {
        final TypeStyle style = new TypeStyle("gate", null, "#ff0000", null, "RETIRED_ICON");

        assertThat(style.getIcon()).isEqualTo("RETIRED_ICON");
        assertThat(style.iconOrNull()).isNull();
        assertThat(style.hasIcon()).isFalse();
    }

    /// A style with no icon is unaffected — the field is optional.
    @Test
    void testStylesWithoutIconsAreUnaffected() {
        final TypeStyle shapeStyle = new TypeStyle("desk", TypeStyle.Shape.SQUARE, "#00ff00");

        assertThat(shapeStyle.getIcon()).isNull();
        assertThat(shapeStyle.hasIcon()).isFalse();
        assertThat(shapeStyle.getShape()).isEqualTo(TypeStyle.Shape.SQUARE);
    }
}
