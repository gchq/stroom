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

package stroom.floormap.client.value;

import stroom.floormap.client.model.Fact;
import stroom.floormap.shared.FloorMapFieldMapping.Role;
import stroom.floormap.shared.FloorMapTransformationMatrix;
import stroom.query.api.Column;
import stroom.query.api.Row;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Turns a StroomQL result table into {@link Fact}s for the canvas.
 *
 * <p>This lived as a private method on {@code FloorMapMapPresenter} — a GWT presenter, so
 * untestable — even though the work is pure: match schema roles to result columns, parse each
 * row's comma-separated values, and collapse the rows to one fact per key. Moving it here
 * makes the Map tab's entire ingest path testable on the JVM, which is where the parsing
 * mistakes it is prone to actually get caught.</p>
 *
 * <h3>Why the number parsing here differs from its neighbours</h3>
 * <p>Three sibling parsers exist and none of them is a drop-in replacement, so resist the
 * urge to unify them:</p>
 * <ul>
 *   <li>{@link FloorMapEntryParser} reads a <em>structured</em> value (JSON object or XML
 *       document) through a {@link ValueAccessor}. Here the input is the flattened text a
 *       result table carries, e.g. {@code "[1.0, 2.0]"}.</li>
 *   <li>{@link XmlValueText#parseCommaSeparatedNumbers(String)} is all-or-nothing: one bad
 *       element yields {@code null} for the whole array. That is correct there, because a
 *       partially-parsed coordinate array silently mis-places an object. It is
 *       <strong>deliberately not</strong> the rule here, because these parsers read only the
 *       leading values they need and have always ignored trailing extras — tightening that
 *       would stop placing objects that place correctly today.</li>
 *   <li>The accessors' {@code getArray} contract is stricter again.</li>
 * </ul>
 *
 * <p>The behaviour below is preserved exactly as it was in the presenter. Any change to it
 * is a decision about live maps, not a refactor.</p>
 */
public final class FloorMapFactTableParser {

    /** The result column carrying the temporal-store key; matched case-insensitively. */
    private static final String KEY_COLUMN = "Key";

    /** Vertex pairs below this count are not a polygon. */
    private static final int MIN_VERTICES = 3;

    /** A transformation matrix needs six components. */
    private static final int MATRIX_COMPONENTS = 6;

    private FloorMapFactTableParser() {
        // Static utility.
    }

    /**
     * Parses a result table into one {@link Fact} per key.
     *
     * <p>The query returns every effective-time shard of every key, in ascending
     * effective-time order, so a later shard overwrites an earlier one — the canvas shows a
     * single current instance per object rather than every time version at once. Distinct
     * keys (several backgrounds, say) are all preserved, in first-seen order.</p>
     *
     * @param columns     the result columns; {@code null} yields an empty list
     * @param rows        the result rows; {@code null} yields an empty list
     * @param aliasByRole the column alias expected for each schema role, as produced by the
     *                    query builder. A role absent from the map, or mapped to
     *                    {@code null}, simply goes unmatched — pre-area schemas have no
     *                    geometry or opacity role, and that is not an error.
     * @param warnings    receives a message per unparseable value; may be {@code null}
     * @return the facts, never {@code null}
     */
    public static List<Fact> parse(final List<Column> columns,
                                   final List<Row> rows,
                                   final Map<Role, String> aliasByRole,
                                   final Consumer<String> warnings) {
        if (columns == null) {
            return new ArrayList<>();
        }

        int keyIdx = -1;
        int typeIdx = -1;
        int labelIdx = -1;
        int coordsIdx = -1;
        int imgIdx = -1;
        int worldToMapIdx = -1;
        int geometryIdx = -1;
        int fillIdx = -1;
        int opacityIdx = -1;

        // Aliases are resolved once, before the loop: each lookup is a linear scan of the
        // schema and none of them varies by column, so resolving per column made this
        // O(columns x schema) for no benefit.
        final String typeAlias = alias(aliasByRole, Role.TYPE);
        final String positionAlias = alias(aliasByRole, Role.POSITION);
        final String imageAlias = alias(aliasByRole, Role.IMAGE);
        final String worldToMapAlias = alias(aliasByRole, Role.WORLD_TO_MAP);
        final String geometryAlias = alias(aliasByRole, Role.GEOMETRY);
        final String fillAlias = alias(aliasByRole, Role.FILL);
        final String opacityAlias = alias(aliasByRole, Role.OPACITY);
        final String labelAlias = alias(aliasByRole, Role.LABEL);

        for (int i = 0; i < columns.size(); i++) {
            final String colName = columns.get(i).getName();
            if (colName == null) {
                continue;
            }
            // equalsIgnoreCase is null-safe on its argument, so an unmapped role's null
            // alias simply never matches.
            if (colName.equalsIgnoreCase(KEY_COLUMN)) {
                keyIdx = i;
            } else if (colName.equalsIgnoreCase(typeAlias)) {
                typeIdx = i;
            } else if (colName.equalsIgnoreCase(positionAlias)) {
                coordsIdx = i;
            } else if (colName.equalsIgnoreCase(imageAlias)) {
                imgIdx = i;
            } else if (colName.equalsIgnoreCase(worldToMapAlias)) {
                worldToMapIdx = i;
            } else if (colName.equalsIgnoreCase(labelAlias)) {
                labelIdx = i;
            } else if (colName.equalsIgnoreCase(geometryAlias)) {
                geometryIdx = i;
            } else if (colName.equalsIgnoreCase(fillAlias)) {
                fillIdx = i;
            } else if (colName.equalsIgnoreCase(opacityAlias)) {
                opacityIdx = i;
            }
        }

        final Map<String, Fact> factsByKey = new LinkedHashMap<>();
        if (rows != null) {
            for (final Row row : rows) {
                final List<String> values = row.getValues();
                if (values == null) {
                    continue;
                }
                final String key = valueAt(values, keyIdx);
                // Fact turns a missing type into "", so none is substituted here.
                final String type = valueAt(values, typeIdx);
                final String img = valueAt(values, imgIdx);

                // Every row is a fact placed by its WORLD_TO_MAP matrix — a background is
                // simply an image fact, not a special case.
                double worldX = 0;
                double worldY = 0;
                final double[] xy = parseCoords(valueAt(values, coordsIdx), warnings);
                if (xy != null) {
                    worldX = xy[0];
                    worldY = xy[1];
                }

                factsByKey.put(key, new Fact(
                        key,
                        type,
                        img,
                        parseMatrix(valueAt(values, worldToMapIdx), warnings),
                        new double[]{worldX, worldY},
                        parseVertices(valueAt(values, geometryIdx), warnings),
                        valueAt(values, fillIdx),
                        parseNullableDouble(valueAt(values, opacityIdx)),
                        valueAt(values, labelIdx)));
            }
        }
        return new ArrayList<>(factsByKey.values());
    }

    /**
     * Parses {@code "[x, y]"} into {@code {x, y}}.
     *
     * <p>Square brackets and quotes are optional; values beyond the first two are ignored,
     * which is why this is not
     * {@link XmlValueText#parseCommaSeparatedNumbers(String)}.</p>
     *
     * @return the pair, or {@code null} when absent or unparseable
     */
    static double[] parseCoords(final String str, final Consumer<String> warnings) {
        final String[] parts = split(str);
        if (parts == null) {
            return null;
        }
        try {
            if (parts.length >= 2) {
                return new double[]{
                        Double.parseDouble(parts[0].trim()),
                        Double.parseDouble(parts[1].trim())};
            }
        } catch (final NumberFormatException e) {
            NullSafe.consume("Failed to parse coordinates string: " + str, warnings);
            return null;
        }
        NullSafe.consume("Coordinates string needs two values: " + str, warnings);
        return null;
    }

    /**
     * Parses a flat {@code "[x0, y0, x1, y1, ...]"} geometry into vertex pairs.
     *
     * <p>A trailing odd value is ignored, matching {@link FloorMapEntryParser}.</p>
     *
     * @return the vertices, or {@code null} when absent, unparseable, or fewer than
     *         {@value #MIN_VERTICES} pairs
     */
    static double[][] parseVertices(final String str, final Consumer<String> warnings) {
        final String[] parts = split(str);
        if (parts == null) {
            return null;
        }
        final int count = parts.length / 2;
        if (count < MIN_VERTICES) {
            return null;
        }
        try {
            // Only whole pairs are parsed, so a trailing odd value is never read.
            final double[] flat = new double[count * 2];
            for (int i = 0; i < flat.length; i++) {
                flat[i] = Double.parseDouble(parts[i].trim());
            }
            return FloorMapEntryParser.parseVertices(flat);
        } catch (final NumberFormatException e) {
            NullSafe.consume("Failed to parse geometry string: " + str, warnings);
            return null;
        }
    }

    /**
     * Parses a six-component {@code "[a, b, c, d, e, f]"} matrix.
     *
     * @return the matrix, or {@link FloorMapTransformationMatrix#identity()} when absent or
     *         unparseable — a fact with no usable matrix still has to be placed somewhere,
     *         and identity is the neutral choice
     */
    static FloorMapTransformationMatrix parseMatrix(final String str,
                                                    final Consumer<String> warnings) {
        final String[] parts = split(str);
        if (parts == null) {
            return FloorMapTransformationMatrix.identity();
        }
        try {
            if (parts.length >= MATRIX_COMPONENTS) {
                return new FloorMapTransformationMatrix(
                        Double.parseDouble(parts[0].trim()),
                        Double.parseDouble(parts[1].trim()),
                        Double.parseDouble(parts[2].trim()),
                        Double.parseDouble(parts[3].trim()),
                        Double.parseDouble(parts[4].trim()),
                        Double.parseDouble(parts[5].trim()));
            }
            NullSafe.consume("Matrix string needs " + MATRIX_COMPONENTS + " values: " + str, warnings);
        } catch (final NumberFormatException e) {
            NullSafe.consume("Failed to parse matrix string: " + str, warnings);
        }
        return FloorMapTransformationMatrix.identity();
    }

    /** Parses a single double, or {@code null} for blank or unparseable input. */
    static Double parseNullableDouble(final String str) {
        if (NullSafe.isBlankString(str)) {
            return null;
        }
        try {
            return Double.parseDouble(str.trim());
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    /**
     * Strips the optional {@code []} wrapper and any quotes, then splits on commas.
     *
     * @return the parts, or {@code null} when there is nothing to parse
     */
    private static String[] split(final String str) {
        if (NullSafe.isBlankString(str)) {
            return null;
        }
        return str.replace("[", "")
                .replace("]", "")
                .replace("\"", "")
                .split(",");
    }

    private static String alias(final Map<Role, String> aliasByRole, final Role role) {
        return aliasByRole != null
                ? aliasByRole.get(role)
                : null;
    }

    /** The value at {@code index}, or {@code null} when the column is absent or short. */
    private static String valueAt(final List<String> values, final int index) {
        return index != -1 && values.size() > index
                ? values.get(index)
                : null;
    }
}
