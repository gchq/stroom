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

package stroom.floormap.client.cluster;

import java.util.List;
import java.util.Objects;

/**
 * One group of entities merged into a single canvas glyph because they are
 * closer together on screen than a glyph is wide — see
 * {@link FloorMapClusterOverlay} for how they are formed.
 *
 * <p>A cluster is always <strong>homogeneous</strong>: every member shares one
 * type, so the merged glyph keeps that type's shape, colour and layer dimming,
 * and the caption can name it ("10 users") rather than falling back to a
 * type-less count.</p>
 *
 * <p>Holds no GWT or DOM types so it can be unit-tested on the JVM.</p>
 */
public final class FloorMapCluster {

    /**
     * How much wider a cluster's glyph gets per ten-fold increase in its member
     * count. A pair is barely bigger than a lone entity; ten are noticeably
     * bigger; a hundred bigger again.
     */
    private static final double GROWTH_PER_DECADE = 0.35;

    /**
     * The widest a cluster glyph may get, as a multiple of a lone entity's. Past
     * this the badge starts hiding more of the floor plan than the count is
     * worth, and the number in the pill carries the magnitude anyway.
     */
    private static final double MAX_SIZE_FACTOR = 1.8;

    private final String key;
    private final String type;
    private final List<String> memberIds;
    private final double mapX;
    private final double mapY;
    private final String focusedMemberId;

    /**
     * @param key             the cluster's identity for this frame — its
     *                        <em>lowest</em> member id, not the seed's; see
     *                        {@link #getKey()}, which explains why
     * @param type            the shared entity type, or {@code null} when the
     *                        members carry no type
     * @param memberIds       the member ids, already sorted and unmodifiable
     * @param mapX            where to draw, in map space (see {@link #getMapX()})
     * @param mapY            where to draw, in map space
     * @param focusedMemberId the tracked/selected member, or {@code null}
     */
    FloorMapCluster(final String key,
                    final String type,
                    final List<String> memberIds,
                    final double mapX,
                    final double mapY,
                    final String focusedMemberId) {
        this.key = key;
        this.type = type;
        this.memberIds = memberIds;
        this.mapX = mapX;
        this.mapY = mapY;
        this.focusedMemberId = focusedMemberId;
    }

    /**
     * The cluster's identity: its <strong>lowest member id</strong>.
     *
     * <p>Used as the suffix of the glyph's SVG element id, and as the lookup key
     * when a hover or a click has to find the cluster again. The lowest member
     * rather than the one that seeded the merge, so a cluster keeps its identity
     * when it merely gains a member — that decides whether an open hover panel
     * survives the next frame, and the seed cannot promise it, since which member
     * seeds a cluster changes with the membership.</p>
     *
     * <p>It is still only as stable as the membership's <em>floor</em>: a cluster
     * that loses its lowest member, or absorbs one lower, is keyed differently
     * afterwards. Callers must treat a key that no longer resolves as "the cluster
     * went away".</p>
     */
    public String getKey() {
        return key;
    }

    /**
     * The type shared by every member, or {@code null} if the members carry no
     * type. Drives the glyph's shape and colour, its layer's dimming, and the
     * caption's noun.
     */
    public String getType() {
        return type;
    }

    /**
     * The member ids, sorted so the order does not depend on the query row
     * order. Never fewer than two — a lone entity is not clustered.
     */
    public List<String> getMemberIds() {
        return memberIds;
    }

    /**
     * Where the merged glyph is drawn, in map space — the members' centroid,
     * except when one of them is {@link #getFocusedMemberId() focused}, in which
     * case it is <em>that member's own position</em>.
     *
     * <p>The exception is what lets a tracked entity keep working inside a crowd.
     * The glyph then sits exactly where the camera is pointing rather than at the
     * crowd's average, so following someone does not drift off them as the people
     * around them move.</p>
     */
    public double getMapX() {
        return mapX;
    }

    /** Where the merged glyph is drawn, in map space. See {@link #getMapX()}. */
    public double getMapY() {
        return mapY;
    }

    /**
     * The tracked or selected member this cluster is drawn around, or
     * {@code null} when none of its members is focused.
     *
     * <p>A focused cluster is drawn <em>as</em> that member: at their position,
     * carrying the selection ring, and captioned with their name. One glyph serves
     * for both because clusters are homogeneous — the glyph comes from the shared
     * type, so a focused member's glyph and its cluster's are the same shape and
     * colour anyway.</p>
     *
     * <p>Where more than one member is focused (not reachable from the Map tab,
     * whose selection is single) this is the first in {@link #getMemberIds()}
     * order, so the choice is at least deterministic.</p>
     */
    public String getFocusedMemberId() {
        return focusedMemberId;
    }

    /** {@code true} if the tracked or selected entity is one of this cluster's members. */
    public boolean hasFocusedMember() {
        return focusedMemberId != null;
    }

    /** How many entities this cluster stands for; always at least two. */
    public int size() {
        return memberIds.size();
    }

    /**
     * How much bigger this cluster's glyph is drawn than a lone entity's — a
     * multiplier on the glyph box, never below {@code 1}.
     *
     * @see #sizeFactor(int)
     */
    public double getSizeFactor() {
        return sizeFactor(size());
    }

    /**
     * How much bigger a glyph standing for {@code memberCount} entities is drawn
     * than a lone entity's.
     *
     * <p>Growth is logarithmic and capped: the difference between 2 and 20 should
     * be visible at a glance, but a crowd of 500 cannot be allowed to blot out the
     * floor plan, and it does not need to — the count pill states the number
     * exactly. This is the same curve every map clusterer uses, for the same
     * reason.</p>
     *
     * <p>Lives here rather than in the renderer because
     * {@link FloorMapClusterOverlay} needs it too: a bigger glyph covers more
     * ground, so it has to merge anything within <em>its</em> reach rather than a
     * lone entity's, or the badge is drawn over entities it does not speak
     * for.</p>
     *
     * @param memberCount how many entities the glyph stands for
     * @return the multiplier, in {@code [1, }{@value #MAX_SIZE_FACTOR}{@code ]}
     */
    public static double sizeFactor(final int memberCount) {
        if (memberCount < 2) {
            return 1.0;
        }
        return Math.min(1.0 + GROWTH_PER_DECADE * Math.log10(memberCount), MAX_SIZE_FACTOR);
    }

    /** The widest any cluster glyph can be drawn, as a multiple of a lone entity's. */
    public static double maxSizeFactor() {
        return MAX_SIZE_FACTOR;
    }

    /**
     * {@code true} if the given entity is one of this cluster's members.
     *
     * @param id an entity id; may be {@code null}
     */
    public boolean contains(final String id) {
        return id != null && memberIds.contains(id);
    }

    /**
     * The caption drawn under the glyph when no member is focused, e.g.
     * {@code "10 users"}.
     *
     * <p>Callers that may be handed a focused cluster should use
     * {@link FloorMapClusterLabel#captionFor} instead, which names the focused
     * member — that wording needs a display name, which this class does not
     * have.</p>
     *
     * @see FloorMapClusterLabel#describe(int, String)
     */
    public String getLabel() {
        return FloorMapClusterLabel.describe(size(), type);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof final FloorMapCluster that)) {
            return false;
        }
        return Objects.equals(key, that.key)
                && Objects.equals(type, that.type)
                && memberIds.equals(that.memberIds)
                && Double.compare(mapX, that.mapX) == 0
                && Double.compare(mapY, that.mapY) == 0
                && Objects.equals(focusedMemberId, that.focusedMemberId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, type, memberIds, mapX, mapY, focusedMemberId);
    }

    @Override
    public String toString() {
        return "FloorMapCluster{" + getLabel()
                + " at (" + mapX + "," + mapY + ")"
                + " members=" + memberIds
                + "}";
    }
}
