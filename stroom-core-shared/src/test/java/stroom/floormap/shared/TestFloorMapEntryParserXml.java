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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests {@link FloorMapEntryParser} against XML-formatted temporal entry
 * values, using {@link DomValueAccessor} as a GWT-free stand-in for the real
 * {@code stroom.floormap.client.XmlValueAccessor}.
 *
 * <p>Mirrors {@link TestFloorMapEntryParser} (which exercises the JSON path
 * via {@link MapValueAccessor}), but with an XPath-style value schema and
 * XML entry payloads, to confirm the parser's format-independence actually
 * holds for the XML {@link ValueFormat}.</p>
 */
class TestFloorMapEntryParserXml {

    private static final String MAP = "testMap";

    /** XPath-style schema, mirroring {@link FloorMapFieldMapping#initialValueSchema()}. */
    private static final List<FloorMapFieldMapping> SCHEMA = List.of(
            new FloorMapFieldMapping("/entry/type", Role.TYPE, "Type", null),
            new FloorMapFieldMapping("/entry/name", Role.LABEL, "Name", null),
            new FloorMapFieldMapping("/entry/coords", Role.POSITION, "Coords", null),
            new FloorMapFieldMapping("/entry/img", Role.IMAGE, "Image", null),
            new FloorMapFieldMapping("/entry/tm-world-to-map", Role.WORLD_TO_MAP, null, null)
    );
    private static final DomValueAccessor ACCESSOR = DomValueAccessor.INSTANCE;

    private final List<String> warnings = new ArrayList<>();

    @BeforeEach
    void setUp() {
        warnings.clear();
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
     * A background entry (declared via {@code <type>} and carrying an image)
     * yields a single fact with that image and its world-to-map placement
     * matrix. A background is not special-cased: it is just an image fact placed
     * by {@code WORLD_TO_MAP}.
     */
    @Test
    void testParse_backgroundEntry() {
        final String xml = "<entry><type>background</type><img>floor1.png</img>"
                + "<tm-world-to-map>2,0,0,2,10,20</tm-world-to-map></entry>";
        final TemporalEntry bgEntry = entry("background", 100, xml);

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
     * An image-bearing entry with no {@code <type>} still becomes an image
     * fact. (Under the new model there is no key- or type-based "background"
     * detection: any fact carrying an image is an image fact.)
     */
    @Test
    void testParse_imageEntry_noType() {
        final String xml = "<entry><img>floor1.png</img></entry>";
        final TemporalEntry bgEntry = entry("background", 100, xml);

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
        final String xml = "<entry><type>gate</type><name>Gate-1</name>"
                + "<coords>100,200</coords></entry>";
        final TemporalEntry objEntry = entry("gate-1", 100, xml);

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
        final String xml = "<entry><type>camera</type><name>Cam-1</name>"
                + "<coords>10,20</coords>"
                + "<tm-world-to-map>2,0,0,2,50,100</tm-world-to-map></entry>";
        final TemporalEntry objEntry = entry("cam-1", 100, xml);

        final List<Fact> facts =
                FloorMapEntryParser.parse(List.of(objEntry), SCHEMA, ACCESSOR, null);

        assertThat(facts).hasSize(1);
        final Fact fact = facts.getFirst();
        assertThat(fact.getPosition()).isNotNull();
        assertThat(fact.getPosition()[0]).isCloseTo(10.0, within(0.001));
        assertThat(fact.getPosition()[1]).isCloseTo(20.0, within(0.001));
        final FloorMapTransformationMatrix m = fact.getWorldToMap();
        final double[] p = fact.getPosition();
        final double mapX = m.getA() * p[0] + m.getC() * p[1] + m.getE();
        final double mapY = m.getB() * p[0] + m.getD() * p[1] + m.getF();
        assertThat(mapX).isCloseTo(70.0, within(0.001));
        assertThat(mapY).isCloseTo(140.0, within(0.001));
    }

    /**
     * An object entry with no {@code <coords>} element has a {@code null}
     * position (rather than defaulting to a point), and still parses without
     * warning.
     */
    @Test
    void testParse_missingCoords_nullPosition() {
        final String xml = "<entry><type>sensor</type><name>S1</name></entry>";
        final TemporalEntry objEntry = entry("s1", 100, xml);

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
        final String xml = "<entry><type>gate</type><coords>50,75</coords></entry>";
        final TemporalEntry objEntry = entry("g1", 100, xml);

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
        final String bgXml = "<entry><type>background</type><img>floor.png</img></entry>";
        final String obj1Xml = "<entry><type>gate</type><coords>10,20</coords></entry>";
        final String obj2Xml = "<entry><type>camera</type><coords>30,40</coords></entry>";

        final List<Fact> facts = FloorMapEntryParser.parse(
                List.of(entry("background", 100, bgXml),
                        entry("gate-1", 100, obj1Xml),
                        entry("cam-1", 100, obj2Xml)),
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
     * A value written as a CDATA section reads exactly like plain text.
     *
     * <p>CDATA and text differ only in how the source document escapes them; to a
     * reader they are the same string. Ignoring CDATA made such a value read as
     * absent, so a fact whose type or image was wrapped in CDATA lost it.</p>
     */
    @Test
    void testParse_cdataValuesAreRead() {
        final String xml = "<entry><type><![CDATA[gate]]></type>"
                + "<img><![CDATA[floor1.png]]></img>"
                + "<coords>50,75</coords></entry>";

        final List<Fact> facts = FloorMapEntryParser.parse(
                List.of(entry("cdata-fact", 100, xml)), SCHEMA, ACCESSOR, warnings::add);

        assertThat(facts).hasSize(1);
        assertThat(facts.getFirst().getType()).isEqualToIgnoringCase("gate");
        assertThat(facts.getFirst().getImage()).isEqualTo("floor1.png");
        assertThat(warnings).isEmpty();
    }

    /**
     * Re-serialising after an edit must not destroy CDATA content or comments.
     *
     * <p>This is the regression test for the data loss that a plain object drag used
     * to cause: the editor re-serialises the whole value on every transform, and the
     * serialiser emitted only element and text nodes, so the contents of every CDATA
     * section and comment were silently dropped from the saved document.</p>
     *
     * <p>CDATA content is preserved as escaped text rather than as a CDATA section.
     * The two forms are equivalent to any XML reader, and escaping avoids having to
     * split the payload around a literal {@code ]]>}. What must survive is the
     * <em>content</em>, and it does.</p>
     */
    @Test
    void testSerialize_preservesCdataContentAndComments() {
        final String xml = "<entry><type><![CDATA[gate & co]]></type>"
                + "<!-- provenance: imported 2026-01-01 -->"
                + "<coords>50,75</coords></entry>";

        final ParsedValue parsed = ACCESSOR.parse(xml);
        assertThat(parsed).isNotNull();

        // Simulate what an object drag does: touch one field, then re-serialise.
        ACCESSOR.setArray(parsed, "/entry/coords", new double[]{10, 20});
        final String round = ACCESSOR.serialise(parsed);

        assertThat(round)
                .as("CDATA content must survive, escaped rather than deleted")
                .contains("gate &amp; co");
        assertThat(round)
                .as("comments must survive")
                .contains("<!-- provenance: imported 2026-01-01 -->");
        assertThat(round)
                .as("the edited field is written back (setArray emits doubles)")
                .contains("10.0,20.0");

        // And the round-tripped document still parses to the same fact.
        final List<Fact> facts = FloorMapEntryParser.parse(
                List.of(entry("round", 100, round)), SCHEMA, ACCESSOR, warnings::add);
        assertThat(facts).hasSize(1);
        assertThat(facts.getFirst().getType()).isEqualToIgnoringCase("gate & co");
        assertThat(warnings).isEmpty();
    }

    /**
     * An element that is present but holds an unreadable matrix is corrupt data and
     * is reported, whereas an element that is simply absent is not — the same
     * present-versus-absent distinction the JSON parser draws.
     */
    @Test
    void testParse_malformedMatrixIsReported_absentMatrixIsNot() {
        final List<Fact> bad = FloorMapEntryParser.parse(
                List.of(entry("bad", 100,
                        "<entry><type>gate</type><coords>1,2</coords>"
                                + "<tm-world-to-map>2,0,x,2,10,20</tm-world-to-map></entry>")),
                SCHEMA, ACCESSOR, warnings::add);

        assertThat(bad).isEmpty();
        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst()).contains("bad");

        warnings.clear();

        final List<Fact> absent = FloorMapEntryParser.parse(
                List.of(entry("fine", 100,
                        "<entry><type>gate</type><coords>1,2</coords></entry>")),
                SCHEMA, ACCESSOR, warnings::add);

        assertThat(absent).hasSize(1);
        assertThat(absent.getFirst().getWorldToMap())
                .isEqualTo(FloorMapTransformationMatrix.identity());
        assertThat(warnings).isEmpty();
    }

    /**
     * An entry whose value is not valid XML is skipped (excluded from the
     * result) while other, well-formed entries still parse, and a warning is
     * emitted for the bad one.
     */
    @Test
    void testParse_malformedEntry_skippedWithWarning() {
        final TemporalEntry badEntry = entry("bad", 100, "<not-closed>");
        final TemporalEntry goodEntry = entry("good", 100,
                "<entry><type>gate</type><coords>1,2</coords></entry>");

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
        final TemporalEntry badEntry = entry("sensor-42", 100, "<unclosed");

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
                List.of(entry("bad1", 100, "<xxx"),
                        entry("bad2", 100, "<yyy"),
                        entry("good", 100, "<entry><type>gate</type><coords>1,2</coords></entry>")),
                SCHEMA, ACCESSOR, warnings::add);

        assertThat(facts).hasSize(1);
        assertThat(warnings).hasSize(2);
        assertThat(warnings.getFirst()).contains("bad1");
        assertThat(warnings.get(1)).contains("bad2");
    }

    /**
     * Passing a {@code null} warning consumer silently skips warning
     * emission rather than throwing a {@code NullPointerException}.
     */
    @Test
    void testParse_nullWarningConsumer_doesNotThrow() {
        final TemporalEntry badEntry = entry("bad", 100, "<not-closed>");
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
     * An entry missing the {@code <type>} element still parses successfully,
     * defaulting to an empty type string without emitting a warning.
     */
    @Test
    void testParse_nullType_usesEmptyString_noWarning() {
        final String xml = "<entry><coords>5,10</coords></entry>";
        final TemporalEntry objEntry = entry("k1", 100, xml);

        final List<Fact> facts =
                FloorMapEntryParser.parse(List.of(objEntry), SCHEMA, ACCESSOR, warnings::add);

        assertThat(facts).hasSize(1);
        assertThat(facts.getFirst().getType()).isEmpty();
        assertThat(warnings).as("missing type is not a warning-worthy condition").isEmpty();
    }

    // -----------------------------------------------------------------------
    // parse — XML-specific: attribute-based paths
    // -----------------------------------------------------------------------

    /**
     * A schema mapping a role to an attribute path (e.g. {@code "/entry/@type"})
     * reads the value from the XML attribute rather than a child element —
     * a feature with no JSON equivalent.
     */
    @Test
    void testParse_typeAsAttribute() {
        final List<FloorMapFieldMapping> attrSchema = List.of(
                new FloorMapFieldMapping("/entry/@type", Role.TYPE, "Type", null),
                new FloorMapFieldMapping("/entry/coords", Role.POSITION, "Coords", null));
        final String xml = "<entry type=\"gate\"><coords>1,2</coords></entry>";
        final TemporalEntry objEntry = entry("g1", 100, xml);

        final List<Fact> facts =
                FloorMapEntryParser.parse(List.of(objEntry), attrSchema, ACCESSOR, warnings::add);

        assertThat(facts).hasSize(1);
        assertThat(facts.getFirst().getType()).isEqualTo("gate");
        assertThat(warnings).isEmpty();
    }

    // -----------------------------------------------------------------------
    // parse — XML-specific: namespaces are ignored
    // -----------------------------------------------------------------------

    /**
     * An entry declaring a default namespace (via an unprefixed
     * {@code xmlns="..."} attribute on the root) parses identically to the
     * equivalent namespace-free document: elements are still matched by
     * their plain path (e.g. {@code "/entry/type"}) and no warning is
     * emitted. A default namespace does not add a prefix to element tag
     * names, so this case already "just works" without any special
     * namespace-handling logic — the same as a non-attribute-based read.
     */
    @Test
    void testParse_defaultNamespace_ignored() {
        final String xml = "<entry xmlns=\"http://example.com/floormap\">"
                + "<type>gate</type><name>Gate-1</name><coords>100,200</coords></entry>";
        final TemporalEntry objEntry = entry("gate-1", 100, xml);

        final List<Fact> facts =
                FloorMapEntryParser.parse(List.of(objEntry), SCHEMA, ACCESSOR, warnings::add);

        assertThat(facts).hasSize(1);
        final Fact fact = facts.getFirst();
        assertThat(fact.getType()).isEqualTo("gate");
        assertThat(fact.getPosition()).isNotNull();
        assertThat(fact.getPosition()[0]).isCloseTo(100.0, within(0.001));
        assertThat(fact.getPosition()[1]).isCloseTo(200.0, within(0.001));
        assertThat(warnings).as("a default namespace should not affect parsing").isEmpty();
    }

    /**
     * An entry declaring a prefixed namespace (e.g. {@code xmlns:fm="..."}
     * with every element written as {@code <fm:...>}) still parses
     * identically to the equivalent namespace-free document. The value
     * schema's paths are plain, unprefixed names (e.g. {@code "/entry/type"}),
     * and the parser must match elements by local name only, discarding the
     * {@code fm:} prefix — i.e. the namespace prefix is ignored rather than
     * causing the field to be missed. This also exercises numeric-array
     * ({@code coords}, {@code tm-world-to-map}) extraction through prefixed
     * elements, confirming the coordinate transform still applies.
     */
    @Test
    void testParse_prefixedNamespace_ignored() {
        final String xml = "<fm:entry xmlns:fm=\"http://example.com/floormap\">"
                + "<fm:type>camera</fm:type><fm:name>Cam-1</fm:name>"
                + "<fm:coords>10,20</fm:coords>"
                + "<fm:tm-world-to-map>2,0,0,2,50,100</fm:tm-world-to-map>"
                + "</fm:entry>";
        final TemporalEntry objEntry = entry("cam-1", 100, xml);

        final List<Fact> facts =
                FloorMapEntryParser.parse(List.of(objEntry), SCHEMA, ACCESSOR, warnings::add);

        assertThat(facts).hasSize(1);
        final Fact fact = facts.getFirst();
        assertThat(fact.getType()).isEqualTo("camera");
        // World-to-map: scale 2x, translate (50, 100).
        // World coords (10, 20) → map coords (2*10 + 50, 2*20 + 100) = (70, 140)
        final FloorMapTransformationMatrix m = fact.getWorldToMap();
        final double[] p = fact.getPosition();
        assertThat(p).isNotNull();
        final double mapX = m.getA() * p[0] + m.getC() * p[1] + m.getE();
        final double mapY = m.getB() * p[0] + m.getD() * p[1] + m.getF();
        assertThat(mapX).isCloseTo(70.0, within(0.001));
        assertThat(mapY).isCloseTo(140.0, within(0.001));
        assertThat(warnings)
                .as("a namespace prefix on every element should not affect parsing")
                .isEmpty();
    }

    // -----------------------------------------------------------------------
    // Areas (GEOMETRY / FILL / OPACITY)
    // -----------------------------------------------------------------------

    /**
     * An XML area entry parses to a fact with vertices, fill and opacity —
     * geometry as comma-separated numbers, opacity via the scalar
     * {@code getNumber} path.
     */
    @Test
    void testParse_areaEntry() {
        final List<FloorMapFieldMapping> schema = new ArrayList<>(SCHEMA);
        schema.add(new FloorMapFieldMapping("/entry/geometry", Role.GEOMETRY, "Geometry", null));
        schema.add(new FloorMapFieldMapping("/entry/fill", Role.FILL, "Fill", null));
        schema.add(new FloorMapFieldMapping("/entry/opacity", Role.OPACITY, "Opacity", null));

        final String xml = "<entry><type>area</type><name>Zone</name>"
                + "<geometry>0,0,100,0,100,50,0,50</geometry>"
                + "<fill>#ff0000</fill><opacity>0.5</opacity>"
                + "<tm-world-to-map>1,0,0,1,20,30</tm-world-to-map></entry>";
        final List<Fact> facts = FloorMapEntryParser.parse(
                List.of(entry("area-1", 0L, xml)), schema, ACCESSOR, warnings::add);

        assertThat(facts).hasSize(1);
        final Fact fact = facts.getFirst();
        assertThat(fact.hasVertices()).isTrue();
        assertThat(fact.getVertices()).hasDimensions(4, 2);
        assertThat(fact.getVertices()[2]).containsExactly(100, 50);
        assertThat(fact.getFill()).isEqualTo("#ff0000");
        assertThat(fact.getOpacity()).isCloseTo(0.5, within(1e-9));
        assertThat(warnings).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static TemporalEntry entry(final String key,
                                       @SuppressWarnings("SameParameterValue") final long time,
                                       final String value) {
        return new TemporalEntry(MAP, key, time, value);
    }
}
