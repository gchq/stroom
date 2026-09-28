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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable DTO representing a single field mapping that tells the floor map
 * editor how to read/write a field from a temporal entry's Value column.
 *
 * <p>Each instance maps a path within the serialised value (e.g. a JSON
 * pointer such as {@code ".type"}) to a semantic {@link Role} that the
 * floor map UI understands. The four fields are:</p>
 * <ul>
 *   <li><b>path</b> – a dot-prefixed path into the value structure
 *       (e.g. {@code ".type"}, {@code ".coords"}). May be {@code null}
 *       if the mapping is purely metadata.</li>
 *   <li><b>role</b> – the {@link Role} that defines how the floor map
 *       editor interprets this field. May be {@code null} for
 *       unmapped entries.</li>
 *   <li><b>displayName</b> – a human-readable label shown in the editor
 *       UI. May be {@code null} if the field should not be displayed
 *       to the user.</li>
 *   <li><b>defaultValue</b> – the fallback value inserted when a new
 *       entity is created and this field is not yet populated. May be
 *       {@code null} to indicate no default.</li>
 * </ul>
 *
 * <p>A list of these mappings forms the <em>value schema</em> stored in
 * {@link FloorMapDoc#getValueSchema()}. The schema tells the UI which
 * fields to expect, in what order, and how to render them.</p>
 *
 * @see FloorMapDoc#getValueSchema()
 * @see Role
 */
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class FloorMapFieldMapping {

    @JsonProperty
    private final String path;

    @JsonProperty
    private final Role role;

    @JsonProperty
    private final String displayName;

    @JsonProperty
    private final String defaultValue;

    /**
     * Defines what a mapped field means to the floor map editor.
     */
    public enum Role {
        /** Object type / icon category. */
        TYPE,
        /** Display name on the canvas. */
        LABEL,
        /** Position coordinates (e.g. a JSON array {@code [x, y]}). */
        POSITION,
        /** Background image URL/data. */
        IMAGE,
        /** Transformation matrix – 6-element array (world to map). Every fact,
         * backgrounds included, is placed by this matrix. */
        WORLD_TO_MAP,
        /**
         * Legacy background matrix (map to screen). No longer read or written —
         * backgrounds now use {@link #WORLD_TO_MAP}. Retained only so existing
         * documents whose schema references this role still deserialise; it is
         * not added to new default schemas.
         */
        MAP_TO_SCREEN,
        /** Area polygon vertices — flat array {@code [x0, y0, x1, y1, ...]} in
         * the fact's local frame, placed by {@link #WORLD_TO_MAP}. */
        GEOMETRY,
        /** Area fill colour (hex string, e.g. {@code "#1e88e5"}). */
        FILL,
        /** Area fill opacity (number in [0, 1]). */
        OPACITY,
        /** Extra user-defined field. */
        CUSTOM
    }

    /**
     * Constructs a new field mapping.
     *
     * <p>All parameters are nullable; see the class-level Javadoc for the
     * semantics of each field.</p>
     *
     * @param path         dot-prefixed path into the serialised value
     *                     (e.g. {@code ".type"}), or {@code null}
     * @param role         the semantic {@link Role} of this field, or
     *                     {@code null} if unmapped
     * @param displayName  human-readable label for the editor UI, or
     *                     {@code null} if the field should be hidden
     * @param defaultValue fallback value for new entities, or {@code null}
     *                     for no default
     */
    @JsonCreator
    public FloorMapFieldMapping(@JsonProperty("path") final String path,
                                @JsonProperty("role") final Role role,
                                @JsonProperty("displayName") final String displayName,
                                @JsonProperty("defaultValue") final String defaultValue) {
        this.path = path;
        this.role = role;
        this.displayName = displayName;
        this.defaultValue = defaultValue;
    }

    /**
     * Returns the dot-prefixed path into the serialised value structure.
     *
     * @return the path string (e.g. {@code ".type"}), or {@code null}
     */
    public String getPath() {
        return path;
    }

    /**
     * Returns the semantic role of this field within the floor map.
     *
     * @return the {@link Role}, or {@code null} if this mapping has no
     *         assigned role
     */
    public Role getRole() {
        return role;
    }

    /**
     * Returns the human-readable label shown for this field in the
     * floor map editor UI.
     *
     * @return the display name, or {@code null} if the field should
     *         not be displayed
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the fallback value used when a new floor map entity is
     * created and this field has not been populated.
     *
     * @return the default value string, or {@code null} if no default
     *         is defined
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * Returns the initial value-schema mappings used to seed a
     * newly-created {@link FloorMapDoc}.
     *
     * <p>This method is <strong>not</strong> intended as a runtime fallback.
     * Once a document is created, its schema is persisted and should be
     * read from {@link FloorMapDoc#getValueSchema()}. This method exists
     * solely so that {@code FloorMapInitPresenter} can provide a sensible
     * starting configuration.</p>
     *
     * <p>The returned list contains the following mappings:</p>
     * <table>
     *   <caption>Initial field mappings</caption>
     *   <tr><th>Path</th><th>Role</th><th>Display Name</th><th>Default Value</th></tr>
     *   <tr><td>{@code .type}</td><td>{@link Role#TYPE}</td><td>Type</td><td>{@code null}</td></tr>
     *   <tr><td>{@code .name}</td><td>{@link Role#LABEL}</td><td>Name</td><td>{@code null}</td></tr>
     *   <tr><td>{@code .coords}</td><td>{@link Role#POSITION}</td><td>Coords</td><td>{@code null}</td></tr>
     *   <tr><td>{@code .img}</td><td>{@link Role#IMAGE}</td><td>Image</td><td>{@code null}</td></tr>
     *   <tr><td>{@code .tm-world-to-map}</td><td>{@link Role#WORLD_TO_MAP}</td>
     *       <td>{@code null}</td><td>{@code null}</td></tr>
     *   <tr><td>{@code .geometry}</td><td>{@link Role#GEOMETRY}</td><td>Geometry</td><td>{@code null}</td></tr>
     *   <tr><td>{@code .fill}</td><td>{@link Role#FILL}</td><td>Fill</td><td>{@code null}</td></tr>
     *   <tr><td>{@code .opacity}</td><td>{@link Role#OPACITY}</td><td>Opacity</td><td>{@code null}</td></tr>
     * </table>
     *
     * <p>The returned list is created via {@link List#of(Object...)} and is
     * therefore <em>unmodifiable</em>; any attempt to mutate it will throw
     * {@link UnsupportedOperationException}.</p>
     *
     * @return a non-null, unmodifiable list of the initial field mappings
     */
    public static List<FloorMapFieldMapping> initialValueSchema() {
        return List.of(
                new FloorMapFieldMapping(".type", Role.TYPE, "Type", null),
                new FloorMapFieldMapping(".name", Role.LABEL, "Name", null),
                new FloorMapFieldMapping(".coords", Role.POSITION, "Coords", null),
                new FloorMapFieldMapping(".img", Role.IMAGE, "Image", null),
                new FloorMapFieldMapping(".tm-world-to-map", Role.WORLD_TO_MAP, null, null),
                new FloorMapFieldMapping(".geometry", Role.GEOMETRY, "Geometry", null),
                new FloorMapFieldMapping(".fill", Role.FILL, "Fill", null),
                new FloorMapFieldMapping(".opacity", Role.OPACITY, "Opacity", null)
        );
    }

    /**
     * Returns a copy of {@code schema} guaranteed to contain mappings for the
     * area roles ({@link Role#GEOMETRY}, {@link Role#FILL},
     * {@link Role#OPACITY}), appending a default mapping for each role that is
     * absent.
     *
     * <p>The check is role-based, not path-based, so a schema that maps
     * {@link Role#GEOMETRY} to a customised path is returned with that mapping
     * untouched. Default paths are derived as <em>siblings</em> of the
     * schema's existing paths — {@code ".type"} yields {@code ".geometry"},
     * {@code "/entry/type"} yields {@code "/entry/geometry"} — so the merge is
     * correct for both value formats and custom XML root elements;
     * {@code format} decides the style only when the schema has no usable
     * path to derive from. The input list is never mutated (it may be the
     * unmodifiable {@link #initialValueSchema()} seed); a new list is always
     * returned.</p>
     *
     * @param schema the existing schema, or {@code null} (treated as empty)
     * @param format the document's value format, used only as the fallback
     *               path style; {@code null} is treated as JSON
     * @return a new list containing all existing mappings plus defaults for
     *         any missing area roles; never {@code null}
     */
    public static List<FloorMapFieldMapping> withAreaMappings(final List<FloorMapFieldMapping> schema,
                                                              final ValueFormat format) {
        final List<FloorMapFieldMapping> result = new ArrayList<>();
        if (schema != null) {
            result.addAll(schema);
        }
        if (isRoleMissing(result, Role.GEOMETRY)) {
            result.add(new FloorMapFieldMapping(
                    siblingPath(result, format, FloorMapJsonKeys.GEOMETRY), Role.GEOMETRY, "Geometry", null));
        }
        if (isRoleMissing(result, Role.FILL)) {
            result.add(new FloorMapFieldMapping(
                    siblingPath(result, format, "fill"), Role.FILL, "Fill", null));
        }
        if (isRoleMissing(result, Role.OPACITY)) {
            result.add(new FloorMapFieldMapping(
                    siblingPath(result, format, FloorMapJsonKeys.OPACITY), Role.OPACITY, "Opacity", null));
        }
        return result;
    }

    /**
     * Derives a path for {@code name} alongside the schema's existing paths:
     * an XPath-style path keeps its parent ({@code "/entry/type"} →
     * {@code "/entry/geometry"}); a dot-style path yields {@code "." + name}.
     * Falls back to the given format's convention when no path is available.
     */
    private static String siblingPath(final List<FloorMapFieldMapping> schema,
                                      final ValueFormat format,
                                      final String name) {
        for (final FloorMapFieldMapping mapping : schema) {
            final String path = mapping != null ? mapping.getPath() : null;
            if (path != null && path.startsWith("/")) {
                final int lastSlash = path.lastIndexOf('/');
                if (lastSlash > 0) {
                    return path.substring(0, lastSlash + 1) + name;
                }
            } else if (path != null && path.startsWith(".")) {
                return "." + name;
            }
        }
        return format == ValueFormat.XML ? "/entry/" + name : "." + name;
    }

    /**
     * True when {@code schema} has <strong>no</strong> mapping for {@code role}.
     *
     * <p>Named for the sense the callers actually use — "add a default mapping for
     * this role if it is missing" — so that a call site reads as what it does. The
     * inverse sense ({@code hasRole} returning {@code false} when the role is
     * present) makes every call site read as its own opposite.</p>
     */
    private static boolean isRoleMissing(final List<FloorMapFieldMapping> schema, final Role role) {
        // Presence of the role, not of a path: a path-less mapping still counts as
        // present, so no second mapping is added for it. Hence not findPath.
        for (final FloorMapFieldMapping mapping : schema) {
            if (mapping != null && mapping.getRole() == role) {
                return false;
            }
        }
        return true;
    }

    /**
     * Finds the path mapped to {@code role} in the schema.
     *
     * <p>A mapping that is present but has no path counts as unmapped, since
     * there is nowhere to read or write the value.</p>
     *
     * @param schema the value schema; may be {@code null}, and may contain
     *               {@code null} entries, which are skipped
     * @param role   the role to look up
     * @return the path of the first mapping for the role, or {@code null} if the
     *         role is not mapped
     */
    public static String findPath(final List<FloorMapFieldMapping> schema, final Role role) {
        if (schema == null) {
            return null;
        }
        for (final FloorMapFieldMapping mapping : schema) {
            if (mapping != null && mapping.getRole() == role) {
                return mapping.getPath();
            }
        }
        return null;
    }

    /**
     * Finds the path mapped to {@code role}, failing loudly when there is none.
     *
     * <p>For writers: the value accessors silently ignore a write to a
     * {@code null} path, so writing through {@link #findPath} for an unmapped role
     * would drop the value without a trace.</p>
     *
     * @param schema the value schema; may be {@code null}
     * @param role   the role to look up
     * @return the path for the role; never {@code null}
     * @throws IllegalStateException if the schema does not map the role
     */
    public static String requirePath(final List<FloorMapFieldMapping> schema, final Role role) {
        final String path = findPath(schema, role);
        if (path == null) {
            throw new IllegalStateException(
                    "The Value Schema for this Floor Map does not define a mapping "
                    + "for the '" + role + "' role. Please add a '" + role
                    + "' mapping in the Settings tab under Value Schema.");
        }
        return path;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FloorMapFieldMapping that = (FloorMapFieldMapping) o;
        return Objects.equals(path, that.path)
                && role == that.role
                && Objects.equals(displayName, that.displayName)
                && Objects.equals(defaultValue, that.defaultValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, role, displayName, defaultValue);
    }

    @Override
    public String toString() {
        return "FloorMapFieldMapping{"
                + "path='" + path + '\''
                + ", role=" + role
                + ", displayName='" + displayName + '\''
                + ", defaultValue='" + defaultValue + '\''
                + '}';
    }
}
