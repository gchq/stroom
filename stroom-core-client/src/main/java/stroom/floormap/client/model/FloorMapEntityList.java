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

package stroom.floormap.client.model;

import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Accumulating roster of every entity seen on a floor map — moving entities
 * from the events stream (people, assets, vehicles, or any other typed event
 * object) plus the static facts from the facts query (objects, backgrounds
 * and areas).
 *
 * <p>The events query at a given playback instant only returns entities with
 * events near that time, so this roster is a union of everything seen since
 * the last {@link #clear()} — entities are never removed when absent from a
 * refresh. This keeps the tracking panel's rows (and the user's selection)
 * stable across the ~300ms playback query refreshes.</p>
 *
 * <p>Holds no GWT or DOM types so it can be unit-tested on the JVM.</p>
 */
public class FloorMapEntityList {

    private final Map<String, EntityEntry> byId = new HashMap<>();

    /**
     * The text to caption an entity with on the canvas.
     *
     * <p>Precedence, and the reasoning for it:</p>
     * <ol>
     *   <li><strong>The fact's {@code LABEL}.</strong> This is the name the user
     *       typed into the properties dialog's Name field, so it is the most
     *       specific answer available and it is what they expect to see.</li>
     *   <li><strong>The owning tab's resolver.</strong> Live event entities have no
     *       {@link Fact} behind them, so the resolver is the only source of a name
     *       for them.</li>
     *   <li><strong>The key, shortened.</strong> Last resort — see
     *       {@link #displayName(String)}.</li>
     * </ol>
     *
     * <p>The label deliberately outranks the resolver here, which is the opposite
     * of the order the hover tooltip uses. The reason is that the roster's resolver
     * returns a <em>key-derived</em> name for everything except areas (see
     * {@link #displayNameFor}), and that name is never blank — so consulting the
     * resolver first would silently discard a user-supplied label for every object
     * and background. Putting the resolver first would make this method a no-op for
     * exactly the facts it exists to serve.</p>
     *
     * @param id       the entity id; may be {@code null}
     * @param label    the fact's {@code LABEL} value, or {@code null} when it has
     *                 none or there is no fact (a live event entity)
     * @param resolver the owning tab's name resolver, or {@code null}
     * @return the caption text; never {@code null}, though it may be empty when
     *         {@code id} is
     */
    public static String captionFor(final String id,
                                    final String label,
                                    final Function<String, String> resolver) {
        if (NullSafe.isNonBlankString(label)) {
            return label.trim();
        }
        if (resolver != null) {
            final String resolved = resolver.apply(id);
            if (NullSafe.isNonBlankString(resolved)) {
                return resolved.trim();
            }
        }
        final String shortened = displayName(id);
        return shortened != null ? shortened : "";
    }

    /**
     * Derives the short display name for an entity id, matching the rule used
     * for canvas labels: the portion before an {@code '@'} when one is present
     * beyond the first character (email-style person ids), otherwise the full id.
     */
    public static String displayName(final String id) {
        if (id == null) {
            return null;
        }
        final int atIdx = id.indexOf('@');
        return atIdx > 0
                ? id.substring(0, atIdx)
                : id;
    }

    /**
     * Merges the entities from a query refresh into the roster. Every event
     * object is admitted regardless of type; null/empty ids are ignored and
     * repeated ids are deduplicated (first-seen type wins).
     *
     * @param objects the objects from the latest refresh; may be {@code null}
     * @return {@code true} if the roster membership changed <em>or</em> an entity already in the
     *         roster was promoted from fact-only to from-events, so callers can skip re-pushing
     *         unchanged data to the grid
     */
    public boolean update(final List<FloorMapObject> objects) {
        if (objects == null) {
            return false;
        }
        boolean changed = false;
        for (final FloorMapObject object : objects) {
            if (object != null) {
                changed |= admit(object.getId(), object.getType(), null, true);
            }
        }
        return changed;
    }

    /**
     * Merges the static facts from a facts query refresh into the roster —
     * objects, backgrounds and areas alike. Admission rules match
     * {@link #update(List)}: null/empty keys are ignored and repeated keys are
     * deduplicated (first-seen type wins, including against an event entity
     * already holding the same id).
     *
     * <p><strong>Areas</strong> are named by their {@code LABEL} rather than
     * their key — see {@link #displayNameFor(Fact)}. Because admission is
     * first-seen-wins, a rename only shows up once the roster is
     * {@link #clear()}ed, which happens on every document (re-)read.</p>
     *
     * @param facts the facts from the latest facts query; may be {@code null}
     * @return {@code true} only if the roster membership changed, so callers
     *         can skip re-pushing unchanged data to the grid
     */
    public boolean updateFacts(final List<Fact> facts) {
        if (facts == null) {
            return false;
        }
        boolean changed = false;
        for (final Fact fact : facts) {
            if (fact != null) {
                changed |= admit(fact.getKey(), fact.getType(), displayNameFor(fact), false);
            }
        }
        return changed;
    }

    /**
     * The roster display name for a fact.
     *
     * <p><strong>Areas</strong> show their user-facing {@code LABEL} name when
     * they have one — an area's key is an opaque generated id, so it reads as
     * noise in the panel, and the name is what the user typed in the properties
     * dialog. Every other fact keeps the key-derived name, so objects and
     * backgrounds are unaffected.</p>
     */
    private static String displayNameFor(final Fact fact) {
        if (fact.isArea()) {
            final String label = fact.getLabelOrNull();
            if (label != null) {
                return label.trim();
            }
        }
        return displayName(fact.getKey());
    }

    /**
     * Admits one entity into the roster if its id is usable and not already
     * present.
     *
     * <p>An id already held as a fact-only entity is <em>promoted</em> when it
     * later turns up in the events stream: it is a moving entity, and the
     * tracking panel's default events-only view must show it whichever query
     * happened to see it first. Only the flag changes — the first-seen name and
     * type are kept, so an area promoted this way keeps its {@code LABEL}
     * name. The reverse never happens: an event entity is not demoted by a fact
     * carrying the same key.</p>
     *
     * @param displayName the name to show, or {@code null} to derive one from
     *                    the id
     * @param fromEvents  {@code true} when this sighting came from the events
     *                    stream, {@code false} for a static fact
     * @return {@code true} if the roster membership — or an entity's
     *         fact/event classification — changed
     */
    private boolean admit(final String id,
                          final String type,
                          final String displayName,
                          final boolean fromEvents) {
        if (NullSafe.isEmptyString(id)) {
            return false;
        }
        final EntityEntry existing = byId.get(id);
        if (existing != null) {
            if (fromEvents && !existing.isFromEvents()) {
                byId.put(id, new EntityEntry(
                        id, existing.getDisplayName(), existing.getType(), true));
                return true;
            }
            return false;
        }
        byId.put(id, new EntityEntry(
                id,
                displayName != null ? displayName : displayName(id),
                type != null ? type : "",
                fromEvents));
        return true;
    }

    /**
     * Returns the roster sorted by display name (case-insensitive), with the
     * full id as a tiebreak so the order is stable.
     */
    public List<EntityEntry> getEntities() {
        final List<EntityEntry> entities = new ArrayList<>(byId.values());
        entities.sort((a, b) -> {
            final int cmp = a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
            return cmp != 0
                    ? cmp
                    : a.getId().compareTo(b.getId());
        });
        return entities;
    }

    public boolean contains(final String id) {
        return id != null && byId.containsKey(id);
    }

    /**
     * The display name already resolved for an entity, or {@code null} if it is
     * not in the roster.
     *
     * <p>Lets callers that need to name an entity <em>elsewhere</em> — the
     * tracking panel's Area column naming an area — reuse the one naming rule
     * rather than re-deriving it and drifting from what the entity's own row
     * shows.</p>
     */
    public String getDisplayName(final String id) {
        final EntityEntry entry = id != null
                ? byId.get(id)
                : null;
        return entry != null
                ? entry.getDisplayName()
                : null;
    }

    /**
     * The type already recorded for an entity, or {@code null} if it is not in the
     * roster.
     *
     * <p>The companion to {@link #getDisplayName}, and a map lookup for the same
     * reason: callers naming many entities at once — the cluster member list, which
     * can hold hundreds — must not go through {@link #getEntities()}, which
     * allocates and sorts the whole roster on every call.</p>
     */
    public String getType(final String id) {
        final EntityEntry entry = id != null
                ? byId.get(id)
                : null;
        return entry != null
                ? entry.getType()
                : null;
    }

    public void clear() {
        byId.clear();
    }

    /**
     * A single entity row. Equality is on {@link #id} only so that a re-created
     * entry for the same entity compares equal to the one a selection model is
     * already holding — a grid data refresh must not read as a selection change.
     */
    @SuppressWarnings("ClassCanBeRecord") // Record not supported in GWT
    public static class EntityEntry {

        private final String id;
        private final String displayName;
        private final String type;
        private final boolean fromEvents;

        public EntityEntry(final String id,
                           final String displayName,
                           final String type,
                           final boolean fromEvents) {
            this.id = id;
            this.displayName = displayName;
            this.type = type;
            this.fromEvents = fromEvents;
        }

        public String getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getType() {
            return type;
        }

        /**
         * Whether this entity has been seen in the events stream — a moving
         * person, vehicle or asset — as opposed to being a static fact only
         * (an object, background or area).
         *
         * <p>The tracking panel shows event entities by default and folds the
         * fact-only rows in behind its "Show Facts" toggle.</p>
         */
        public boolean isFromEvents() {
            return fromEvents;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof final EntityEntry that)) {
                return false;
            }
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id);
        }
    }
}
