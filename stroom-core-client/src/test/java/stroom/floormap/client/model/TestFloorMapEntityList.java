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

import stroom.floormap.client.model.FloorMapEntityList.EntityEntry;
import stroom.floormap.shared.FloorMapJsonKeys;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class TestFloorMapEntityList {

    private FloorMapEntityList entityList;

    @BeforeEach
    void setUp() {
        entityList = new FloorMapEntityList();
    }

    private static FloorMapObject person() {
        return new FloorMapObject("alice@example.com", FloorMapJsonKeys.PERSON, 0, 0);
    }

    private static FloorMapObject entity(final String id, final String type) {
        return new FloorMapObject(id, type, 0, 0);
    }

    // -----------------------------------------------------------------------
    // displayName
    // -----------------------------------------------------------------------

    /**
     * An email-style id is shortened to the portion before the '@', matching
     * the canvas label rule.
     */
    @Test
    void testDisplayNameEmail() {
        assertThat(FloorMapEntityList.displayName("alice@example.com")).isEqualTo("alice");
    }

    /**
     * An id without an '@' is used verbatim.
     */
    @Test
    void testDisplayNamePlainId() {
        assertThat(FloorMapEntityList.displayName("forklift-1")).isEqualTo("forklift-1");
    }

    /**
     * A leading '@' does not produce an empty display name — the full id is
     * kept (the canvas rule only shortens when the '@' is beyond index 0).
     */
    @Test
    void testDisplayNameLeadingAt() {
        assertThat(FloorMapEntityList.displayName("@odd-id")).isEqualTo("@odd-id");
    }

    @Test
    void testDisplayNameNull() {
        assertThat(FloorMapEntityList.displayName(null)).isNull();
    }

    // -----------------------------------------------------------------------
    // Admission — every event type is tracked
    // -----------------------------------------------------------------------

    /**
     * All typed event objects are admitted, not just persons — the tracking
     * panel covers everything coming through the events stream.
     */
    @Test
    void testAdmitsAllTypes() {
        final boolean changed = entityList.update(Arrays.asList(
                person(),
                entity("forklift-1", "vehicle"),
                entity("asset-42", "object"),
                entity("untyped-1", null)));

        assertThat(changed).isTrue();
        assertThat(entityList.getEntities())
                .extracting(EntityEntry::getId)
                .containsExactly("alice@example.com", "asset-42", "forklift-1", "untyped-1");
    }

    /**
     * The entry records the entity's type; a null type is stored as an empty
     * string so grid columns never render "null".
     */
    @Test
    void testStoresType() {
        entityList.update(Arrays.asList(
                person(),
                entity("forklift-1", "vehicle"),
                entity("untyped-1", null)));

        assertThat(entityList.getEntities())
                .extracting(EntityEntry::getType)
                .containsExactly("person", "vehicle", "");
    }

    /**
     * Entities with null or empty ids cannot be listed or tracked and are skipped.
     */
    @Test
    void testSkipsNullAndEmptyIds() {
        final boolean changed = entityList.update(Arrays.asList(
                entity(null, "vehicle"),
                entity("", "vehicle"),
                null));

        assertThat(changed).isFalse();
        assertThat(entityList.getEntities()).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Facts admission — the tracking panel covers static facts too
    // -----------------------------------------------------------------------

    /**
     * Static facts of every kind — point objects, backgrounds and areas — are
     * admitted alongside event entities, keyed by their fact key.
     */
    @Test
    void testUpdateFactsAdmitsAllKinds() {
        final boolean changed = entityList.updateFacts(Arrays.asList(
                fact("gate-1", "gate"),
                fact("background", "background"),
                fact("zone-a", "area")));

        assertThat(changed).isTrue();
        assertThat(entityList.getEntities())
                .extracting(EntityEntry::getId)
                .containsExactly("background", "gate-1", "zone-a");
        assertThat(entityList.getEntities())
                .extracting(EntityEntry::getType)
                .containsExactly("background", "gate", "area");
    }

    /**
     * Facts with null or empty keys cannot be listed or tracked and are
     * skipped, as are null list elements and a null list.
     */
    @Test
    void testUpdateFactsSkipsNullAndEmptyKeys() {
        assertThat(entityList.updateFacts(Arrays.asList(
                fact(null, "gate"),
                fact("", "gate"),
                null))).isFalse();
        assertThat(entityList.updateFacts(null)).isFalse();
        assertThat(entityList.getEntities()).isEmpty();
    }

    /**
     * Facts deduplicate against entries already admitted from the events
     * stream (and vice versa) — first-seen type wins, and a repeat merge
     * reports no change so the grid is not refreshed.
     */
    @Test
    void testUpdateFactsDedupesAgainstEvents() {
        entityList.update(Collections.singletonList(entity("gate-1", "vehicle")));

        assertThat(entityList.updateFacts(
                Collections.singletonList(fact("gate-1", "gate")))).isFalse();

        final List<EntityEntry> entities = entityList.getEntities();
        assertThat(entities).hasSize(1);
        assertThat(entities.getFirst().getType()).isEqualTo("vehicle");
    }

    // -----------------------------------------------------------------------
    // Fact / event classification — backs the tracking panel's Show Facts toggle
    // -----------------------------------------------------------------------

    /**
     * Entities from the events stream are flagged as such and static facts are
     * not, so the tracking panel can list only what moves by default.
     */
    @Test
    void testClassifiesEventsAndFacts() {
        entityList.update(Collections.singletonList(person()));
        entityList.updateFacts(Collections.singletonList(fact("gate-1", "gate")));

        assertThat(entityList.getEntities())
                .extracting(EntityEntry::getId, EntityEntry::isFromEvents)
                .containsExactly(
                        tuple("alice@example.com", true),
                        tuple("gate-1", false));
    }

    /**
     * A fact-only entity that later turns up in the events stream is promoted
     * to an event entity — it moves, so the default events-only view must list
     * it whichever query saw it first. The promotion reports a change so the
     * grid re-filters, and the first-seen name and type are kept.
     */
    @Test
    void testFactIsPromotedWhenItStartsEmittingEvents() {
        entityList.updateFacts(Collections.singletonList(area("area-1", "Loading Bay")));

        assertThat(entityList.update(Collections.singletonList(entity("area-1", "person"))))
                .isTrue();

        assertThat(entityList.getEntities())
                .extracting(EntityEntry::getId,
                        EntityEntry::getDisplayName,
                        EntityEntry::getType,
                        EntityEntry::isFromEvents)
                .containsExactly(tuple("area-1", "Loading Bay", "area", true));

        // Already promoted — a repeat sighting is not a change.
        assertThat(entityList.update(Collections.singletonList(entity("area-1", "person"))))
                .isFalse();
    }

    /**
     * The reverse never happens: a fact carrying the same key as an entity
     * already seen in the events stream does not demote it back out of the
     * default view.
     */
    @Test
    void testEventEntityIsNotDemotedByFact() {
        entityList.update(Collections.singletonList(entity("gate-1", "vehicle")));
        entityList.updateFacts(Collections.singletonList(fact("gate-1", "gate")));

        assertThat(entityList.getEntities())
                .extracting(EntityEntry::isFromEvents)
                .containsExactly(true);
    }

    // -----------------------------------------------------------------------
    // Area naming — areas are named by their LABEL, other facts are not
    // -----------------------------------------------------------------------

    /**
     * An area is named by its user-facing LABEL, because its key is an opaque
     * generated id that reads as noise in the tracking panel.
     */
    @Test
    void testAreaUsesLabelAsDisplayName() {
        entityList.updateFacts(Collections.singletonList(
                area("area-7f2a3c", "Loading Bay")));

        assertThat(entityList.getDisplayName("area-7f2a3c")).isEqualTo("Loading Bay");
        assertThat(entityList.getEntities())
                .extracting(EntityEntry::getDisplayName)
                .containsExactly("Loading Bay");
        // The id itself is untouched — it remains the identity used for tracking.
        assertThat(entityList.getEntities())
                .extracting(EntityEntry::getId)
                .containsExactly("area-7f2a3c");
    }

    /**
     * A keyed type lookup, the companion to {@link FloorMapEntityList#getDisplayName}.
     * Callers naming many entities at once — the cluster member list, which can
     * hold hundreds — need this rather than scanning {@code getEntities()}, which
     * allocates and sorts the whole roster on every call.
     */
    @Test
    void testGetType() {
        entityList.update(Arrays.asList(
                entity("alice@example.com", "person"),
                entity("forklift-1", "vehicle")));
        entityList.updateFacts(Collections.singletonList(fact("desk-3", "object")));

        assertThat(entityList.getType("alice@example.com")).isEqualTo("person");
        assertThat(entityList.getType("forklift-1")).isEqualTo("vehicle");
        assertThat(entityList.getType("desk-3")).isEqualTo("object");
        assertThat(entityList.getType("never-seen")).isNull();
        assertThat(entityList.getType(null)).isNull();
    }

    /** An unnamed or blank-named area falls back to its key. */
    @Test
    void testAreaWithoutLabelFallsBackToKey() {
        entityList.updateFacts(Arrays.asList(
                area("area-1", null),
                area("area-2", "   ")));

        assertThat(entityList.getDisplayName("area-1")).isEqualTo("area-1");
        assertThat(entityList.getDisplayName("area-2")).isEqualTo("area-2");
    }

    /** A label is trimmed, so stray whitespace does not reach the grid. */
    @Test
    void testAreaLabelIsTrimmed() {
        entityList.updateFacts(Collections.singletonList(
                area("area-1", "  Loading Bay  ")));

        assertThat(entityList.getDisplayName("area-1")).isEqualTo("Loading Bay");
    }

    /**
     * Only areas are renamed. A named non-area fact keeps its key-derived name,
     * so objects and backgrounds read exactly as they did before.
     */
    @Test
    void testNonAreaFactKeepsKeyDerivedName() {
        final Fact namedGate = new Fact("gate-1", "gate", null, null, null,
                null, null, null, "Front Door");
        entityList.updateFacts(Collections.singletonList(namedGate));

        assertThat(entityList.getDisplayName("gate-1")).isEqualTo("gate-1");
    }

    /**
     * A fact carrying an image is not an area even with vertices (the renderer
     * paints the image), so it is not renamed either.
     */
    @Test
    void testImageBearingFactWithVerticesKeepsKeyDerivedName() {
        final Fact imageFact = new Fact("odd-1", "area", "/assets/x.png", null, null,
                squareVertices(), null, null, "Should Not Show");
        entityList.updateFacts(Collections.singletonList(imageFact));

        assertThat(entityList.getDisplayName("odd-1")).isEqualTo("odd-1");
    }

    /** An unknown id has no resolved name. */
    @Test
    void testGetDisplayNameUnknown() {
        assertThat(entityList.getDisplayName("nobody")).isNull();
        assertThat(entityList.getDisplayName(null)).isNull();
    }

    private static Fact fact(final String key, final String type) {
        return new Fact(key, type, null, null, null);
    }

    /** An area fact — vertices and no image — with the given LABEL name. */
    private static Fact area(final String key, final String label) {
        return new Fact(key, FloorMapJsonKeys.AREA, null, null, null,
                squareVertices(), null, null, label);
    }

    private static double[][] squareVertices() {
        return new double[][]{{-5, -5}, {5, -5}, {5, 5}, {-5, 5}};
    }

    // -----------------------------------------------------------------------
    // Deduplication and accumulation
    // -----------------------------------------------------------------------

    /**
     * The same id repeated within a single update produces one row, keeping
     * the first-seen type.
     */
    @Test
    void testDedupesWithinUpdate() {
        entityList.update(Arrays.asList(
                entity("forklift-1", "vehicle"),
                entity("forklift-1", "object")));

        final List<EntityEntry> entities = entityList.getEntities();
        assertThat(entities).hasSize(1);
        assertThat(entities.getFirst().getType()).isEqualTo("vehicle");
    }

    /**
     * An entity absent from a later refresh stays in the roster — the roster
     * is a union of everything seen, so playback refreshes don't drop rows.
     */
    @Test
    void testAccumulatesAcrossUpdates() {
        entityList.update(Collections.singletonList(person()));
        entityList.update(Collections.singletonList(entity("forklift-1", "vehicle")));

        assertThat(entityList.getEntities())
                .extracting(EntityEntry::getId)
                .containsExactly("alice@example.com", "forklift-1");
    }

    // -----------------------------------------------------------------------
    // Change flag
    // -----------------------------------------------------------------------

    /**
     * The change flag is true only when membership actually changes, so the
     * presenter can skip grid refreshes for repeat data.
     */
    @Test
    void testChangeFlag() {
        assertThat(entityList.update(Collections.singletonList(person())))
                .isTrue();
        // Same membership again — no change.
        assertThat(entityList.update(Collections.singletonList(person())))
                .isFalse();
        // Subset (alice absent) — roster unchanged because entries accumulate.
        assertThat(entityList.update(Collections.emptyList())).isFalse();
        assertThat(entityList.update(null)).isFalse();
        // New member — change.
        assertThat(entityList.update(Collections.singletonList(entity("forklift-1", "vehicle"))))
                .isTrue();
    }

    // -----------------------------------------------------------------------
    // Sorting
    // -----------------------------------------------------------------------

    /**
     * Entities are sorted by display name case-insensitively with the full id
     * as a tiebreak, and the order is stable across updates.
     */
    @Test
    void testSortOrder() {
        entityList.update(Arrays.asList(
                entity("dave@example.com", "person"),
                entity("Alice@other.org", "person"),
                entity("alice@example.com", "person"),
                entity("bob@example.com", "person")));

        final List<String> ids = entityList.getEntities().stream()
                .map(EntityEntry::getId)
                .collect(Collectors.toList());
        // "alice@example.com" and "Alice@other.org" share the display name
        // case-insensitively, so the id tiebreak orders the upper-case id first.
        assertThat(ids).containsExactly(
                "Alice@other.org",
                "alice@example.com",
                "bob@example.com",
                "dave@example.com");

        // Order is unchanged by a repeat update.
        entityList.update(Collections.singletonList(entity("bob@example.com", "person")));
        assertThat(entityList.getEntities().stream()
                .map(EntityEntry::getId)
                .collect(Collectors.toList()))
                .isEqualTo(ids);
    }

    // -----------------------------------------------------------------------
    // contains / clear
    // -----------------------------------------------------------------------

    @Test
    void testContains() {
        entityList.update(Collections.singletonList(entity("forklift-1", "vehicle")));

        assertThat(entityList.contains("forklift-1")).isTrue();
        assertThat(entityList.contains("forklift-2")).isFalse();
        assertThat(entityList.contains(null)).isFalse();
    }

    /**
     * Clearing empties the roster and the next update reports a change again.
     */
    @Test
    void testClear() {
        entityList.update(Collections.singletonList(person()));
        entityList.clear();

        assertThat(entityList.getEntities()).isEmpty();
        assertThat(entityList.update(Collections.singletonList(person())))
                .isTrue();
    }

    // -----------------------------------------------------------------------
    // EntityEntry equality
    // -----------------------------------------------------------------------

    /**
     * Entry equality is id-based so a re-created entry for the same entity
     * reads as already-selected to a selection model across grid data refreshes.
     */
    @Test
    void testEntityEntryEqualsOnIdOnly() {
        final EntityEntry a = new EntityEntry("forklift-1", "forklift-1", "vehicle", true);
        final EntityEntry b = new EntityEntry("forklift-1", "different label", "object", false);
        final EntityEntry c = new EntityEntry("forklift-2", "forklift-1", "vehicle", true);

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
    }
    // -----------------------------------------------------------------------
    // captionFor (canvas caption text)
    // -----------------------------------------------------------------------

    /**
     * A user-supplied {@code LABEL} wins, which is the whole point of the field.
     *
     * <p>Regression test for a real gap: the canvas caption path used to shorten the
     * key and ignore the label entirely, so an object the user had named "Loading
     * Bay" was captioned "gate-1" while the hover tooltip for the same object said
     * "Loading Bay".</p>
     */
    @Test
    void testCaptionFor_prefersTheFactLabel() {
        assertThat(FloorMapEntityList.captionFor("gate-1@100", "Loading Bay", null))
                .isEqualTo("Loading Bay");
    }

    /**
     * The label must beat the resolver, not the other way round.
     *
     * <p>This is the subtle one. The roster's resolver returns a key-derived name for
     * everything except areas, and that name is never blank — so a resolver-first
     * precedence would silently discard every user-supplied label. Consulting the
     * resolver first would make the method useless for exactly the facts it exists
     * to serve.</p>
     */
    @Test
    void testCaptionFor_labelBeatsAKeyDerivedResolver() {
        assertThat(FloorMapEntityList.captionFor(
                "gate-1@100", "Loading Bay", FloorMapEntityList::displayName))
                .isEqualTo("Loading Bay");
    }

    /**
     * With no label — a live event entity, which has no {@link Fact} behind it — the
     * resolver is the only source of a name.
     */
    @Test
    void testCaptionFor_fallsBackToTheResolver() {
        assertThat(FloorMapEntityList.captionFor("user-42@100", null, ignored -> "Alice"))
                .isEqualTo("Alice");
    }

    /** A blank label or a blank resolver result is not a name. */
    @Test
    void testCaptionFor_blankValuesAreSkipped() {
        assertThat(FloorMapEntityList.captionFor("user-42@100", "   ", ignored -> "Alice"))
                .isEqualTo("Alice");
        assertThat(FloorMapEntityList.captionFor("user-42@100", null, ignored -> "  "))
                .isEqualTo("user-42");
        assertThat(FloorMapEntityList.captionFor("user-42@100", null, ignored -> null))
                .isEqualTo("user-42");
    }

    /** With nothing else available the key is shortened at the {@code @}. */
    @Test
    void testCaptionFor_lastResortIsTheShortenedKey() {
        assertThat(FloorMapEntityList.captionFor("user-42@100", null, null))
                .isEqualTo("user-42");
        assertThat(FloorMapEntityList.captionFor("plain-id", null, null))
                .isEqualTo("plain-id");
    }

    /** Never returns null, so a caller can hand the result straight to the renderer. */
    @Test
    void testCaptionFor_nullIdYieldsEmptyNotNull() {
        assertThat(FloorMapEntityList.captionFor(null, null, null)).isEmpty();
    }

    /** Values are trimmed, so stray whitespace cannot shift a caption's placement. */
    @Test
    void testCaptionFor_trimsWhitespace() {
        assertThat(FloorMapEntityList.captionFor("k", "  Loading Bay  ", null))
                .isEqualTo("Loading Bay");
        assertThat(FloorMapEntityList.captionFor("k", null, ignored -> "  Alice  "))
                .isEqualTo("Alice");
    }

}
