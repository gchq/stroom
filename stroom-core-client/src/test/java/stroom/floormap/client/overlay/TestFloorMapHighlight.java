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

package stroom.floormap.client.overlay;

import stroom.floormap.client.model.Fact;
import stroom.floormap.client.model.FloorMapObject;
import stroom.floormap.shared.FloorMapGroup;
import stroom.floormap.shared.FloorMapJsonKeys;
import stroom.floormap.shared.FloorMapTransformationMatrix;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// Pins the highlight precedence table before the canvas view is refactored onto
/// it, so the existing area-related behaviour cannot drift while group highlight
/// is added.
class TestFloorMapHighlight {

    private static final String PURPLE = "#8e24aa";

    private static Fact area() {
        final double[][] local = new double[][]{
                {-(double) 30, -(double) 30}, {(double) 30, -(double) 30}, {(double) 30, (double) 30}, {-(double) 30,
                (double) 30}};
        return new Fact("bay", FloorMapJsonKeys.AREA, null,
                FloorMapTransformationMatrix.translate(100, 100),
                new double[]{0, 0}, local, null, null);
    }

    private static FloorMapObject event(final String id, final double x, final double y) {
        return new FloorMapObject(id, FloorMapJsonKeys.PERSON, x, y);
    }

    /// Alice inside "bay", bob outside it, with "bay" focused.
    private static FloorMapAreaOverlay areaOverlayFocusedOnBay() {
        final List<Fact> facts = Collections.singletonList(area());
        final List<FloorMapObject> events = Arrays.asList(
                event("alice", 100, 100), event("bob", 900, 900));
        return FloorMapAreaOverlay.of(FloorMapAreaMembership.compute(facts, events), "bay");
    }

    private static FloorMapGroupOverlay groupOverlay(final String colour, final String... members) {
        return FloorMapGroupOverlay.of(
                Collections.singletonList(new FloorMapGroup("g", "g", colour, Arrays.asList(members))),
                Collections.singleton("g"));
    }

    // ------------------------------------------------------------------------
    // Precedence
    // ------------------------------------------------------------------------

    /// Group beats area-related: the group highlight was explicitly asked for.
    @Test
    void testGroupBeatsAreaRelated() {
        final FloorMapHighlight highlight = FloorMapHighlight.of(
                groupOverlay(PURPLE, "alice"), areaOverlayFocusedOnBay());

        assertThat(highlight.colourFor("alice")).isEqualTo(PURPLE);
        assertThat(highlight.isDashed("alice")).isFalse();
    }

    /// Area-related still applies to an entity in no shown group.
    @Test
    void testAreaRelatedAppliesWhenNotGrouped() {
        final FloorMapHighlight highlight = FloorMapHighlight.of(
                groupOverlay(PURPLE, "someone-else"), areaOverlayFocusedOnBay());

        assertThat(highlight.colourFor("alice")).isEqualTo(FloorMapHighlight.RELATED_COLOUR);
        assertThat(highlight.isDashed("alice")).isTrue();
    }

    /// A group member outside any area relation is still highlighted.
    @Test
    void testGroupAppliesWithoutAnyAreaRelation() {
        final FloorMapHighlight highlight = FloorMapHighlight.of(
                groupOverlay(PURPLE, "bob"), areaOverlayFocusedOnBay());

        assertThat(highlight.colourFor("bob")).isEqualTo(PURPLE);
        assertThat(highlight.isDashed("bob")).isFalse();
    }

    @Test
    void testUnrelatedEntityHasNoHighlight() {
        final FloorMapHighlight highlight = FloorMapHighlight.of(
                groupOverlay(PURPLE, "alice"), areaOverlayFocusedOnBay());

        assertThat(highlight.colourFor("nobody")).isNull();
        assertThat(highlight.isHighlighted("nobody")).isFalse();
        assertThat(highlight.isDashed("nobody")).isFalse();
    }

    /// The dash is what distinguishes the two sources, so it must be carried
    /// separately from the colour rather than inferred from it: a group whose
    /// colour happens to equal the area-related green is still drawn solid.
    @Test
    void testGreenGroupIsStillSolid() {
        final FloorMapHighlight highlight = FloorMapHighlight.of(
                groupOverlay(FloorMapHighlight.RELATED_COLOUR, "alice"),
                areaOverlayFocusedOnBay());

        assertThat(highlight.colourFor("alice")).isEqualTo(FloorMapHighlight.RELATED_COLOUR);
        assertThat(highlight.isDashed("alice")).isFalse();
    }

    /// An area can itself be a group member, and then takes the group colour.
    @Test
    void testAreaCanCarryGroupColour() {
        final FloorMapHighlight highlight = FloorMapHighlight.of(
                groupOverlay(PURPLE, "bay"), FloorMapAreaOverlay.EMPTY);

        assertThat(highlight.colourFor("bay")).isEqualTo(PURPLE);
    }

    // ------------------------------------------------------------------------
    // Empty / null behaviour — the no-groups path must be exactly as before
    // ------------------------------------------------------------------------

    /// With no groups anywhere, resolution reduces to plain area containment.
    @Test
    void testWithoutGroupsBehavesAsAreaOverlayAlone() {
        final FloorMapAreaOverlay areas = areaOverlayFocusedOnBay();
        final FloorMapHighlight highlight = FloorMapHighlight.of(FloorMapGroupOverlay.EMPTY, areas);

        assertThat(highlight.colourFor("alice")).isEqualTo(FloorMapHighlight.RELATED_COLOUR);
        assertThat(highlight.isDashed("alice")).isTrue();
        assertThat(highlight.colourFor("bob")).isNull();
    }

    @Test
    void testNoHighlightsAtAllYieldsEmpty() {
        assertThat(FloorMapHighlight.of(FloorMapGroupOverlay.EMPTY, FloorMapAreaOverlay.EMPTY))
                .isSameAs(FloorMapHighlight.EMPTY);
        assertThat(FloorMapHighlight.of(null, null)).isSameAs(FloorMapHighlight.EMPTY);
        assertThat(FloorMapHighlight.EMPTY.isEmpty()).isTrue();
    }

    @Test
    void testNullsAreSafe() {
        final FloorMapHighlight highlight = FloorMapHighlight.of(
                groupOverlay(PURPLE, "alice"), null);

        assertThat(highlight.colourFor(null)).isNull();
        assertThat(highlight.isDashed(null)).isFalse();
        assertThat(highlight.colourFor("alice")).isEqualTo(PURPLE);
        assertThat(FloorMapHighlight.EMPTY.colourFor("alice")).isNull();
    }

    /// A user's first group must not look like a related-entity highlight. The
    /// counterpart of `TestFloorMapGroup.testDefaultColourAvoidsReservedColours`,
    /// kept here because the group colour is shared code and this one is not.
    @Test
    void testRelatedColourDiffersFromGroupDefault() {
        assertThat(FloorMapHighlight.RELATED_COLOUR)
                .isNotEqualToIgnoringCase(FloorMapGroup.DEFAULT_COLOUR);
    }
}
