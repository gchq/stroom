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

import stroom.floormap.shared.FloorMapFieldMapping.Role;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Tests for [FloorMapFieldMapping] — construction, equality,
/// and JSON serialisation round-trip.
class TestFloorMapFieldMapping {

    private static final List<FloorMapFieldMapping> INITIAL_SCHEMA =
            FloorMapFieldMapping.initialValueSchema();

    @Test
    void testConstruction() {
        final FloorMapFieldMapping mapping = new FloorMapFieldMapping(
                ".type", Role.TYPE, "Type", "gates");

        assertThat(mapping.getPath()).isEqualTo(".type");
        assertThat(mapping.getRole()).isEqualTo(Role.TYPE);
        assertThat(mapping.getDisplayName()).isEqualTo("Type");
        assertThat(mapping.getDefaultValue()).isEqualTo("gates");
    }

    @Test
    void testNullFields() {
        final FloorMapFieldMapping mapping = new FloorMapFieldMapping(
                null, null, null, null);

        assertThat(mapping.getPath()).isNull();
        assertThat(mapping.getRole()).isNull();
        assertThat(mapping.getDisplayName()).isNull();
        assertThat(mapping.getDefaultValue()).isNull();
    }

    @Test
    void testEquality() {
        final FloorMapFieldMapping a = new FloorMapFieldMapping(
                ".type", Role.TYPE, "Type", "gates");
        final FloorMapFieldMapping b = new FloorMapFieldMapping(
                ".type", Role.TYPE, "Type", "gates");
        final FloorMapFieldMapping c = new FloorMapFieldMapping(
                ".coords", Role.POSITION, "Position", null);

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void testJsonRoundTrip_singleMapping() {
        final FloorMapFieldMapping original = new FloorMapFieldMapping(
                ".type", Role.TYPE, "Type", "gates");

        final String json = JsonUtil.writeValueAsString(original);
        assertThat(json).isNotNull();
        assertThat(json).contains("\"path\"");
        assertThat(json).contains(".type");
        assertThat(json).contains("\"role\"");
        assertThat(json).contains("TYPE");

        final FloorMapFieldMapping deserialized =
                JsonUtil.readValue(json, FloorMapFieldMapping.class);
        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void testJsonRoundTrip_nullFieldsOmitted() {
        final FloorMapFieldMapping original = new FloorMapFieldMapping(
                ".coords", Role.POSITION, "Position", null);

        final String json = JsonUtil.writeValueAsString(original);

        // null fields should be omitted (@JsonInclude(Include.NON_NULL))
        assertThat(json).doesNotContain("defaultValue");

        final FloorMapFieldMapping deserialized =
                JsonUtil.readValue(json, FloorMapFieldMapping.class);
        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void testJsonRoundTrip_schemaList() {
        final List<FloorMapFieldMapping> schema = List.of(
                new FloorMapFieldMapping(
                        ".type", Role.TYPE, "Type", "gates"),
                new FloorMapFieldMapping(
                        ".coords", Role.POSITION, "Position", null),
                new FloorMapFieldMapping(
                        ".img", Role.IMAGE, "Image", null),
                new FloorMapFieldMapping(
                        ".tm-world-to-map", Role.WORLD_TO_MAP,
                        "World to Map", null),
                new FloorMapFieldMapping(
                        ".tm-map-to-screen", Role.MAP_TO_SCREEN,
                        "Map to Screen", null));

        // Embed in a FloorMapDoc to test full round-trip
        final FloorMapDoc doc = FloorMapDoc.builder()
                .uuid("test-uuid")
                .name("TestMap")
                .valueSchema(schema)
                .build();

        final String json = JsonUtil.writeValueAsString(doc);
        assertThat(json).isNotNull();

        final FloorMapDoc deserialized =
                JsonUtil.readValue(json, FloorMapDoc.class);
        assertThat(deserialized.getValueSchema())
                .hasSize(5)
                .isEqualTo(schema);
    }

    @Test
    void testJsonRoundTrip_xmlPaths() {
        final List<FloorMapFieldMapping> schema = List.of(
                new FloorMapFieldMapping(
                        "/entry/type", Role.TYPE, "Type", null),
                new FloorMapFieldMapping(
                        "/entry/coords", Role.POSITION, "Position", null),
                new FloorMapFieldMapping(
                        "/entry/@id", null, "ID", null));

        final FloorMapDoc doc = FloorMapDoc.builder()
                .uuid("test-uuid")
                .name("TestMap")
                .valueFormat(ValueFormat.XML)
                .valueSchema(schema)
                .build();

        final String json = JsonUtil.writeValueAsString(doc);
        final FloorMapDoc deserialized =
                JsonUtil.readValue(json, FloorMapDoc.class);

        assertThat(deserialized.getValueFormat())
                .isEqualTo(ValueFormat.XML);
        assertThat(deserialized.getValueSchema())
                .hasSize(3)
                .isEqualTo(schema);
        assertThat(deserialized.getValueSchema().get(2).getPath())
                .isEqualTo("/entry/@id");
    }

    @Test
    void testAllRoles() {
        // Verify all Role enum values can be serialised/deserialised
        for (final Role role : Role.values()) {
            final FloorMapFieldMapping original =
                    new FloorMapFieldMapping(
                            ".test", role, role.name(), null);

            final String json =
                    JsonUtil.writeValueAsString(original);
            final FloorMapFieldMapping deserialized =
                    JsonUtil.readValue(json,
                            FloorMapFieldMapping.class);

            assertThat(deserialized.getRole())
                    .isEqualTo(role);
        }
    }

    @Test
    void testInitialValueSchema() {
        // Verify the static initialValueSchema() helper
        final List<FloorMapFieldMapping> schema =
                FloorMapFieldMapping.initialValueSchema();

        assertThat(schema).isNotNull().isNotEmpty();
        // Should contain at least TYPE and POSITION
        assertThat(schema.stream()
                .anyMatch(m -> m.getRole() == Role.TYPE))
                .isTrue();
        assertThat(schema.stream()
                .anyMatch(m -> m.getRole() == Role.POSITION))
                .isTrue();
        // New documents support areas out of the box.
        assertThat(schema.stream()
                .anyMatch(m -> m.getRole() == Role.GEOMETRY))
                .isTrue();
        assertThat(schema.stream()
                .anyMatch(m -> m.getRole() == Role.FILL))
                .isTrue();
        assertThat(schema.stream()
                .anyMatch(m -> m.getRole() == Role.OPACITY))
                .isTrue();
        // ...and deleting a fact from a point in time.
        assertThat(FloorMapFieldMapping.findPath(schema, Role.STATUS)).isEqualTo(".status");
    }

    /// A STATUS mapping survives serialisation like any other role.
    @Test
    void testJsonRoundTrip_statusMapping() {
        final FloorMapFieldMapping original =
                new FloorMapFieldMapping(".lifecycle", Role.STATUS, "Status", null);

        final FloorMapFieldMapping restored = JsonUtil.readValue(
                JsonUtil.writeValueAsString(original), FloorMapFieldMapping.class);

        assertThat(restored.getRole()).isEqualTo(Role.STATUS);
        assertThat(restored.getPath()).isEqualTo(".lifecycle");
    }

    /// All three default area mappings are appended to a pre-area schema.
    @Test
    void testWithAreaMappings_appendsMissing() {
        final List<FloorMapFieldMapping> legacy = List.of(
                new FloorMapFieldMapping(".type", Role.TYPE, "Type", null),
                new FloorMapFieldMapping(".coords", Role.POSITION, "Coords", null));

        final List<FloorMapFieldMapping> merged =
                FloorMapFieldMapping.withAreaMappings(legacy, ValueFormat.JSON);

        assertThat(merged).hasSize(5);
        // Existing mappings keep their position and settings.
        assertThat(merged.getFirst()).isEqualTo(legacy.getFirst());
        assertThat(merged.get(1)).isEqualTo(legacy.get(1));
        assertThat(merged.stream().map(FloorMapFieldMapping::getRole))
                .contains(Role.GEOMETRY, Role.FILL, Role.OPACITY);
        // The immutable input list is untouched.
        assertThat(legacy).hasSize(2);
    }

    /// On an XML-format schema the derived paths are XPath-style siblings of
    /// the existing mappings — a JSON-style ".geometry" would be silently
    /// unwritable by the XML accessor.
    @Test
    void testWithAreaMappings_xmlPathsDerivedFromSiblings() {
        final List<FloorMapFieldMapping> xmlSchema = List.of(
                new FloorMapFieldMapping("/rec/type", Role.TYPE, "Type", null),
                new FloorMapFieldMapping("/rec/coords", Role.POSITION, "Coords", null));

        final List<FloorMapFieldMapping> merged =
                FloorMapFieldMapping.withAreaMappings(xmlSchema, ValueFormat.XML);

        assertThat(merged.stream()
                .filter(m -> m.getRole() == Role.GEOMETRY)
                .map(FloorMapFieldMapping::getPath))
                .containsExactly("/rec/geometry");
        assertThat(merged.stream()
                .filter(m -> m.getRole() == Role.FILL)
                .map(FloorMapFieldMapping::getPath))
                .containsExactly("/rec/fill");

        // With no existing path to derive from, the format decides the style.
        assertThat(FloorMapFieldMapping.withAreaMappings(null, ValueFormat.XML)
                .getFirst().getPath())
                .isEqualTo("/entry/geometry");
    }

    /// The check is role-based: a customised path for an area role is kept, and
    /// only the genuinely missing roles are appended.
    @Test
    void testWithAreaMappings_respectsCustomisedPaths() {
        final List<FloorMapFieldMapping> schema = List.of(
                new FloorMapFieldMapping(".poly", Role.GEOMETRY, "Polygon", null));

        final List<FloorMapFieldMapping> merged =
                FloorMapFieldMapping.withAreaMappings(schema, ValueFormat.JSON);

        assertThat(merged).hasSize(3);
        assertThat(merged.getFirst().getPath()).isEqualTo(".poly");
        assertThat(merged.stream()
                .filter(m -> m.getRole() == Role.GEOMETRY))
                .hasSize(1);
    }

    /// Merging an already-complete schema changes nothing; null is tolerated.
    @Test
    void testWithAreaMappings_idempotent() {
        final List<FloorMapFieldMapping> complete =
                FloorMapFieldMapping.withAreaMappings(
                        FloorMapFieldMapping.initialValueSchema(), ValueFormat.JSON);
        assertThat(FloorMapFieldMapping.withAreaMappings(complete, ValueFormat.JSON))
                .isEqualTo(complete);

        assertThat(FloorMapFieldMapping.withAreaMappings(null, ValueFormat.JSON))
                .extracting(FloorMapFieldMapping::getRole)
                .containsExactly(Role.GEOMETRY, Role.FILL, Role.OPACITY);
    }

    // -----------------------------------------------------------------------
    // findPath
    // -----------------------------------------------------------------------

    /// The path mapped to the `TYPE` role in the schema is returned.
    @Test
    void testFindPath_type() {
        assertThat(FloorMapFieldMapping.findPath(INITIAL_SCHEMA, Role.TYPE))
                .isEqualTo(".type");
    }

    /// The path mapped to the `POSITION` role in the schema is returned.
    @Test
    void testFindPath_position() {
        assertThat(FloorMapFieldMapping.findPath(INITIAL_SCHEMA, Role.POSITION))
                .isEqualTo(".coords");
    }

    /// The path mapped to the `IMAGE` role in the schema is returned.
    @Test
    void testFindPath_image() {
        assertThat(FloorMapFieldMapping.findPath(INITIAL_SCHEMA, Role.IMAGE))
                .isEqualTo(".img");
    }

    /// The path mapped to the `WORLD_TO_MAP` role in the schema is
    /// returned.
    @Test
    void testFindPath_worldToMap() {
        assertThat(FloorMapFieldMapping.findPath(INITIAL_SCHEMA, Role.WORLD_TO_MAP))
                .isEqualTo(".tm-world-to-map");
    }

    /// A `null` schema yields `null` rather than throwing.
    @Test
    void testFindPath_nullSchema() {
        assertThat(FloorMapFieldMapping.findPath(null, Role.TYPE)).isNull();
    }

    /// When the schema has no mapping for the requested role, `null` is
    /// returned instead of matching an unrelated entry.
    @Test
    void testFindPath_missingRole() {
        final List<FloorMapFieldMapping> partial = List.of(
                new FloorMapFieldMapping(".type", Role.TYPE, "Type", null));
        assertThat(FloorMapFieldMapping.findPath(partial, Role.POSITION)).isNull();
    }

    /// `null` entries in the schema are skipped rather than throwing.
    @Test
    void testFindPath_nullElement() {
        final List<FloorMapFieldMapping> schema = new ArrayList<>();
        schema.add(null);
        schema.add(new FloorMapFieldMapping(".type", Role.TYPE, "Type", null));
        assertThat(FloorMapFieldMapping.findPath(schema, Role.TYPE)).isEqualTo(".type");
        assertThat(FloorMapFieldMapping.findPath(schema, Role.LABEL)).isNull();
    }

    // -----------------------------------------------------------------------
    // requirePath
    // -----------------------------------------------------------------------

    @Test
    void testRequirePath_mapped() {
        assertThat(FloorMapFieldMapping.requirePath(INITIAL_SCHEMA, Role.TYPE))
                .isEqualTo(".type");
    }

    /// An unmapped role fails loudly, naming the role, because a write to a
    /// `null` path would otherwise be silently dropped.
    @Test
    void testRequirePath_missingRole() {
        final List<FloorMapFieldMapping> partial = List.of(
                new FloorMapFieldMapping(".type", Role.TYPE, "Type", null));
        assertThatThrownBy(() -> FloorMapFieldMapping.requirePath(partial, Role.GEOMETRY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'GEOMETRY'");
    }

    @Test
    void testRequirePath_nullSchema() {
        assertThatThrownBy(() -> FloorMapFieldMapping.requirePath(null, Role.TYPE))
                .isInstanceOf(IllegalStateException.class);
    }
}
