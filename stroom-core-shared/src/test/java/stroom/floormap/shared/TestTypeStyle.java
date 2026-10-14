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

package stroom.floormap.shared;

import stroom.floormap.shared.TypeStyle.Shape;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestTypeStyle {

    private static List<String> types(final List<TypeStyle> styles) {
        return styles.stream().map(TypeStyle::getType).toList();
    }

    /// Discovering into an empty config adds every type, alphabetically.
    @Test
    void testMerge_emptyExisting_addsAlphabetically() {
        final List<TypeStyle> merged =
                TypeStyle.merge(null, List.of("room", "background", "gate"));
        assertThat(types(merged)).containsExactly("background", "gate", "room");
    }

    /// Existing entries keep their (user-arranged) order; new types append after.
    @Test
    void testMerge_preservesExistingOrder_appendsNew() {
        final List<TypeStyle> existing = List.of(
                new TypeStyle("gate", Shape.SQUARE, "#111"),
                new TypeStyle("background", Shape.CIRCLE, "#222"));

        final List<TypeStyle> merged =
                TypeStyle.merge(existing, List.of("background", "gate", "person", "desk"));

        // gate, background stay first in their existing order; new ones (desk,
        // person) are appended alphabetically.
        assertThat(types(merged)).containsExactly("gate", "background", "desk", "person");
    }

    /// A re-discovery of already-known types leaves the config unchanged.
    @Test
    void testMerge_existingTypeSettingsPreserved() {
        final List<TypeStyle> existing = List.of(new TypeStyle("gate", Shape.DIAMOND, "#abc"));
        final List<TypeStyle> merged = TypeStyle.merge(existing, List.of("gate"));

        assertThat(merged).hasSize(1);
        assertThat(merged.getFirst().getShape()).isEqualTo(Shape.DIAMOND);
        assertThat(merged.getFirst().getColour()).isEqualTo("#abc");
    }

    /// Null / blank discovered names are ignored.
    @Test
    void testMerge_ignoresBlankNames() {
        final List<TypeStyle> merged = TypeStyle.merge(null,
                java.util.Arrays.asList("gate", "", null, "desk"));
        assertThat(types(merged)).containsExactly("desk", "gate");
    }

    /// Null discovered set leaves existing untouched.
    @Test
    void testMerge_nullDiscovered_returnsExisting() {
        final List<TypeStyle> existing = List.of(new TypeStyle("gate", null, null));
        final List<TypeStyle> merged = TypeStyle.merge(existing, null);
        assertThat(types(merged)).containsExactly("gate");
    }

    /// A null element in the stored list is skipped, not dereferenced.
    ///
    /// Nothing in the application produces one — every producer builds elements
    /// explicitly — so this guards against a hand-edited or badly imported document
    /// carrying a literal `null` in its `typeStyles` array. The three sibling
    /// walkers over this same list (`colourForType`, `withAreaStyle`,
    /// `FloorMapDocSession.hasAreaStyle`) all guard for it; `merge` did not.
    ///
    /// Uses `Arrays.asList` rather than `List.of`, which rejects nulls.
    @Test
    void testMerge_nullElementIsSkipped() {
        final List<TypeStyle> existing =
                Arrays.asList(new TypeStyle("gate", null, null), null, new TypeStyle("camera", null, null));

        final List<TypeStyle> merged = TypeStyle.merge(existing, List.of("sensor"));

        assertThat(types(merged))
                .as("the null is dropped, the real entries and the discovered type survive")
                .containsExactly("gate", "camera", "sensor");
        assertThat(merged).doesNotContainNull();
    }

    /// The "area" style is inserted directly after the last background entry —
    /// areas must paint above the floor plan but beneath everything else.
    @Test
    void testWithAreaStyle_insertsAfterBackground() {
        final List<TypeStyle> existing = List.of(
                new TypeStyle("background", null, null),
                new TypeStyle("gate", null, null),
                new TypeStyle("person", null, null));

        assertThat(types(TypeStyle.withAreaStyle(existing)))
                .containsExactly("background", "area", "gate", "person");
    }

    /// With no background style, the area style goes first.
    @Test
    void testWithAreaStyle_noBackground_insertsFirst() {
        final List<TypeStyle> existing = List.of(new TypeStyle("gate", null, null));
        assertThat(types(TypeStyle.withAreaStyle(existing)))
                .containsExactly("area", "gate");
        assertThat(types(TypeStyle.withAreaStyle(null))).containsExactly("area");
    }

    /// An existing "area" style is kept untouched (idempotent).
    @Test
    void testWithAreaStyle_existingAreaKept() {
        final List<TypeStyle> existing = List.of(
                new TypeStyle("area", Shape.SQUARE, "#123456"),
                new TypeStyle("background", null, null));

        final List<TypeStyle> result = TypeStyle.withAreaStyle(existing);

        assertThat(result).isEqualTo(existing);
        assertThat(result.getFirst().getColour()).isEqualTo("#123456");
    }

    @Test
    void testGraphic_absentByDefault() {
        // The 3-arg convenience constructor is the shape-and-colour case, so every
        // existing call site keeps drawing a shape.
        final TypeStyle style = new TypeStyle("person", Shape.CIRCLE, "#1f77b4");

        assertThat(style.getGraphic()).isNull();
        assertThat(style.hasGraphic()).isFalse();
    }

    @Test
    void testGraphic_setAndReported() {
        final TypeStyle style = new TypeStyle("van", null, "#1f77b4", "/assets/abc/icons/van.svg");

        assertThat(style.getGraphic()).isEqualTo("/assets/abc/icons/van.svg");
        assertThat(style.hasGraphic()).isTrue();
    }

    @Test
    void testGraphic_blankIsNotAGraphic() {
        // A cleared picker yields "", which must fall back to the shape rather than
        // rendering an image with an empty href.
        assertThat(new TypeStyle("van", Shape.SQUARE, "#111111", "").hasGraphic()).isFalse();
    }

    @Test
    void testGraphic_participatesInEquality() {
        final TypeStyle shapeStyle = new TypeStyle("van", null, "#111111", null);
        final TypeStyle imageStyle = new TypeStyle("van", null, "#111111", "/assets/abc/van.png");

        assertThat(imageStyle).isNotEqualTo(shapeStyle);
        assertThat(imageStyle).isEqualTo(new TypeStyle("van", null, "#111111", "/assets/abc/van.png"));
        assertThat(imageStyle.hashCode()).isEqualTo(
                new TypeStyle("van", null, "#111111", "/assets/abc/van.png").hashCode());
    }

    /// A configured colour wins — this is what the map paints, so it is what pickers show.
    @Test
    void testColourForType_configuredColourWins() {
        final List<TypeStyle> styles = List.of(
                new TypeStyle("gate", null, "#abcdef"),
                new TypeStyle("area", null, "#1e88e5"));

        assertThat(TypeStyle.colourForType("gate", styles)).isEqualTo("#abcdef");
        assertThat(TypeStyle.colourForType("area", styles)).isEqualTo("#1e88e5");
    }

    /// A style with no colour of its own falls through to the built-in default.
    @Test
    void testColourForType_blankConfiguredColourFallsBack() {
        final List<TypeStyle> styles = java.util.Arrays.asList(
                null,
                new TypeStyle("gate", Shape.SQUARE, null),
                new TypeStyle("desk", Shape.SQUARE, ""));

        assertThat(TypeStyle.colourForType("gate", styles)).isEqualTo(TypeStyle.DEFAULT_COLOUR);
        assertThat(TypeStyle.colourForType("desk", styles)).isEqualTo(TypeStyle.DEFAULT_COLOUR);
    }

    /// People keep their traditional blue until a person layer is configured.
    @Test
    void testColourForType_personDefaultsToBlue() {
        assertThat(TypeStyle.colourForType("person", null))
                .isEqualTo(TypeStyle.DEFAULT_PERSON_COLOUR);
        // Case-insensitive, matching the renderer's long-standing behaviour.
        assertThat(TypeStyle.colourForType("Person", null))
                .isEqualTo(TypeStyle.DEFAULT_PERSON_COLOUR);
        // ...and a configured person layer still overrides it.
        assertThat(TypeStyle.colourForType("person", List.of(new TypeStyle("person", null, "#101010"))))
                .isEqualTo("#101010");
    }

    /// An unknown or absent type still resolves to a usable colour.
    @Test
    void testColourForType_unknownType() {
        assertThat(TypeStyle.colourForType("camera", List.of(new TypeStyle("gate", null, "#abcdef"))))
                .isEqualTo(TypeStyle.DEFAULT_COLOUR);
        assertThat(TypeStyle.colourForType(null, null)).isEqualTo(TypeStyle.DEFAULT_COLOUR);
    }

    /// The default an area inherits is the "area" layer's colour, not a hard-coded one.
    @Test
    void testColourForType_areaDefaultComesFromTheAreaLayer() {
        final List<TypeStyle> styles = TypeStyle.withAreaStyle(null);
        assertThat(TypeStyle.colourForType("area", styles)).isEqualTo("#1e88e5");

        // Recolouring the area layer moves the default the fill picker shows with it.
        final List<TypeStyle> recoloured = List.of(new TypeStyle("area", null, "#ff9800"));
        assertThat(TypeStyle.colourForType("area", recoloured)).isEqualTo("#ff9800");
    }

    @Test
    void testMerge_discoveredTypesHaveNoGraphic() {
        final List<TypeStyle> result = TypeStyle.merge(null, List.of("van"));

        assertThat(result.getFirst().hasGraphic()).isFalse();
    }
}
