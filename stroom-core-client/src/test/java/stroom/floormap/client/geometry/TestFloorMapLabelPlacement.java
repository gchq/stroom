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

package stroom.floormap.client.geometry;

import stroom.floormap.client.geometry.FloorMapLabelPlacement.Label;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestFloorMapLabelPlacement {

    private static final double VIEWPORT_W = 1600;
    private static final double VIEWPORT_H = 900;

    /// A 80x14 caption centred at (x, y).
    private static Label label(final String key,
                               final double x,
                               final double y,
                               final int priority) {
        return new Label(key, x, y, 80, 14, priority);
    }

    private static Set<String> place(final Label... labels) {
        return FloorMapLabelPlacement.place(
                Arrays.asList(labels), VIEWPORT_W, VIEWPORT_H);
    }

    /// Captions that do not touch are all drawn — the ordinary, uncrowded case.
    @Test
    void testNonOverlappingLabelsAreAllPlaced() {
        assertThat(place(
                label("a", 100, 100, 0),
                label("b", 400, 100, 0),
                label("c", 100, 400, 0)))
                .containsExactlyInAnyOrder("a", "b", "c");
    }

    /// The reported case: a user and a desk at the same spot. Both glyphs are still
    /// drawn — only the less important caption steps aside.
    @Test
    void testOverlappingLabelsKeepTheHigherPriority() {
        final Set<String> visible = place(
                label("desk-3", 500, 500, 3000),
                label("alice", 500, 500, 2000));

        assertThat(visible).containsExactly("alice");
    }

    /// Priority decides, not input order.
    @Test
    void testPriorityWinsRegardlessOfInputOrder() {
        assertThat(place(
                label("alice", 500, 500, 2000),
                label("desk-3", 500, 500, 3000)))
                .containsExactly("alice");
        assertThat(place(
                label("desk-3", 500, 500, 3000),
                label("alice", 500, 500, 2000)))
                .containsExactly("alice");
    }

    /// Equal priority breaks on the key, so a frame cannot place different labels
    /// depending on the order rows arrived in — that would read as flicker.
    @Test
    void testEqualPriorityBreaksOnKeyDeterministically() {
        assertThat(place(
                label("zach", 500, 500, 2000),
                label("alice", 500, 500, 2000)))
                .containsExactly("alice");
        assertThat(place(
                label("alice", 500, 500, 2000),
                label("zach", 500, 500, 2000)))
                .containsExactly("alice");
    }

    /// The whole result is order-independent, not just the winner of one pair.
    @Test
    void testResultDoesNotDependOnInputOrder() {
        final List<Label> labels = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            labels.add(label("label" + i, (i % 8) * 50, ((double) i / 8) * 10, i % 4));
        }
        final List<Label> reversed = new ArrayList<>(labels);
        Collections.reverse(reversed);

        assertThat(FloorMapLabelPlacement.place(reversed, VIEWPORT_W, VIEWPORT_H))
                .isEqualTo(FloorMapLabelPlacement.place(labels, VIEWPORT_W, VIEWPORT_H));
    }

    /// Nothing placed ever overlaps anything else placed.
    @Test
    void testPlacedLabelsNeverOverlap() {
        final List<Label> labels = new ArrayList<>();
        // A dense field, far more captions than can possibly fit.
        for (int i = 0; i < 200; i++) {
            labels.add(label("label" + i, (i % 20) * 30, ((double) i / 20) * 8, i % 3));
        }

        final Set<String> visible =
                FloorMapLabelPlacement.place(labels, VIEWPORT_W, VIEWPORT_H);
        final List<double[]> rects = new ArrayList<>();
        for (final Label label : labels) {
            if (visible.contains(label.getKey())) {
                // Same package, so the real geometry is reachable — a
                // reimplementation here could agree with a wrong implementation.
                rects.add(label.rect());
            }
        }

        // The field must actually be crowded, or "nothing overlaps" is vacuous —
        // a fixture that happened to spread the labels out would pass while
        // testing nothing.
        assertThat(visible).isNotEmpty().hasSizeLessThan(labels.size());
        for (int i = 0; i < rects.size(); i++) {
            for (int j = i + 1; j < rects.size(); j++) {
                assertThat(overlaps(rects.get(i), rects.get(j)))
                        .as("labels " + i + " and " + j + " overlap")
                        .isFalse();
            }
        }
    }

    /// Captions that merely abut are both readable, so both are kept.
    @Test
    void testTouchingEdgesDoNotCount() {
        // 80 wide, centred at 100 and 180 → they share the edge at x=140.
        assertThat(place(
                label("a", 100, 500, 0),
                label("b", 180, 500, 0)))
                .containsExactlyInAnyOrder("a", "b");
    }

    /// A caption entirely off-screen is not drawn — and, importantly, does not
    /// reserve space, or something just outside the view would silently suppress a
    /// caption the user can see.
    @Test
    void testOffScreenLabelsAreDroppedAndReserveNothing() {
        final Set<String> visible = place(
                // Off the left edge, and higher priority than the on-screen one.
                label("offscreen", -400, 500, 0),
                label("onscreen", 100, 500, 3000));

        assertThat(visible).containsExactly("onscreen");
    }

    /// A caption straddling the edge is still partly visible, so it is kept.
    @Test
    void testPartlyVisibleLabelsAreKept() {
        assertThat(place(label("edge", 10, 500, 0))).containsExactly("edge");
    }

    /// With no viewport known yet, nothing is culled for being off-screen.
    @Test
    void testNoViewportDisablesCulling() {
        assertThat(FloorMapLabelPlacement.place(
                Collections.singletonList(label("far", -5000, -5000, 0)), 0, 0))
                .containsExactly("far");
    }

    /// Null and empty inputs are safe.
    @Test
    void testEmptyInputs() {
        assertThat(FloorMapLabelPlacement.place(null, VIEWPORT_W, VIEWPORT_H)).isEmpty();
        assertThat(FloorMapLabelPlacement.place(
                Collections.emptyList(), VIEWPORT_W, VIEWPORT_H)).isEmpty();
    }

    /// Crowding costs the least important captions, never the most important. A
    /// tracked entity's name survives a pile-up that drops everything else.
    @Test
    void testTheMostImportantLabelAlwaysSurvives() {
        final List<Label> labels = new ArrayList<>();
        labels.add(label("tracked", 500, 500, 0));
        for (int i = 0; i < 20; i++) {
            labels.add(label("other" + i, 500, 500, 2000));
        }

        assertThat(FloorMapLabelPlacement.place(labels, VIEWPORT_W, VIEWPORT_H))
                .containsExactly("tracked");
    }

    private static boolean overlaps(final double[] a, final double[] b) {
        return a[0] < b[2] && a[2] > b[0] && a[1] < b[3] && a[3] > b[1];
    }
}
