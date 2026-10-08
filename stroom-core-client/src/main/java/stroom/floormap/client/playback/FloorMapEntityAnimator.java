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
import stroom.floormap.shared.FloorMapGeometry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The floor-map entity animation "data machine": tracks event entities as they
/// move over time, interpolating between positions, recording fading movement
/// trails, and teleporting on discontinuous time jumps.
///
/// Kept out of the GWT canvas presenter so this logic — the animate-vs-teleport
/// decision, the return-to-previous-target case, trail capping and fade timing — is
/// unit-testable on the JVM. It holds no GWT/DOM
/// types and knows nothing about rendering or scheduling: the presenter owns the
/// `AnimationScheduler` loop, camera-follow and the SVG draw, and drives
/// this class one frame at a time via [#advanceFrame].
///
/// All positions are in map space. The presenter decorates the returned draw
/// list with image "twins" (a view concern) before rendering.
public final class FloorMapEntityAnimator {

    /// Duration of a single entity move animation, in ms.
    private static final double ANIMATION_DURATION_MS = 800.0;

    /// Maximum recorded trail points per entity (bounds memory during long playback).
    private static final int TRAIL_MAX_PTS = 5000;

    /// How long (wall-clock ms) a trail takes to fade out after the entity stops.
    private static final double TRAIL_FADE_DURATION_MS = 2000.0;

    /// The oldest a trail point may be when it is drawn, in scheduler milliseconds.
    ///
    /// A backstop rather than the thing that bounds a trail: [#startAnimation] scopes a
    /// trail to one movement, and a movement lasts [#ANIMATION_DURATION_MS], so a trail
    /// reaches this age only if frames stop arriving mid-movement and then resume - a backgrounded
    /// tab. It is kept because it is the one bound that does not depend on movements continuing to
    /// arrive, and because [#TRAIL_MAX_PTS] cannot stand in for it: the cap bounds recorded
    /// frames, not elapsed time.
    ///
    /// The trim runs once per frame over every trail rather than only over the one a point was
    /// just appended to, so the window bounds what is *drawn* and not merely what is
    /// written - see [#ageTrails].
    private static final double TRAIL_MAX_AGE_MS = 20_000.0;

    /// `true` while the timeline is actively playing.
    private boolean isPlaying = false;

    /// When `true`, the next [#onEventObjects] teleports entities to
    /// their new positions rather than animating, even while playing. Set by
    /// [#clear()] so a scrub/skip places entities instantly.
    private boolean pendingTeleport = false;

    /// In-flight animations keyed by entity id.
    private final Map<String, EntityAnimation> activeAnimations = new HashMap<>();

    /// Last known rendered state (id, type, map position) per entity.
    private final Map<String, FloorMapObject> lastEntityPositions = new HashMap<>();

    /// Trail points per entity; oldest first, capped at [#TRAIL_MAX_PTS].
    private final Map<String, TrailBuffer> entityTrails = new HashMap<>();

    /// Timestamp each entity's last animation finished, initiating the trail fade.
    private final Map<String, Double> trailFadeStartTimes = new HashMap<>();

    /// The most recent timestamp [#advanceFrame] was given, so a draw that has no timestamp
    /// of its own can still age a fade correctly.
    ///
    /// [#buildDrawList(double)] is called both from the animation loop, which has a
    /// scheduler timestamp, and from an ordinary redraw - a pan, a zoom, a query refresh - which
    /// does not and passes zero. A trail can be part-way through its fade while nothing is
    /// animating, because the fade starts exactly when an animation *finishes*. Treating
    /// zero as "no fade" therefore drew a fading trail at full opacity for that frame, and the
    /// next loop tick put it back, which reads as the trail flickering bright.
    ///
    /// Timestamps come from the animation scheduler, so they cannot be substituted with a
    /// wall-clock reading here - the epochs differ. Remembering the last one keeps every fade
    /// calculation in the scheduler's own time base, at worst one frame stale.
    private double lastFrameTimestampMs;

    /// The current non-animated event overlay (set by [#onEventObjects]).
    private List<FloorMapObject> eventObjects = new ArrayList<>();

    /// Sets whether the timeline is playing (drives animate-vs-teleport).
    public void setPlaying(final boolean playing) {
        this.isPlaying = playing;
    }

    /// Discards all in-flight animations and trail data and arms a teleport for
    /// the next [#onEventObjects]. Call on a discontinuous time jump
    /// (scrub/skip/loop-around). Does not touch [#eventObjects] (the last
    /// drawn overlay stays until the next update).
    public void clear() {
        activeAnimations.clear();
        entityTrails.clear();
        trailFadeStartTimes.clear();
        lastFrameTimestampMs = 0;
        pendingTeleport = true;
    }

    /// Applies a fresh set of event entities.
    ///
    /// Teleport path (not playing, or a pending teleport): entities jump to
    /// their new positions, stale per-entity state for vanished entities is
    /// pruned, and trails are dropped. Animate path (playing): each changed
    /// entity starts an animation from its current position; unchanged/animating
    /// ones are owned by the loop.
    ///
    /// @param objects the new entities (may be `null`)
    /// @return `true` if this was a teleport (instant), `false` if it
    ///         started/continued animations (the caller should run the loop)
    public boolean onEventObjects(final List<FloorMapObject> objects) {
        final List<FloorMapObject> objs = objects != null ? objects : new ArrayList<>();

        if (!isPlaying || pendingTeleport) {
            this.eventObjects = objs;
            // Prune per-entity state for entities no longer present so a vanished
            // entity can't linger as a ghost. Only on the teleport path — not on
            // every event — so partial playback result batches don't strip
            // anchors and make a still-present entity teleport instead of animate.
            final Set<String> currentIds = new HashSet<>();
            for (final FloorMapObject obj : objs) {
                currentIds.add(obj.getId());
            }
            lastEntityPositions.keySet().retainAll(currentIds);
            activeAnimations.clear();
            // Teleport = instant placement, so no in-progress journeys/trails.
            entityTrails.clear();
            trailFadeStartTimes.clear();
            for (final FloorMapObject obj : objs) {
                lastEntityPositions.put(obj.getId(), new FloorMapObject(
                        obj.getId(), obj.getType(), obj.getX(), obj.getY()));
            }
            pendingTeleport = false;
            return true;
        }

        final List<FloorMapObject> unanimated = new ArrayList<>();
        for (final FloorMapObject obj : objs) {
            if (handleEntityUpdate(obj)) {
                unanimated.add(obj);
            }
        }
        this.eventObjects = unanimated;
        return false;
    }

    /// Advances all in-flight animations and trail fades by one frame.
    ///
    /// A trail belongs to the movement that is happening now - see [#startAnimation] -
    /// so the only thing this has to end is the fade that follows a movement, and ending it
    /// discards the trail with it.
    ///
    /// @param timestampMs the current scheduler timestamp (ms), for trail fade timing
    /// @param deltaMs      elapsed time since the previous frame (ms), for progress
    /// @return `true` if anything is still animating or fading (the caller
    ///         should keep the loop running)
    public boolean advanceFrame(final double timestampMs, final double deltaMs) {
        lastFrameTimestampMs = timestampMs;

        final List<String> finished = new ArrayList<>();
        for (final Map.Entry<String, EntityAnimation> entry : activeAnimations.entrySet()) {
            final EntityAnimation anim = entry.getValue();
            anim.progress = Math.min(1.0, anim.progress + deltaMs / ANIMATION_DURATION_MS);
            recordTrailPoint(anim.id, anim.currentX(), anim.currentY(), timestampMs);
            if (anim.progress >= 1.0) {
                lastEntityPositions.put(anim.id, new FloorMapObject(
                        anim.id, anim.type, anim.toX, anim.toY));
                finished.add(anim.id);
                trailFadeStartTimes.put(anim.id, timestampMs);
            }
        }
        for (final String id : finished) {
            activeAnimations.remove(id);
        }

        // Expiry is the only way out of a fade here: an entity that starts moving again has
        // already had its fade entry and its trail dropped by startAnimation, so a fade and a
        // live animation never coexist for one id.
        //
        // There used to be a second way out - "moving again, cancel the fade" - which dropped the
        // fade but kept the trail. The new movement then appended to the old trail, which came
        // back at full opacity: the faded trails reappearing when a person moved again.
        final List<String> doneFading = new ArrayList<>();
        for (final Map.Entry<String, Double> fade : trailFadeStartTimes.entrySet()) {
            if (timestampMs - fade.getValue() >= TRAIL_FADE_DURATION_MS) {
                entityTrails.remove(fade.getKey()); // fully faded
                doneFading.add(fade.getKey());
            }
        }
        for (final String id : doneFading) {
            trailFadeStartTimes.remove(id);
        }

        ageTrails(timestampMs);

        return isActive();
    }

    /// Drops trail sections older than [#TRAIL_MAX_AGE_MS] from *every* trail, and
    /// forgets the buffers that empty.
    ///
    /// Per frame rather than per recorded point: a trail that is not gaining points is still
    /// being drawn, and the age window is a claim about what the viewer is shown. Trimming on
    /// append alone made that claim hold only while the entity happened to be moving.
    ///
    /// Cheap: one pass over the live trails, each of which walks its own head forward only past
    /// points that have actually expired, so a steady state costs one comparison per entity.
    private void ageTrails(final double timestampMs) {
        final double cutoffMs = timestampMs - TRAIL_MAX_AGE_MS;
        final Iterator<Map.Entry<String, TrailBuffer>> it = entityTrails.entrySet().iterator();
        while (it.hasNext()) {
            final TrailBuffer trail = it.next().getValue();
            trail.dropOlderThan(cutoffMs);
            if (trail.isEmpty()) {
                // Nothing left to draw, and a buffer is three arrays of TRAIL_MAX_PTS doubles -
                // worth handing back rather than holding per entity that ever moved.
                it.remove();
            }
        }
    }

    /// Builds the event-overlay draw list: the non-animated entities plus each
    /// animated entity at its interpolated position and each stationary entity at
    /// its last position, with trail data attached. Does *not* decorate
    /// with image twins — that is a rendering concern the caller handles.
    ///
    /// @param nowMs current scheduler timestamp (ms) for trail alpha, or `0` if the caller
    ///         has none - an ordinary redraw rather than an animation frame. Zero does not
    ///         mean "no fade": the last frame's timestamp is used instead, because a trail can
    ///         be mid-fade while nothing is animating.
    /// @return the overlay entities to draw
    public List<FloorMapObject> buildDrawList(final double nowMs) {
        final List<FloorMapObject> combined = new ArrayList<>(eventObjects);

        for (final Map.Entry<String, EntityAnimation> entry : activeAnimations.entrySet()) {
            final EntityAnimation anim = entry.getValue();
            final FloorMapObject obj = new FloorMapObject(
                    anim.id, anim.type, anim.currentX(), anim.currentY());
            attachTrail(obj, anim.id, nowMs);
            combined.add(obj);
        }

        final Set<String> drawnIds = new HashSet<>();
        for (final FloorMapObject obj : combined) {
            drawnIds.add(obj.getId());
        }
        for (final Map.Entry<String, FloorMapObject> entry : lastEntityPositions.entrySet()) {
            final String id = entry.getKey();
            if (!drawnIds.contains(id)) {
                final FloorMapObject last = entry.getValue();
                final FloorMapObject obj = new FloorMapObject(
                        id, last.getType(), last.getX(), last.getY());
                attachTrail(obj, id, nowMs);
                combined.add(obj);
            }
        }
        return combined;
    }

    /// Returns the current map-space position of the entity: its live interpolated
    /// animation position, else its last committed position, else its position in
    /// the current overlay. `null` if the animator doesn't know it (the
    /// caller may fall back to a static fact).
    ///
    /// @param id the entity id
    /// @return `{mapX, mapY}`, or `null`
    public double[] positionOf(final String id) {
        if (id == null) {
            return null;
        }
        final EntityAnimation animation = activeAnimations.get(id);
        if (animation != null) {
            return new double[]{animation.currentX(), animation.currentY()};
        }
        final FloorMapObject last = lastEntityPositions.get(id);
        if (last != null) {
            return new double[]{last.getX(), last.getY()};
        }
        for (final FloorMapObject obj : eventObjects) {
            if (id.equals(obj.getId())) {
                return new double[]{obj.getX(), obj.getY()};
            }
        }
        return null;
    }

    /// Returns the entity's type, looked up the same way — and in the same order
    /// — as [#positionOf], so a caller describing an entity cannot end up
    /// reporting one source's type beside another source's position.
    ///
    /// Needed because an event entity's type is not recorded anywhere else on
    /// the client: it arrives with the events query and is then held only here.
    ///
    /// @param id the entity id
    /// @return the type, or `null` if the animator doesn't know the entity
    ///         (the caller may fall back to a static fact)
    public String typeOf(final String id) {
        if (id == null) {
            return null;
        }
        final EntityAnimation animation = activeAnimations.get(id);
        if (animation != null) {
            return animation.type;
        }
        final FloorMapObject last = lastEntityPositions.get(id);
        if (last != null) {
            return last.getType();
        }
        for (final FloorMapObject obj : eventObjects) {
            if (id.equals(obj.getId())) {
                return obj.getType();
            }
        }
        return null;
    }

    /// `true` if any animation is in flight or any trail is still fading.
    public boolean isActive() {
        return !activeAnimations.isEmpty() || !trailFadeStartTimes.isEmpty();
    }

    // -----------------------------------------------------------------------

    /// Records/updates one entity from an event refresh. When not playing (or a
    /// teleport is pending) it just records the anchor position. When playing it
    /// starts an animation from the entity's current position to the new one,
    /// unless it is already heading there. Returns `true` if the caller
    /// should draw the entity itself (not animated), `false` if the animator
    /// now owns it.
    private boolean handleEntityUpdate(final FloorMapObject obj) {
        if (!isPlaying || pendingTeleport) {
            lastEntityPositions.put(obj.getId(), new FloorMapObject(
                    obj.getId(), obj.getType(), obj.getX(), obj.getY()));
            return true;
        }

        final FloorMapObject last = lastEntityPositions.get(obj.getId());
        if (last == null) {
            lastEntityPositions.put(obj.getId(), new FloorMapObject(
                    obj.getId(), obj.getType(), obj.getX(), obj.getY()));
            return true;
        }

        final EntityAnimation existing = activeAnimations.get(obj.getId());
        final boolean alreadyAnimatingToTarget = existing != null
                && Math.abs(existing.toX - obj.getX()) < 0.001
                && Math.abs(existing.toY - obj.getY()) < 0.001;

        if (!alreadyAnimatingToTarget) {
            // Compare the new target against the CURRENT destination (the
            // in-flight animation's endpoint if animating, else the last
            // committed position) so a "return to A" update while animating A→B
            // isn't dropped as unchanged.
            final double refX = existing != null ? existing.toX : last.getX();
            final double refY = existing != null ? existing.toY : last.getY();
            if (FloorMapGeometry.distanceSquared(refX, refY, obj.getX(), obj.getY()) > 0.0001) {
                final double fromX = existing != null ? existing.currentX() : last.getX();
                final double fromY = existing != null ? existing.currentY() : last.getY();
                startAnimation(new EntityAnimation(
                        obj.getId(), obj.getType(), fromX, fromY, obj.getX(), obj.getY()));
                return false;
            }
        }
        return false;
    }

    /// Starts an entity's movement, replacing any movement it was already making - and with it,
    /// the trail.
    ///
    /// **A trail shows the movement in progress and nothing else.** It used to span every
    /// movement inside [#TRAIL_MAX_AGE_MS], which sounds like recent history and is not:
    /// playback runs the timeline at a multiple of real time (a thousandfold at x1) while the
    /// events query is throttled to a fixed wall-clock interval, so the number of movements inside
    /// a wall-clock window is a property of the playback speed, not of the data. At x1 over a store
    /// whose entities move every couple of minutes, a twenty-second window held something like
    /// sixty-six of them - drawn as one polyline, which joins each movement's end to the next one's
    /// start and covers the map in lines nobody walked.
    ///
    /// The consequence to know about: a trail is now a single interpolated leg, so it is always
    /// straight and never shows a turn. That was a deliberate property of the old behaviour - see
    /// the decimation note in [#attachTrail] - and it has been traded for a trail that states
    /// something true at any playback speed.
    private void startAnimation(final EntityAnimation animation) {
        activeAnimations.put(animation.id, animation);
        // The previous movement is over, so its points and any fade of them go now rather than
        // being extended into this one.
        entityTrails.remove(animation.id);
        trailFadeStartTimes.remove(animation.id);
    }

    /// Attaches an `{x, y, alpha}` trail to `obj`: alpha runs 0 (oldest)
    /// → 1 (newest), scaled by a global fade factor once the entity has stopped.
    private void attachTrail(final FloorMapObject obj, final String id, final double nowMs) {
        final TrailBuffer raw = entityTrails.get(id);
        if (raw == null || raw.isEmpty()) {
            return;
        }
        double fadeFactor = 1.0;
        final Double fadeStart = trailFadeStartTimes.get(id);
        // A redraw outside the animation loop passes 0; fall back to the last frame's timestamp so
        // the fade is aged consistently whoever is drawing. See lastFrameTimestampMs.
        final double effectiveNowMs = nowMs > 0
                ? nowMs
                : lastFrameTimestampMs;
        if (fadeStart != null && effectiveNowMs > 0) {
            final double elapsed = effectiveNowMs - fadeStart;
            fadeFactor = Math.max(0.0, 1.0 - elapsed / TRAIL_FADE_DURATION_MS);
        }
        if (fadeFactor <= 0.0) {
            return;
        }
        // Every recorded point is rendered. An earlier version decimated to a fixed budget on the
        // grounds that a 6px path through 400 points looks like one through 5000 - true of a
        // straight run, false of a winding one. Uniform striding skips whichever points happen to
        // fall between samples, and turning points are exactly the ones that carry the shape: on a
        // 12-leg path decimated 13:1, ten of the twelve corners were dropped and the trail cut
        // across them instead of following the route the entity took. Any future decimation has to
        // be shape-preserving (Ramer-Douglas-Peucker or similar), not positional.
        //
        // A trail is a single interpolated leg now (see startAnimation), so it has no corners left
        // to lose and the budget could not be reached anyway - one movement is ANIMATION_DURATION_MS
        // of frames. The rule is kept because it is about how to reduce a path, and a trail that
        // spans more than one leg again would bring the corners back with it.
        final int size = raw.size();
        final int last = size - 1;
        final List<double[]> trailWithAlpha = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            final double alpha = (size == 1 ? 1.0 : (double) i / last) * fadeFactor;
            trailWithAlpha.add(new double[]{raw.pointX(i), raw.pointY(i), alpha});
        }
        obj.setTrail(trailWithAlpha);
    }

    /// Appends `(x, y)` to the entity's trail, overwriting the oldest once at the cap.
    ///
    /// Ageing is not done here. It belongs to the frame rather than to the append - see
    /// [#ageTrails] - so that a trail nobody is adding to is aged as well.
    private void recordTrailPoint(final String id,
                                  final double x,
                                  final double y,
                                  final double timestampMs) {
        //noinspection unused k
        final TrailBuffer trail = entityTrails.computeIfAbsent(id, k -> new TrailBuffer(TRAIL_MAX_PTS));
        trail.add(x, y, timestampMs);
    }

    // -----------------------------------------------------------------------

    /// A fixed-capacity ring buffer of `(x, y)` trail points, oldest first.
    ///
    /// Replaces a `List<double[]>` that dropped its oldest point with `remove(0)`.
    /// Once an entity's trail reached the cap that shifted every remaining element down by one,
    /// per entity, per frame - so the cost of keeping a long trail grew with its length, exactly
    /// when the frame budget was tightest. Appending here is constant time and allocates nothing:
    /// coordinates live in two flat arrays rather than one small array per point.
    private static final class TrailBuffer {

        private final double[] xs;
        private final double[] ys;
        /// Scheduler timestamp each point was recorded at, for age trimming.
        private final double[] ts;
        /// Index of the oldest point once full; otherwise 0.
        private int head;
        private int size;

        private TrailBuffer(final int capacity) {
            this.xs = new double[capacity];
            this.ys = new double[capacity];
            this.ts = new double[capacity];
        }

        private void add(final double x, final double y, final double timestampMs) {
            final int slot = (head + size) % xs.length;
            xs[slot] = x;
            ys[slot] = y;
            ts[slot] = timestampMs;
            if (size < xs.length) {
                size++;
            } else {
                // Full: the write consumed the oldest slot, so the oldest is now the next one.
                head = (head + 1) % xs.length;
            }
        }

        /// Drops points recorded before `cutoffMs`. Points are appended in time order, so
        /// this only ever walks the head forward and stops at the first point still in range.
        private void dropOlderThan(final double cutoffMs) {
            while (size > 0 && ts[head] < cutoffMs) {
                head = (head + 1) % ts.length;
                size--;
            }
        }

        private boolean isEmpty() {
            return size == 0;
        }

        private int size() {
            return size;
        }

        /// @param i index from the oldest point (0) to the newest (`size() - 1`).
        private double pointX(final int i) {
            return xs[(head + i) % xs.length];
        }

        /// @param i index from the oldest point (0) to the newest (`size() - 1`).
        private double pointY(final int i) {
            return ys[(head + i) % ys.length];
        }
    }

    /// A single in-flight entity move, interpolated linearly by [#progress].
    private static final class EntityAnimation {

        private final String id;
        private final String type;
        private final double fromX;
        private final double fromY;
        private final double toX;
        private final double toY;
        private double progress; // 0.0 → 1.0

        EntityAnimation(final String id,
                        final String type,
                        final double fromX,
                        final double fromY,
                        final double toX,
                        final double toY) {
            this.id = id;
            this.type = type;
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
            this.progress = 0.0;
        }

        double currentX() {
            return fromX + (toX - fromX) * progress;
        }

        double currentY() {
            return fromY + (toY - fromY) * progress;
        }
    }
}
