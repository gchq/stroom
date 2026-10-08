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

package stroom.floormap.client.playback;

import stroom.floormap.client.model.FloorMapObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TestFloorMapEntityAnimator {

    private static final double TOL = 1e-6;
    private static final double DURATION_MS = 800.0;

    private FloorMapEntityAnimator animator;

    @BeforeEach
    void setUp() {
        animator = new FloorMapEntityAnimator();
    }

    private static FloorMapObject obj(final String id, final double x, final double y) {
        return new FloorMapObject(id, "person", x, y);
    }

    // -----------------------------------------------------------------------

    /// Not playing: entities teleport, positions are recorded, and it's inactive.
    @Test
    void testTeleportRecordsPositions() {
        final boolean teleported = animator.onEventObjects(List.of(obj("a", 1, 2), obj("b", 3, 4)));
        assertThat(teleported).isTrue();
        assertThat(animator.isActive()).isFalse();
        assertThat(animator.positionOf("a")).containsExactly(1.0, 2.0);
        assertThat(animator.positionOf("b")).containsExactly(3.0, 4.0);
    }

    /// The type is answerable for any entity the animator knows — including one
    /// mid-animation, which is when the hover panel most needs it — and null for
    /// one it has never seen.
    @Test
    void testTypeOfMatchesPositionOf() {
        assertThat(animator.typeOf("a")).isNull();

        animator.onEventObjects(List.of(obj("a", 1, 2)));
        assertThat(animator.typeOf("a")).isEqualTo("person");
        assertThat(animator.typeOf("nobody")).isNull();
        assertThat(animator.typeOf(null)).isNull();

        // Mid-animation the position comes from the in-flight animation, so the
        // type must come from there too.
        animator.setPlaying(true);
        animator.onEventObjects(List.of(new FloorMapObject("a", "vehicle", 9, 9)));
        animator.advanceFrame(DURATION_MS / 2, DURATION_MS / 2);
        assertThat(animator.isActive()).isTrue();
        assertThat(animator.typeOf("a")).isEqualTo("vehicle");
    }

    /// A teleport prunes state for entities that are no longer present.
    @Test
    void testTeleportPrunesVanishedEntities() {
        animator.onEventObjects(List.of(obj("a", 1, 1), obj("b", 2, 2)));
        animator.onEventObjects(List.of(obj("a", 5, 5)));
        assertThat(animator.positionOf("a")).containsExactly(5.0, 5.0);
        assertThat(animator.positionOf("b")).isNull();
    }

    /// Playing: a moved entity animates and, on frame completion, lands at the target.
    @Test
    void testPlayingAnimatesToTarget() {
        animator.setPlaying(true);
        animator.onEventObjects(List.of(obj("a", 0, 0)));   // first appearance, recorded
        final boolean teleported = animator.onEventObjects(List.of(obj("a", 10, 0)));
        assertThat(teleported).isFalse();
        assertThat(animator.isActive()).isTrue();

        // Halfway through the animation the entity is at the midpoint.
        animator.advanceFrame(DURATION_MS / 2, DURATION_MS / 2);
        final FloorMapObject mid = drawn(animator.buildDrawList(0));
        assertThat(mid.getX()).isCloseTo(5.0, within(0.001));

        // Completing the animation lands it on the target.
        animator.advanceFrame(DURATION_MS, DURATION_MS / 2);
        assertThat(animator.positionOf("a")).containsExactly(10.0, 0.0);
        // A trail fade starts on completion, so it stays active until that ends...
        assertThat(animator.isActive()).isTrue();
        // ...and advancing past the fade duration returns it to idle (fade GC).
        animator.advanceFrame(DURATION_MS + 2000 + 1, 16);
        assertThat(animator.isActive()).isFalse();
    }

    /// While animating A→B, an update back to A must NOT be dropped as unchanged
    /// (regression for the return-to-previous-target bug).
    @Test
    void testReturnToPreviousTargetWhileAnimating() {
        animator.setPlaying(true);
        animator.onEventObjects(List.of(obj("a", 0, 0)));
        animator.onEventObjects(List.of(obj("a", 10, 0)));   // animate 0 → 10
        animator.advanceFrame(DURATION_MS / 2, DURATION_MS / 2); // at x=5

        animator.onEventObjects(List.of(obj("a", 0, 0)));    // back to 0 — must re-animate
        assertThat(animator.isActive()).isTrue();
        animator.advanceFrame(DURATION_MS * 2, DURATION_MS);  // finish
        assertThat(animator.positionOf("a")).containsExactly(0.0, 0.0);
    }

    /// buildDrawList includes stationary (last-known) entities.
    @Test
    void testBuildDrawListIncludesStationary() {
        animator.onEventObjects(List.of(obj("a", 2, 3)));
        final FloorMapObject a = drawn(animator.buildDrawList(0));
        assertThat(a.getX()).isCloseTo(2.0, within(TOL));
        assertThat(a.getY()).isCloseTo(3.0, within(TOL));
    }

    /// clear() arms a teleport: the next update is instant even while playing.
    @Test
    void testClearArmsTeleport() {
        animator.setPlaying(true);
        animator.onEventObjects(List.of(obj("a", 0, 0)));
        animator.clear();
        final boolean teleported = animator.onEventObjects(List.of(obj("a", 99, 99)));
        assertThat(teleported).isTrue();
        assertThat(animator.positionOf("a")).containsExactly(99.0, 99.0);
    }

    private static FloorMapObject drawn(final List<FloorMapObject> list) {
        return list.stream().filter(o -> o.getId().equals("a")).findFirst().orElseThrow();
    }

    // -----------------------------------------------------------------------
    // Trail recording and decimation
    // -----------------------------------------------------------------------

    /// Drives one entity along a straight line for `frames` frames, returning the trail the
    /// renderer would be handed. Each frame advances the animation a little, so a trail point is
    /// recorded per frame.
    private List<double[]> trailAfterFrames(final int frames) {
        animator.setPlaying(true);
        animator.onEventObjects(List.of(obj("a", 0, 0)));
        animator.onEventObjects(List.of(obj("a", 10000, 0)));
        // A long animation so it stays in flight for every frame, recording a point each time.
        for (int i = 1; i <= frames; i++) {
            animator.advanceFrame(i, 1);
        }
        return drawn(animator.buildDrawList(0)).getTrail();
    }

    /// A short trail is passed through point-for-point.
    @Test
    void testShortTrailIsPassedThroughWhole() {
        final List<double[]> trail = trailAfterFrames(50);

        assertThat(trail).hasSize(50);
        // Alpha runs 0 (oldest) to 1 (newest).
        assertThat(trail.getFirst()[2]).isCloseTo(0.0, within(TOL));
        assertThat(trail.getLast()[2]).isCloseTo(1.0, within(TOL));
    }

    /// Every recorded point reaches the renderer, however long the trail.
    ///
    /// Regression test for a decimation pass that sampled every Nth point once a trail passed a
    /// fixed budget. Uniform striding drops whichever points fall between samples, and turning
    /// points are exactly the ones carrying the shape - so the drawn polyline cut across corners
    /// rather than following the route the entity took. Sampling by position cannot preserve shape;
    /// only a shape-aware reduction could.
    @Test
    void testLongTrailKeepsEveryPoint() {
        // A point is recorded per frame while the animation is in flight, so stop short of
        // ANIMATION_DURATION_MS: the move is still running, no fade has started, and the trail is
        // still well past the 400-point budget the removed decimation used - which is the point.
        final List<double[]> trail = trailAfterFrames(700);

        assertThat(trail.size())
                .as("must exceed the old decimation budget, or the test proves nothing")
                .isGreaterThan(400);
        // Consecutive along a straight run: nothing sampled out.
        for (int i = 1; i < trail.size(); i++) {
            assertThat(trail.get(i)[0]).isGreaterThan(trail.get(i - 1)[0]);
        }
        assertThat(trail.getFirst()[2]).isCloseTo(0.0, within(TOL));
        assertThat(trail.getLast()[2]).isCloseTo(1.0, within(TOL));
    }

    /// A direction change ends the trail rather than bending it: the leg the entity is now making
    /// is the whole trail, and the leg it just finished is gone.
    ///
    /// Replaces a test that asserted the opposite - that the corner survived into a trail
    /// spanning both legs. That was the deliberate behaviour until the trail was scoped to one
    /// movement; see [FloorMapEntityAnimator] on why a multi-leg trail is unreadable at
    /// playback speed.
    @Test
    void testATurnEndsTheTrailRatherThanBendingIt() {
        animator.setPlaying(true);
        animator.onEventObjects(List.of(obj("a", 0, 0)));
        animator.onEventObjects(List.of(obj("a", 500, 0)));      // leg 1: rightwards
        for (int i = 1; i <= 600; i++) {
            animator.advanceFrame(i, 1);
        }
        final double[] corner = animator.positionOf("a");
        animator.onEventObjects(List.of(obj("a", corner[0], 500)));  // leg 2: upwards
        for (int i = 601; i <= 1200; i++) {
            animator.advanceFrame(i, 1);
        }

        final List<double[]> trail = drawn(animator.buildDrawList(0)).getTrail();

        assertThat(trail)
                .as("leg 1 ran along y=0 and is over - none of it may still be drawn")
                .allMatch(p -> p[1] > 0.0);
        assertThat(trail)
                .as("the trail is leg 2, which is vertical at the corner's x")
                .allMatch(p -> Math.abs(p[0] - corner[0]) < 1e-6);
    }

    /// The ring buffer must wrap correctly: past its capacity the oldest points are overwritten
    /// and the trail still reads oldest-first, with x strictly increasing along a straight run.
    @Test
    void testTrailStaysOrderedOldestFirstAfterWrapping() {
        final List<double[]> trail = trailAfterFrames(300);

        for (int i = 1; i < trail.size(); i++) {
            assertThat(trail.get(i)[0])
                    .as("x at %d must exceed x at %d", i, i - 1)
                    .isGreaterThan(trail.get(i - 1)[0]);
            assertThat(trail.get(i)[2])
                    .as("alpha at %d must not decrease", i)
                    .isGreaterThanOrEqualTo(trail.get(i - 1)[2]);
        }
    }

    /// A redraw that has no scheduler timestamp of its own must still see the fade.
    ///
    /// Regression test: `buildDrawList` is called both from the animation loop, which has
    /// a timestamp, and from an ordinary redraw - pan, zoom, query refresh - which passes zero. The
    /// fade begins when an animation *finishes*, so a trail is routinely mid-fade with
    /// nothing animating. Treating zero as "no fade" drew that trail at full opacity for the frame,
    /// and the next loop tick restored the faded value, which reads as the trail flickering
    /// bright.
    @Test
    void testFadeIsAppliedWhenTheCallerHasNoTimestamp() {
        animator.setPlaying(true);
        animator.onEventObjects(List.of(obj("a", 0, 0)));
        animator.onEventObjects(List.of(obj("a", 10, 0)));
        // Finish the move, which starts the fade at t=DURATION_MS.
        animator.advanceFrame(DURATION_MS, DURATION_MS);
        // Advance to half way through the 2000ms fade without completing it.
        animator.advanceFrame(DURATION_MS + 1000, 1000);

        final double alphaFromLoop = newestAlpha(animator.buildDrawList(DURATION_MS + 1000));
        final double alphaFromRedraw = newestAlpha(animator.buildDrawList(0));

        assertThat(alphaFromLoop)
                .as("half way through the fade")
                .isCloseTo(0.5, within(0.01));
        assertThat(alphaFromRedraw)
                .as("a timestamp-less redraw must age the fade the same way, not reset it to 1.0")
                .isCloseTo(alphaFromLoop, within(TOL));
    }

    /// Alpha of the newest trail point of the single drawn entity.
    private static double newestAlpha(final List<FloorMapObject> drawList) {
        final List<double[]> trail = drawn(drawList).getTrail();
        return trail.getLast()[2];
    }

    // -----------------------------------------------------------------------
    // Trail ageing
    // -----------------------------------------------------------------------

    /// Trail sections older than the window are dropped, however long the entity keeps moving.
    ///
    /// Regression test: the only things that discarded trail data were a teleport and a fade
    /// that ran to completion, and the fade is cancelled the moment the entity moves again - so an
    /// entity that moved intermittently never lost any. The point cap did not help, bounding
    /// recorded frames rather than elapsed time, so sections minutes old were still drawn.
    @Test
    void testTrailSectionsOlderThanTheWindowAreDropped() {
        animator.setPlaying(true);
        animator.onEventObjects(List.of(obj("a", 0, 0)));
        animator.onEventObjects(List.of(obj("a", 100000, 0)));

        // 40s of wall-clock, one point per frame. deltaMs is kept small so the move is still in
        // flight at the end - timestamp and delta are independent inputs, and this test is about
        // ageing rather than animation duration.
        for (int frame = 1; frame <= 400; frame++) {
            animator.advanceFrame(frame * 100.0, 1.0);
        }
        final double nowMs = 400 * 100.0;

        final List<double[]> trail = drawn(animator.buildDrawList(nowMs)).getTrail();

        // 40s of movement, 20s window -> only the recent half survives.
        assertThat(trail.size())
                .as("should hold roughly the last 20s of points, not all 40s")
                .isBetween(180, 220);
    }

    /// A trail that fits inside the window is untouched by ageing.
    @Test
    void testTrailWithinTheWindowIsNotTrimmed() {
        animator.setPlaying(true);
        animator.onEventObjects(List.of(obj("a", 0, 0)));
        animator.onEventObjects(List.of(obj("a", 100000, 0)));

        // 5s of wall-clock, well inside the 20s window, move still in flight.
        for (int frame = 1; frame <= 50; frame++) {
            animator.advanceFrame(frame * 100.0, 1.0);
        }

        final List<double[]> trail = drawn(animator.buildDrawList(50 * 100.0)).getTrail();

        assertThat(trail).hasSize(50);
    }

    // -----------------------------------------------------------------------
    // Trail lifecycle across a pause
    // -----------------------------------------------------------------------

    /// Movement that resumes after a pause starts a fresh trail rather than resurrecting the one
    /// that was fading.
    ///
    /// Regression test for trails "becoming activated again when a person moves". Cancelling a
    /// fade used to leave the points in place, and with the fade entry gone the fade factor reverts
    /// to 1.0 - so the trail from before the pause reappeared at full opacity and joined up with
    /// the new movement, drawing a journey that was never made in one go. Repeated pauses
    /// accumulated that across the map.
    @Test
    void testResumedMovementStartsAFreshTrail() {
        animator.setPlaying(true);
        animator.onEventObjects(List.of(obj("a", 0, 0)));
        animator.onEventObjects(List.of(obj("a", 10, 0)));       // leg 1: rightwards along y=0
        for (int frame = 1; frame <= 4; frame++) {
            animator.advanceFrame(frame * 200.0, 200.0);         // completes at t=800, fade starts
        }
        assertThat(drawn(animator.buildDrawList(800)).getTrail()).hasSize(4);

        // Part way through the fade, still visible, then the entity moves again.
        animator.advanceFrame(1000, 200);
        animator.onEventObjects(List.of(obj("a", 10, 50)));      // leg 2: upwards from the pause
        animator.advanceFrame(1200, 200);

        final List<double[]> trail = drawn(animator.buildDrawList(1200)).getTrail();

        assertThat(trail)
                .as("only the movement that is happening now")
                .hasSize(1);
        assertThat(trail)
                .as("no point from before the pause may return")
                .noneMatch(p -> p[0] < 10.0 - TOL);
        assertThat(trail.getLast()[2])
                .as("the new trail is not still wearing the cancelled fade")
                .isCloseTo(1.0, within(TOL));
    }

    /// A move whose next target arrives before it finishes starts a new trail, because that next
    /// target is a new movement.
    ///
    /// This is the *normal* case during playback rather than an edge: the timeline runs at a
    /// multiple of real time while the events query is throttled to a fixed wall-clock interval,
    /// so a fresh position lands every few hundred milliseconds and a move is almost always
    /// replaced before it completes. Keeping the points across that accumulated tens of legs into
    /// one polyline, which joins each leg's end to the next one's start.
    @Test
    void testAnInterruptingMoveStartsANewTrail() {
        animator.setPlaying(true);
        animator.onEventObjects(List.of(obj("a", 0, 0)));
        animator.onEventObjects(List.of(obj("a", 800, 0)));      // leg 1 along y=0
        animator.advanceFrame(100, 100);                          // in flight, one point recorded
        animator.onEventObjects(List.of(obj("a", 800, 800)));    // leg 2 before leg 1 finished
        animator.advanceFrame(200, 100);

        final List<double[]> trail = drawn(animator.buildDrawList(200)).getTrail();

        assertThat(trail)
                .as("only the leg being made now")
                .hasSize(1);
        assertThat(trail.getFirst()[1])
                .as("leg 1's point ran along y=0 and must not have been carried over")
                .isGreaterThan(0.0);
    }

    /// The tangle seen in playback, at the cadence that produced it: a new desk every 300ms of
    /// wall clock, indefinitely. However long that runs, an entity carries one leg's worth of
    /// trail - the count must not grow with the number of desks visited.
    @Test
    void testTrailDoesNotGrowAcrossAHundredHops() {
        animator.setPlaying(true);
        animator.onEventObjects(List.of(obj("a", 80, 90)));
        final double[][] desks = {{80, 90}, {200, 90}, {320, 90}, {80, 240}, {200, 240}, {320, 240}};

        double nowMs = 0;
        int largest = 0;
        for (int hop = 1; hop <= 100; hop++) {
            animator.onEventObjects(List.of(obj("a", desks[hop % desks.length][0],
                    desks[hop % desks.length][1])));
            // ~300ms of frames at 60fps before the next position lands, so the 800ms move never
            // completes and no fade ever starts - the condition the old trail accumulated under.
            for (int frame = 0; frame < 18; frame++) {
                nowMs += 16.0;
                animator.advanceFrame(nowMs, 16.0);
            }
            largest = Math.max(largest, drawn(animator.buildDrawList(nowMs)).getTrail().size());
        }

        assertThat(largest)
                .as("one leg is 18 frames here; 100 hops must not accumulate into one polyline")
                .isLessThanOrEqualTo(18);
    }

    /// A fade that runs to completion still clears the trail (unchanged behaviour).
    @Test
    void testCompletedFadeStillClearsTheTrail() {
        animator.setPlaying(true);
        animator.onEventObjects(List.of(obj("a", 0, 0)));
        animator.onEventObjects(List.of(obj("a", 10, 0)));
        animator.advanceFrame(DURATION_MS, DURATION_MS);          // lands; fade starts
        assertThat(drawn(animator.buildDrawList(DURATION_MS)).getTrail()).isNotNull();

        animator.advanceFrame(DURATION_MS + 2000 + 1, 16);        // fade runs out

        assertThat(animator.isActive()).isFalse();
        assertThat(drawn(animator.buildDrawList(DURATION_MS + 2000 + 1)).getTrail())
                .as("a spent fade leaves nothing behind to be revived")
                .isNull();
    }

    /// Nothing older than the age window is ever drawn, whether or not the trail is currently
    /// gaining points - the window bounds what is on screen, not merely what is written.
    @Test
    void testNoDrawnPointIsOlderThanTheWindow() {
        animator.setPlaying(true);
        animator.onEventObjects(List.of(obj("a", 0, 0)));
        animator.onEventObjects(List.of(obj("a", 100000, 0)));

        // 40s of movement in 100ms frames, sampling the drawn trail as it goes.
        for (int frame = 1; frame <= 400; frame++) {
            final double nowMs = frame * 100.0;
            animator.advanceFrame(nowMs, 1.0);
            final List<double[]> trail = drawn(animator.buildDrawList(nowMs)).getTrail();
            // One point is recorded per 100ms frame, so a 20s window holds at most 201 of them.
            assertThat(trail.size())
                    .as("at t=%s the drawn trail must not exceed the 20s window", nowMs)
                    .isLessThanOrEqualTo(201);
        }
    }

    /// Ageing must keep the trail contiguous and ordered - it drops from the old end only, never
    /// leaving a gap in the middle.
    @Test
    void testAgeingKeepsTheTrailContiguousAndOrdered() {
        animator.setPlaying(true);
        animator.onEventObjects(List.of(obj("a", 0, 0)));
        animator.onEventObjects(List.of(obj("a", 100000, 0)));

        for (int frame = 1; frame <= 400; frame++) {
            animator.advanceFrame(frame * 100.0, 1.0);
        }

        final List<double[]> trail = drawn(animator.buildDrawList(400 * 100.0)).getTrail();

        for (int i = 1; i < trail.size(); i++) {
            assertThat(trail.get(i)[0])
                    .as("x must increase along the trail at index %d", i)
                    .isGreaterThan(trail.get(i - 1)[0]);
        }
        // Alpha still spans the full range across whatever survived.
        assertThat(trail.getFirst()[2]).isCloseTo(0.0, within(TOL));
        assertThat(trail.getLast()[2]).isCloseTo(1.0, within(TOL));
    }
}
