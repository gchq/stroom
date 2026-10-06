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

import stroom.floormap.shared.FloorMapFieldMapping.Role;
import stroom.util.shared.TemporalEntry;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TestFloorMapEntryParser {

    private static final String MAP = "testMap";
    private static final List<FloorMapFieldMapping> SCHEMA =
            FloorMapFieldMapping.initialValueSchema();
    private static final MapValueAccessor ACCESSOR = MapValueAccessor.INSTANCE;

    /** Collects warnings emitted during parsing. Reset before each test. */
    private final List<String> warnings = new ArrayList<>();

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        warnings.clear();
    }

    // -----------------------------------------------------------------------
    // findPath
    // -----------------------------------------------------------------------

    /**
     * The path mapped to the {@code TYPE} role in the schema is returned.
     */
    @Test
    void testFindPath_type() {
        assertThat(FloorMapEntryParser.findPath(SCHEMA, Role.TYPE))
                .isEqualTo(".type");
    }

    /**
     * The path mapped to the {@code POSITION} role in the schema is returned.
     */
    @Test
    void testFindPath_position() {
        assertThat(FloorMapEntryParser.findPath(SCHEMA, Role.POSITION))
                .isEqualTo(".coords");
    }

    /**
     * The path mapped to the {@code IMAGE} role in the schema is returned.
     */
    @Test
    void testFindPath_image() {
        assertThat(FloorMapEntryParser.findPath(SCHEMA, Role.IMAGE))
                .isEqualTo(".img");
    }

    /**
     * The path mapped to the {@code WORLD_TO_MAP} role in the schema is
     * returned.
     */
    @Test
    void testFindPath_worldToMap() {
        assertThat(FloorMapEntryParser.findPath(SCHEMA, Role.WORLD_TO_MAP))
                .isEqualTo(".tm-world-to-map");
    }

    /**
     * A {@code null} schema yields {@code null} rather than throwing.
     */
    @Test
    void testFindPath_nullSchema() {
        assertThat(FloorMapEntryParser.findPath(null, Role.TYPE)).isNull();
    }

    /**
     * When the schema has no mapping for the requested role, {@code null} is
     * returned instead of matching an unrelated entry.
     */
    @Test
    void testFindPath_missingRole() {
        final List<FloorMapFieldMapping> partial = List.of(
                new FloorMapFieldMapping(".type", Role.TYPE, "Type", null));
        assertThat(FloorMapEntryParser.findPath(partial, Role.POSITION)).isNull();
    }

    // -----------------------------------------------------------------------
    // parse — null/empty inputs
    // -----------------------------------------------------------------------

    /**
     * A {@code null} entry list produces an empty fact list with no warnings.
     */
    @Test
    void testParse_nullEntries() {
        final List<Fact> facts =
                FloorMapEntryParser.parse(null, SCHEMA, ACCESSOR, warnings::add);
        assertThat(facts).isEmpty();
        assertThat(warnings).isEmpty();
    }

    /**
     * An empty entry list produces an empty fact list with no warnings.
     */
    @Test
    void testParse_emptyEntries() {
        final List<Fact> facts =
                FloorMapEntryParser.parse(List.of(), SCHEMA, ACCESSOR, warnings::add);
        assertThat(facts).isEmpty();
        assertThat(warnings).isEmpty();
    }

    // -----------------------------------------------------------------------
    // parse — background (image) entry
    // -----------------------------------------------------------------------

    /**
     * A background entry (declared via {@code type} and carrying an image)
     * yields a single fact with that image and its world-to-map placement
     * matrix. A background is not special-cased: it is just an image fact placed
     * by {@code WORLD_TO_MAP}.
     */
    @Test
    void testParse_backgroundEntry() {
        final String json = "{\"type\":\"background\",\"img\":\"floor1.png\","
                + "\"tm-world-to-map\":[2,0,0,2,10,20]}";
        final TemporalEntry bgEntry = entry("background", 100, json);

        final List<Fact> facts =
                FloorMapEntryParser.parse(List.of(bgEntry), SCHEMA, ACCESSOR, warnings::add);

        assertThat(facts).hasSize(1);
        final Fact bg = facts.getFirst();
        assertThat(bg.hasImage()).isTrue();
        assertThat(bg.getImage()).isEqualTo("floor1.png");
        assertThat(bg.getType()).isEqualToIgnoringCase("background");
        assertThat(bg.getWorldToMap()).isNotNull();
        assertThat(bg.getWorldToMap().getA()).isEqualTo(2.0);
        assertThat(bg.getWorldToMap().getE()).isEqualTo(10.0);
        assertThat(bg.getWorldToMap().getF()).isEqualTo(20.0);
        assertThat(warnings).as("valid background should not emit warnings").isEmpty();
    }

    /**
     * An image-bearing entry with no {@code type} still becomes an image fact.
     * (Under the new model there is no key- or type-based "background"
     * detection: any fact carrying an image is an image fact.)
     */
    @Test
    void testParse_imageEntry_noType() {
        final String json = "{\"img\":\"floor1.png\"}";
        final TemporalEntry bgEntry = entry("background", 100, json);

        final List<Fact> facts =
                FloorMapEntryParser.parse(List.of(bgEntry), SCHEMA, ACCESSOR, warnings::add);
        assertThat(facts).hasSize(1);
        final Fact fact = facts.getFirst();
        assertThat(fact.hasImage()).isTrue();
        assertThat(fact.getImage()).isEqualTo("floor1.png");
        assertThat(warnings).isEmpty();
    }

    // -----------------------------------------------------------------------
    // parse — regular object entries
    // -----------------------------------------------------------------------

    /**
     * A regular object entry with no world-to-map matrix is parsed with its
     * raw (world) coordinates unchanged and an identity placement matrix.
     */
    @Test
    void testParse_regularObject_identityMatrix() {
        final String json = "{\"type\":\"gate\",\"name\":\"Gate-1\","
                + "\"coords\":[100,200]}";
        final TemporalEntry objEntry = entry("gate-1", 100, json);

        final List<Fact> facts =
                FloorMapEntryParser.parse(List.of(objEntry), SCHEMA, ACCESSOR, warnings::add);

        assertThat(facts).hasSize(1);
        final Fact fact = facts.getFirst();
        assertThat(fact.getKey()).isEqualTo("gate-1");
        assertThat(fact.getType()).isEqualTo("gate");
        assertThat(fact.getPosition()).isNotNull();
        assertThat(fact.getPosition()[0]).isCloseTo(100.0, within(0.001));
        assertThat(fact.getPosition()[1]).isCloseTo(200.0, within(0.001));
        assertThat(fact.getWorldToMap()).isEqualTo(FloorMapTransformationMatrix.identity());
        assertThat(warnings).as("valid entry should not emit warnings").isEmpty();
    }

    /**
     * A regular object entry carries its world position and its world-to-map
     * matrix. Composing the two (as the canvas does) maps the world point into
     * map space (scale and translation applied).
     */
    @Test
    void testParse_regularObject_withWorldToMapTransform() {
        // World-to-map: scale 2x, translate (50, 100)
        // World coords (10, 20) → map coords (2*10 + 50, 2*20 + 100) = (70, 140)
        final String json = "{\"type\":\"camera\",\"name\":\"Cam-1\","
                + "\"coords\":[10,20],"
                + "\"tm-world-to-map\":[2,0,0,2,50,100]}";
        final TemporalEntry objEntry = entry("cam-1", 100, json);

        final List<Fact> facts =
                FloorMapEntryParser.parse(List.of(objEntry), SCHEMA, ACCESSOR, null);

        assertThat(facts).hasSize(1);
        final Fact fact = facts.getFirst();
        // Position is stored in world space.
        assertThat(fact.getPosition()).isNotNull();
        assertThat(fact.getPosition()[0]).isCloseTo(10.0, within(0.001));
        assertThat(fact.getPosition()[1]).isCloseTo(20.0, within(0.001));
        // Composing world→map places the point at (70, 140) in map space.
        final FloorMapTransformationMatrix m = fact.getWorldToMap();
        final double[] p = fact.getPosition();
        final double mapX = m.getA() * p[0] + m.getC() * p[1] + m.getE();
        final double mapY = m.getB() * p[0] + m.getD() * p[1] + m.getF();
        assertThat(mapX).isCloseTo(70.0, within(0.001));
        assertThat(mapY).isCloseTo(140.0, within(0.001));
    }

    /**
     * An object entry with no {@code coords} field has a {@code null} position
     * (rather than defaulting to a point), and still parses without warning.
     */
    @Test
    void testParse_missingCoords_nullPosition() {
        final String json = "{\"type\":\"sensor\",\"name\":\"S1\"}";
        final TemporalEntry objEntry = entry("s1", 100, json);

        final List<Fact> facts =
                FloorMapEntryParser.parse(List.of(objEntry), SCHEMA, ACCESSOR, null);

        assertThat(facts).hasSize(1);
        assertThat(facts.getFirst().getPosition()).isNull();
    }

    /**
     * An object entry with no world-to-map matrix defaults to the identity
     * placement matrix, so its world position passes through unchanged.
     */
    @Test
    void testParse_missingMatrix_usesIdentity() {
        // Without a world-to-map matrix, the placement matrix is identity.
        final String json = "{\"type\":\"gate\",\"coords\":[50,75]}";
        final TemporalEntry objEntry = entry("g1", 100, json);

        final List<Fact> facts =
                FloorMapEntryParser.parse(List.of(objEntry), SCHEMA, ACCESSOR, null);

        final Fact fact = facts.getFirst();
        assertThat(fact.getWorldToMap()).isEqualTo(FloorMapTransformationMatrix.identity());
        assertThat(fact.getPosition()).isNotNull();
        assertThat(fact.getPosition()[0]).isCloseTo(50.0, within(0.001));
        assertThat(fact.getPosition()[1]).isCloseTo(75.0, within(0.001));
    }

    // -----------------------------------------------------------------------
    // parse — mixed entries
    // -----------------------------------------------------------------------

    /**
     * A mixed batch of one background (image) entry and several regular objects
     * produces one fact per entry: the image fact carries the image, the others
     * do not.
     */
    @Test
    void testParse_backgroundAndMultipleObjects() {
        final String bgJson = "{\"type\":\"background\",\"img\":\"floor.png\"}";
        final String obj1Json = "{\"type\":\"gate\",\"coords\":[10,20]}";
        final String obj2Json = "{\"type\":\"camera\",\"coords\":[30,40]}";

        final List<Fact> facts = FloorMapEntryParser.parse(
                List.of(entry("background", 100, bgJson),
                        entry("gate-1", 100, obj1Json),
                        entry("cam-1", 100, obj2Json)),
                SCHEMA, ACCESSOR, null);

        assertThat(facts).hasSize(3);
        assertThat(facts.stream().filter(Fact::hasImage).count()).isEqualTo(1L);
        assertThat(facts.stream().filter(Fact::hasImage).findFirst().orElseThrow().getImage())
                .isEqualTo("floor.png");
        assertThat(facts.stream().filter(f -> !f.hasImage()).count()).isEqualTo(2L);
    }

    // -----------------------------------------------------------------------
    // parse — error handling
    // -----------------------------------------------------------------------

    /**
     * An entry whose value is not valid JSON is skipped (excluded from the
     * result) while other, well-formed entries still parse, and a warning is
     * emitted for the bad one.
     */
    @Test
    void testParse_malformedEntry_skippedWithWarning() {
        final TemporalEntry badEntry = entry("bad", 100, "not-json");
        final TemporalEntry goodEntry = entry("good", 100,
                "{\"type\":\"gate\",\"coords\":[1,2]}");

        final List<Fact> facts = FloorMapEntryParser.parse(
                List.of(badEntry, goodEntry), SCHEMA, ACCESSOR, warnings::add);

        // Only the good entry should be parsed
        assertThat(facts).hasSize(1);
        assertThat(facts.getFirst().getKey()).isEqualTo("good");
        // A warning should have been emitted for the bad entry
        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst()).contains("bad");
    }

    /**
     * The warning emitted for a malformed entry includes that entry's key,
     * so the user can identify which fact failed to parse.
     */
    @Test
    void testParse_malformedEntry_warningContainsKey() {
        final TemporalEntry badEntry = entry("sensor-42", 100, "totally invalid");

        FloorMapEntryParser.parse(
                List.of(badEntry), SCHEMA, ACCESSOR, warnings::add);

        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst())
                .as("warning message should include the entry key")
                .contains("sensor-42");
    }

    /**
     * Multiple malformed entries in the same batch each produce their own
     * warning, and do not prevent the well-formed entry from parsing.
     */
    @Test
    void testParse_multipleMalformedEntries_emitsMultipleWarnings() {
        final List<Fact> facts = FloorMapEntryParser.parse(
                List.of(entry("bad1", 100, "xxx"),
                        entry("bad2", 100, "yyy"),
                        entry("good", 100, "{\"type\":\"gate\",\"coords\":[1,2]}")),
                SCHEMA, ACCESSOR, warnings::add);

        assertThat(facts).hasSize(1);
        assertThat(warnings).hasSize(2);
        assertThat(warnings.getFirst()).contains("bad1");
        assertThat(warnings.get(1)).contains("bad2");
    }

    /**
     * A stored world-to-map matrix that is present, entirely numeric, and
     * degenerate is corrupt data: it collapses the fact to a single point and no
     * coordinate derived from it can be inverted. The entry must be skipped and
     * reported, not accepted.
     *
     * <p>This is the case that used to be accepted silently. The fact rendered at
     * the origin with no warning, and because the canvas then converted vertex
     * drags through the (silently substituted) identity inverse, editing it wrote
     * coordinates in the wrong space back into the document.</p>
     */
    @Test
    void testParse_degenerateWorldToMapMatrix_skipsEntryAndWarns() {
        final List<Fact> facts = FloorMapEntryParser.parse(
                List.of(entry("bad-matrix", 100,
                                "{\"type\":\"gate\",\"coords\":[1,2],"
                                        + "\"tm-world-to-map\":[0,0,0,0,0,0]}"),
                        entry("good", 100, "{\"type\":\"gate\",\"coords\":[3,4]}")),
                SCHEMA, ACCESSOR, warnings::add);

        assertThat(facts)
                .as("the degenerate entry must not become a fact")
                .hasSize(1);
        assertThat(facts.getFirst().getKey()).isEqualTo("good");
        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst())
                .as("the warning must name the offending entry and say why")
                .contains("bad-matrix")
                .contains("not invertible");
    }

    /**
     * Linearly dependent rows are degenerate too, even though none of the six
     * values is zero — the check must be on the determinant, not on the presence
     * of zeroes.
     */
    @Test
    void testParse_linearlyDependentWorldToMapMatrix_skipsEntryAndWarns() {
        final List<Fact> facts = FloorMapEntryParser.parse(
                List.of(entry("dependent-rows", 100,
                        "{\"type\":\"gate\",\"coords\":[1,2],"
                                + "\"tm-world-to-map\":[2,4,1,2,5,6]}")),
                SCHEMA, ACCESSOR, warnings::add);

        assertThat(facts).isEmpty();
        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst()).contains("dependent-rows");
    }

    /**
     * A matrix containing a non-numeric token is present but unreadable, which is
     * corrupt data: the entry is skipped and reported.
     *
     * <p>This is the case the author asked to be surfaced rather than absorbed. The
     * bad token is not silently replaced with a zero (which used to manufacture an
     * all-zero matrix that looked structurally valid and then behaved as a
     * degenerate transform), and nor is it quietly treated as "no matrix supplied"
     * — the user is told which row is wrong.</p>
     */
    @Test
    void testParse_nonNumericTokenInMatrix_skipsEntryAndWarns() {
        final List<Fact> facts = FloorMapEntryParser.parse(
                List.of(entry("bad-token", 100,
                        "{\"type\":\"gate\",\"coords\":[1,2],"
                                + "\"tm-world-to-map\":[2,0,\"x\",2,10,20]}")),
                SCHEMA, ACCESSOR, warnings::add);

        assertThat(facts).isEmpty();
        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst())
                .contains("bad-token")
                .contains("not a readable numeric array");
    }

    /**
     * A matrix with too few values is present but unusable, and is reported the
     * same way.
     */
    @Test
    void testParse_shortMatrix_skipsEntryAndWarns() {
        final List<Fact> facts = FloorMapEntryParser.parse(
                List.of(entry("short-matrix", 100,
                        "{\"type\":\"gate\",\"coords\":[1,2],"
                                + "\"tm-world-to-map\":[1,0,0,1]}")),
                SCHEMA, ACCESSOR, warnings::add);

        assertThat(facts).isEmpty();
        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst())
                .contains("short-matrix")
                .contains("needs 6 values but has 4");
    }

    /**
     * A row that simply carries no matrix is <strong>not</strong> an error. Data
     * arriving from a stream may legitimately omit fields, so an absent matrix maps
     * to the identity, silently — the distinction the parser draws is present
     * versus absent, not null versus non-null.
     */
    @Test
    void testParse_absentMatrix_isIdentityWithNoWarning() {
        final List<Fact> facts = FloorMapEntryParser.parse(
                List.of(entry("no-matrix", 100, "{\"type\":\"gate\",\"coords\":[1,2]}")),
                SCHEMA, ACCESSOR, warnings::add);

        assertThat(facts).hasSize(1);
        assertThat(facts.getFirst().getWorldToMap())
                .isEqualTo(FloorMapTransformationMatrix.identity());
        assertThat(warnings)
                .as("missing data is normal and must stay silent")
                .isEmpty();
    }

    /**
     * An explicit JSON {@code null} is the format's way of saying "no value", so it
     * counts as absent rather than as malformed.
     */
    @Test
    void testParse_explicitNullMatrix_isTreatedAsAbsent() {
        final List<Fact> facts = FloorMapEntryParser.parse(
                List.of(entry("null-matrix", 100,
                        "{\"type\":\"gate\",\"coords\":[1,2],"
                                + "\"tm-world-to-map\":null}")),
                SCHEMA, ACCESSOR, warnings::add);

        assertThat(facts).hasSize(1);
        assertThat(facts.getFirst().getWorldToMap())
                .isEqualTo(FloorMapTransformationMatrix.identity());
        assertThat(warnings).isEmpty();
    }

    /**
     * {@code hasValue} separates the two states the typed getters conflate.
     */
    @Test
    void testHasValue_distinguishesAbsentFromMalformed() {
        final ParsedValue value = ACCESSOR.parse(
                "{\"present-but-bad\":[1,\"nope\"],\"explicit-null\":null,\"good\":[1,2]}");

        assertThat(ACCESSOR.hasValue(value, ".present-but-bad"))
                .as("something is there, it just cannot be read")
                .isTrue();
        assertThat(ACCESSOR.getArray(value, ".present-but-bad")).isNull();

        assertThat(ACCESSOR.hasValue(value, ".explicit-null"))
                .as("an explicit null means no value")
                .isFalse();
        assertThat(ACCESSOR.hasValue(value, ".missing-entirely")).isFalse();
        assertThat(ACCESSOR.hasValue(value, ".good")).isTrue();
    }

    /**
     * The {@link ValueAccessor#getArray} contract is all-or-nothing: one
     * unparseable element makes the whole array {@code null}. Pinned here because
     * the rest of the parser relies on it — a caller cannot tell a fabricated zero
     * from a real one.
     */
    @Test
    void testGetArray_malformedElement_yieldsNullNotAZero() {
        final ParsedValue value = ACCESSOR.parse(
                "{\"coords\":[1,\"nope\"],\"good\":[1,2]}");

        assertThat(ACCESSOR.getArray(value, ".coords"))
                .as("a malformed element makes the whole array malformed")
                .isNull();
        assertThat(ACCESSOR.getArray(value, ".good"))
                .as("a well-formed array is still returned")
                .containsExactly(1d, 2d);
    }

    /**
     * Passing a {@code null} warning consumer silently skips warning
     * emission rather than throwing a {@code NullPointerException}.
     */
    @Test
    void testParse_nullWarningConsumer_doesNotThrow() {
        // Passing null consumer should silently skip, not NPE
        final TemporalEntry badEntry = entry("bad", 100, "not-json");
        final List<Fact> facts = FloorMapEntryParser.parse(
                List.of(badEntry), SCHEMA, ACCESSOR, null);
        assertThat(facts).isEmpty();
    }

    /**
     * An entry with a {@code null} value is skipped and produces a warning
     * that identifies the key and the fact that the value was null.
     */
    @Test
    void testParse_nullValue_emitsWarning() {
        final TemporalEntry nullValue = new TemporalEntry(MAP, "k1", 100L, null);
        final List<Fact> facts =
                FloorMapEntryParser.parse(List.of(nullValue), SCHEMA, ACCESSOR, warnings::add);
        assertThat(facts).isEmpty();
        assertThat(warnings).as("null value should emit a warning").hasSize(1);
        assertThat(warnings.getFirst()).contains("k1").contains("null");
    }

    /**
     * An entry with an empty-string value is skipped and produces a warning
     * identifying the key.
     */
    @Test
    void testParse_emptyValue_emitsWarning() {
        final TemporalEntry emptyValue = entry("k1", 100, "");
        final List<Fact> facts =
                FloorMapEntryParser.parse(List.of(emptyValue), SCHEMA, ACCESSOR, warnings::add);
        assertThat(facts).isEmpty();
        assertThat(warnings).as("empty value should emit a warning").hasSize(1);
        assertThat(warnings.getFirst()).contains("k1");
    }

    /**
     * An entry missing the {@code type} field still parses successfully,
     * defaulting to an empty type string without emitting a warning.
     */
    @Test
    void testParse_nullType_usesEmptyString_noWarning() {
        final String json = "{\"coords\":[5,10]}";
        final TemporalEntry objEntry = entry("k1", 100, json);

        final List<Fact> facts =
                FloorMapEntryParser.parse(List.of(objEntry), SCHEMA, ACCESSOR, warnings::add);

        assertThat(facts).hasSize(1);
        assertThat(facts.getFirst().getType()).isEmpty();
        assertThat(warnings).as("missing type is not a warning-worthy condition").isEmpty();
    }

    // -----------------------------------------------------------------------
    // parse — the authoritative fact list
    // -----------------------------------------------------------------------

    /**
     * Every entry becomes one {@link Fact}, in order — including the background.
     */
    @Test
    void testFacts_oneFactPerEntry() {
        final List<Fact> facts = FloorMapEntryParser.parse(List.of(
                        entry("background", 100,
                                "{\"type\":\"background\",\"img\":\"floor.png\","
                                        + "\"tm-world-to-map\":[2,0,0,2,10,20]}"),
                        entry("gate-1", 100,
                                "{\"type\":\"gate\",\"coords\":[3,4]}")),
                SCHEMA, ACCESSOR, warnings::add);

        assertThat(facts).hasSize(2);
        assertThat(facts.getFirst().getKey()).isEqualTo("background");
        assertThat(facts.get(1).getKey()).isEqualTo("gate-1");
    }

    /**
     * A background fact carries its image and its world-to-map placement
     * matrix, is typed {@code "background"}, and has no position.
     */
    @Test
    void testFacts_backgroundFact() {
        final List<Fact> facts = FloorMapEntryParser.parse(List.of(
                        entry("background", 100,
                                "{\"type\":\"background\",\"img\":\"floor.png\","
                                        + "\"tm-world-to-map\":[2,0,0,2,10,20]}")),
                SCHEMA, ACCESSOR, warnings::add);

        final Fact bg = facts.getFirst();
        assertThat(bg.getType()).isEqualToIgnoringCase("background");
        assertThat(bg.hasImage()).isTrue();
        assertThat(bg.getImage()).isEqualTo("floor.png");
        assertThat(bg.getPosition()).isNull();
        assertThat(bg.getWorldToMap().getA()).isEqualTo(2.0);
        assertThat(bg.getWorldToMap().getE()).isEqualTo(10.0);
        assertThat(bg.getWorldToMap().getF()).isEqualTo(20.0);
    }

    /**
     * A regular fact carries its world position and world-to-map matrix, has no
     * image, and is not a background.
     */
    @Test
    void testFacts_regularFact() {
        final List<Fact> facts = FloorMapEntryParser.parse(List.of(
                        entry("gate-1", 100,
                                "{\"type\":\"gate\",\"coords\":[3,4],"
                                        + "\"tm-world-to-map\":[1,0,0,1,0,0]}")),
                SCHEMA, ACCESSOR, warnings::add);

        final Fact fact = facts.getFirst();
        assertThat(fact.hasImage()).isFalse();
        assertThat(fact.getType()).isEqualTo("gate");
        assertThat(fact.getPosition()).containsExactly(3.0, 4.0);
    }

    /**
     * A mixed batch of one background (image) entry and one object entry yields
     * one image fact and one non-image fact, in entry order.
     */
    @Test
    void testFacts_mixedBackgroundAndObject() {
        final List<Fact> facts = FloorMapEntryParser.parse(List.of(
                        entry("background", 100,
                                "{\"type\":\"background\",\"img\":\"floor.png\","
                                        + "\"tm-world-to-map\":[1,0,0,1,0,0]}"),
                        entry("gate-1", 100, "{\"type\":\"gate\",\"coords\":[3,4]}")),
                SCHEMA, ACCESSOR, warnings::add);

        assertThat(facts).hasSize(2);
        // The background fact carries the image.
        assertThat(facts.getFirst().getKey()).isEqualTo("background");
        assertThat(facts.getFirst().hasImage()).isTrue();
        assertThat(facts.getFirst().getImage()).isEqualTo("floor.png");
        // The object fact does not.
        assertThat(facts.get(1).getKey()).isEqualTo("gate-1");
        assertThat(facts.get(1).hasImage()).isFalse();
    }

    /**
     * A {@code null} entry list yields an empty (non-null) fact list.
     */
    @Test
    void testFacts_nullEntries_emptyList() {
        final List<Fact> facts =
                FloorMapEntryParser.parse(null, SCHEMA, ACCESSOR, warnings::add);
        assertThat(facts).isEmpty();
    }

    // -----------------------------------------------------------------------
    // parse — areas (GEOMETRY / FILL / OPACITY)
    // -----------------------------------------------------------------------

    /**
     * An area entry parses to a fact with the flat geometry array folded into
     * vertex pairs, plus its fill and opacity.
     */
    @Test
    void testParse_areaEntry() {
        final String json = "{\"type\":\"area\",\"name\":\"Loading Bay\","
                + "\"geometry\":[0,0,100,0,100,50,0,50],"
                + "\"fill\":\"#ff0000\",\"opacity\":0.5,"
                + "\"tm-world-to-map\":[1,0,0,1,20,30]}";
        final List<Fact> facts = FloorMapEntryParser.parse(
                List.of(entry("area-1", 0L, json)), SCHEMA, ACCESSOR, warnings::add);

        assertThat(facts).hasSize(1);
        final Fact fact = facts.getFirst();
        assertThat(fact.hasVertices()).isTrue();
        assertThat(fact.getVertices()).hasDimensions(4, 2);
        assertThat(fact.getVertices()[0]).containsExactly(0, 0);
        assertThat(fact.getVertices()[2]).containsExactly(100, 50);
        assertThat(fact.getFill()).isEqualTo("#ff0000");
        assertThat(fact.getOpacity()).isCloseTo(0.5, within(1e-9));
        assertThat(fact.getWorldToMap().getE()).isCloseTo(20, within(1e-9));
        assertThat(warnings).isEmpty();
    }

    /**
     * A trailing odd value in the flat geometry array is ignored rather than
     * corrupting the vertex pairs.
     */
    @Test
    void testParse_areaEntry_oddGeometryLength() {
        final String json = "{\"type\":\"area\",\"geometry\":[0,0,10,0,10,10,99]}";
        final List<Fact> facts = FloorMapEntryParser.parse(
                List.of(entry("area-1", 0L, json)), SCHEMA, ACCESSOR, warnings::add);

        assertThat(facts.getFirst().getVertices()).hasDimensions(3, 2);
    }

    /**
     * A geometry with fewer than three vertices is not a renderable area —
     * the fact still parses but reports no vertices.
     */
    @Test
    void testParse_areaEntry_tooFewVertices() {
        final String json = "{\"type\":\"area\",\"geometry\":[0,0,10,10]}";
        final List<Fact> facts = FloorMapEntryParser.parse(
                List.of(entry("area-1", 0L, json)), SCHEMA, ACCESSOR, warnings::add);

        assertThat(facts).hasSize(1);
        assertThat(facts.getFirst().hasVertices()).isFalse();
        assertThat(facts.getFirst().getVertices()).isNull();
    }

    /**
     * Non-area entries parse with no vertices/fill/opacity — and a schema
     * without the area roles (a pre-area document) still parses entries that
     * carry geometry JSON, simply ignoring it.
     */
    @Test
    void testParse_areaFieldsAbsent_andLegacySchema() {
        final String plainJson = "{\"type\":\"gate\",\"coords\":[1,2]}";
        final List<Fact> plain = FloorMapEntryParser.parse(
                List.of(entry("gate-1", 0L, plainJson)), SCHEMA, ACCESSOR, warnings::add);
        assertThat(plain.getFirst().hasVertices()).isFalse();
        assertThat(plain.getFirst().getFill()).isNull();
        assertThat(plain.getFirst().getOpacity()).isNull();

        // Legacy schema: no GEOMETRY/FILL/OPACITY mappings at all.
        final List<FloorMapFieldMapping> legacySchema = List.of(
                new FloorMapFieldMapping(".type", Role.TYPE, "Type", null),
                new FloorMapFieldMapping(".coords", Role.POSITION, "Coords", null));
        final String areaJson = "{\"type\":\"area\",\"geometry\":[0,0,1,0,1,1]}";
        final List<Fact> legacy = FloorMapEntryParser.parse(
                List.of(entry("area-1", 0L, areaJson)), legacySchema, ACCESSOR, warnings::add);
        assertThat(legacy).hasSize(1);
        assertThat(legacy.getFirst().hasVertices()).isFalse();
        assertThat(warnings).isEmpty();
    }

    /**
     * An area value written through the accessor (as the editor writes it)
     * serialises and parses back to the same fact — the full round-trip the
     * "Draw Area Here" flow depends on, including the scalar opacity written
     * via {@code setNumber}.
     */
    @Test
    void testParse_areaRoundTrip() {
        final ParsedValue value = ACCESSOR.createEmpty("entry");
        ACCESSOR.setString(value, ".type", "area");
        ACCESSOR.setString(value, ".name", "zone-1");
        ACCESSOR.setArray(value, ".coords", new double[]{0, 0});
        ACCESSOR.setArray(value, ".tm-world-to-map", new double[]{1, 0, 0, 1, 5, 6});
        ACCESSOR.setArray(value, ".geometry", new double[]{-10, -10, 10, -10, 0, 15});
        ACCESSOR.setString(value, ".fill", "#1e88e5");
        ACCESSOR.setNumber(value, ".opacity", 0.25);
        final String serialised = ACCESSOR.serialise(value);

        final List<Fact> facts = FloorMapEntryParser.parse(
                List.of(entry("zone-1", 0L, serialised)), SCHEMA, ACCESSOR, warnings::add);

        assertThat(facts).hasSize(1);
        final Fact fact = facts.getFirst();
        assertThat(fact.hasVertices()).isTrue();
        assertThat(fact.getVertices()).hasDimensions(3, 2);
        assertThat(fact.getVertices()[2]).containsExactly(0, 15);
        assertThat(fact.getFill()).isEqualTo("#1e88e5");
        assertThat(fact.getOpacity()).isCloseTo(0.25, within(1e-9));
        assertThat(warnings).isEmpty();
    }

    /**
     * {@code getNumber} tolerates a numeric string and returns {@code null}
     * for junk; {@code setNumber(null)} removes the field.
     */
    @Test
    void testAccessor_numberLeniency() {
        final ParsedValue value = ACCESSOR.parse("{\"opacity\":\"0.75\",\"bad\":\"x\"}");
        assertThat(ACCESSOR.getNumber(value, ".opacity")).isCloseTo(0.75, within(1e-9));
        assertThat(ACCESSOR.getNumber(value, ".bad")).isNull();
        assertThat(ACCESSOR.getNumber(value, ".missing")).isNull();

        ACCESSOR.setNumber(value, ".opacity", null);
        assertThat(ACCESSOR.getNumber(value, ".opacity")).isNull();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * End-to-end: a value carrying the {@code LABEL} role reaches the caption.
     *
     * <p>The gap this closes spanned four layers, each individually correct and
     * individually tested: the default schema maps {@code .name} to
     * {@link Role#LABEL}, the parser reads it onto the {@link Fact}, and the canvas
     * then captioned the glyph with the fact's <em>key</em>. Nothing was broken
     * anywhere — the call between the last two layers was simply missing, which is
     * precisely what a per-layer suite cannot catch. So this test deliberately runs
     * the whole way from a stored row to the caption text.</p>
     */
    @Test
    void testParse_labelRoleReachesTheCanvasCaption() {
        final List<Fact> facts = FloorMapEntryParser.parse(
                List.of(entry("gate-1@100", 100,
                        "{\"type\":\"gate\",\"name\":\"Loading Bay\",\"coords\":[1,2]}")),
                SCHEMA, ACCESSOR, warnings::add);

        assertThat(facts).hasSize(1);
        final Fact fact = facts.getFirst();
        assertThat(fact.getLabelOrNull())
                .as("the parser must carry the LABEL onto the fact")
                .isEqualTo("Loading Bay");
        assertThat(FloorMapEntityList.captionFor(
                fact.getKey(), fact.getLabelOrNull(), FloorMapEntityList::displayName))
                .as("and the canvas must caption with it, not with the key")
                .isEqualTo("Loading Bay");
    }


    private static TemporalEntry entry(final String key,
                                       @SuppressWarnings("SameParameterValue") final long time,
                                       final String value) {
        return new TemporalEntry(MAP, key, time, value);
    }
}
