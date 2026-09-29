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
import stroom.floormap.shared.FloorMapDoc;
import stroom.floormap.shared.FloorMapFieldMapping;
import stroom.floormap.shared.FloorMapFieldMapping.Role;
import stroom.floormap.shared.FloorMapTransformationMatrix;
import stroom.util.shared.NullSafe;
import stroom.util.shared.TemporalEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;


/// Shared utility that parses a list of [TemporalEntry]
/// objects into an ordered list of [Fact]s — the authoritative model the
/// canvas renders from. Backgrounds, static facts and events are all just facts.
///
/// Parsing is driven by a list of [FloorMapFieldMapping]s (the value
/// schema). Each mapping's [Role] determines how the extracted value is
/// interpreted. A schema must always be provided; it is stored in the
/// [FloorMapDoc] and configured via the Settings tab.
///
/// Both JSON and XML value formats are supported. The caller supplies
/// an appropriate [ValueAccessor] implementation for parsing and field
/// extraction.
///
/// This class has no GWT dependencies and can be tested with standard
/// JUnit.
public final class FloorMapEntryParser {

    private FloorMapEntryParser() {
        // Utility class
    }

    /// Parses temporal entries into an ordered list of [Fact]s.
    ///
    /// Every entry becomes one fact placed by its `WORLD_TO_MAP` matrix —
    /// backgrounds are not special-cased; a background is simply an entry that
    /// carries an image (and typically a `"background"` type for z-order).
    /// The optional `POSITION` coords are read for every fact (used by the
    /// imageless default-graphic renderer); an image fact usually has none.
    ///
    /// @param entries         the temporal entries to parse; may be `null` or empty
    /// @param schema          the value schema to use; may be `null`
    /// @param accessor        the value accessor for parsing; must not be `null`
    /// @param warningConsumer callback for warning messages (e.g. malformed entries);
    ///         may be `null` to silently ignore warnings
    /// @return the ordered fact list; never `null`
    public static List<Fact> parse(
            final List<TemporalEntry> entries,
            final List<FloorMapFieldMapping> schema,
            final ValueAccessor accessor,
            final Consumer<String> warningConsumer) {
        final List<Fact> facts = new ArrayList<>();

        if (entries == null) {
            return facts;
        }

        // Resolve the path for each role from the schema.
        final String typePath = FloorMapFieldMapping.findPath(schema, Role.TYPE);
        final String labelPath = FloorMapFieldMapping.findPath(schema, Role.LABEL);
        final String positionPath = FloorMapFieldMapping.findPath(schema, Role.POSITION);
        final String imagePath = FloorMapFieldMapping.findPath(schema, Role.IMAGE);
        final String worldToMapPath = FloorMapFieldMapping.findPath(schema, Role.WORLD_TO_MAP);
        final String geometryPath = FloorMapFieldMapping.findPath(schema, Role.GEOMETRY);
        final String fillPath = FloorMapFieldMapping.findPath(schema, Role.FILL);
        final String opacityPath = FloorMapFieldMapping.findPath(schema, Role.OPACITY);

        for (final TemporalEntry entry : entries) {
            try {
                final String valueStr = entry.getValue();
                if (valueStr == null) {
                    NullSafe.consume("Skipping temporal entry with null value (key='"
                            + entry.getKey() + "')", warningConsumer);
                    continue;
                }
                if (!accessor.canParse(valueStr.trim())) {
                    NullSafe.consume("Skipping unparseable temporal entry (key='"
                                    + entry.getKey() + "'): value does not match expected format",
                            warningConsumer);
                    continue;
                }
                final ParsedValue parsed = accessor.parse(valueStr);
                if (parsed == null) {
                    NullSafe.consume("Skipping temporal entry that parsed to null (key='"
                            + entry.getKey() + "')", warningConsumer);
                    continue;
                }

                final String type = typePath != null
                        ? accessor.getString(parsed, typePath)
                        : null;
                final String label = labelPath != null
                        ? accessor.getString(parsed, labelPath)
                        : null;
                final String image = imagePath != null
                        ? accessor.getString(parsed, imagePath)
                        : null;

                // Every fact — background or not — is placed by its WORLD_TO_MAP.
                final FloorMapTransformationMatrix worldToMap =
                        parseMatrix(accessor, parsed, worldToMapPath);

                double[] position = null;
                if (positionPath != null) {
                    final double[] coords = accessor.getArray(parsed, positionPath);
                    if (coords != null && coords.length >= 2) {
                        position = new double[]{coords[0], coords[1]};
                    }
                }

                final double[][] vertices = geometryPath != null
                        ? parseVertices(accessor.getArray(parsed, geometryPath))
                        : null;
                final String fill = fillPath != null
                        ? accessor.getString(parsed, fillPath)
                        : null;
                final Double opacity = opacityPath != null
                        ? accessor.getNumber(parsed, opacityPath)
                        : null;

                facts.add(new Fact(entry.getKey(), type, image, worldToMap, position,
                        vertices, fill, opacity, label));
            } catch (final Exception ex) {
                NullSafe.consume("Skipping malformed temporal entry (key='"
                        + entry.getKey() + "'): " + ex.getMessage(), warningConsumer);
            }
        }

        return facts;
    }

    /// Parses a 6-element transformation matrix from a numeric array at the
    /// given path. Returns [FloorMapTransformationMatrix#identity()] if
    /// the path is null or the array is missing/malformed — an absent matrix
    /// means "place this fact without transforming it", which is a legitimate
    /// state.
    ///
    /// A matrix that *is* present but unusable is a different matter and
    /// is rejected. "Unusable" covers three cases: the value cannot be read as six
    /// numbers at all, there are fewer than six of them, or the six form a
    /// degenerate transform that collapses the fact to a single point. All three
    /// can only be corrupt data, and all three used to be accepted silently — the
    /// degenerate case then went on to quietly mis-transform every coordinate
    /// derived from it, including coordinates the vertex editor writes back to the
    /// document. Throwing means the caller's per-entry handler skips the entry and
    /// reports it, so the user learns which row is bad rather than wondering why an
    /// object sits at the origin.
    ///
    /// The distinction that matters is *present* versus
    /// *absent*, which is why this consults
    /// [ValueAccessor#hasValue] rather than inferring absence from a
    /// `null` array. Data arriving from a stream may legitimately omit the
    /// matrix, and that must stay silent; data that is there but wrong must not.
    ///
    /// @throws IllegalArgumentException if a matrix is present but unusable
    private static FloorMapTransformationMatrix parseMatrix(
            final ValueAccessor accessor, final ParsedValue parsed,
            final String path) {
        if (path == null || !accessor.hasValue(parsed, path)) {
            // Role not mapped, or the row simply does not carry a matrix. Placing the
            // fact untransformed is the correct reading of "no transform supplied".
            return FloorMapTransformationMatrix.identity();
        }
        final double[] arr = accessor.getArray(parsed, path);
        if (arr == null) {
            throw new IllegalArgumentException(
                    "world-to-map matrix at '" + path + "' is present but is not a "
                            + "readable numeric array");
        }
        if (arr.length < 6) {
            throw new IllegalArgumentException(
                    "world-to-map matrix at '" + path + "' needs 6 values but has "
                            + arr.length);
        }
        final FloorMapTransformationMatrix matrix = new FloorMapTransformationMatrix(
                arr[0], arr[1], arr[2], arr[3], arr[4], arr[5]);
        if (!matrix.hasInverse()) {
            throw new IllegalArgumentException(
                    "world-to-map matrix at '" + path + "' is not invertible: " + matrix);
        }
        return matrix;
    }

    /// Folds a flat `{x0, y0, x1, y1, ...}` geometry array into vertex
    /// pairs. Returns `null` for a missing or too-short array (fewer
    /// than 3 vertices). A trailing odd value is ignored.
    ///
    /// Public because the stored form is flat wherever geometry is read — the
    /// properties dialog measures an area's outline from the same array this
    /// parser folds.
    ///
    /// @param flat the stored geometry array; may be `null`
    /// @return the vertex pairs, or `null` if there are fewer than three
    public static double[][] parseVertices(final double[] flat) {
        if (flat == null || flat.length < 6) {
            return null;
        }
        final int count = flat.length / 2;
        final double[][] vertices = new double[count][];
        for (int i = 0; i < count; i++) {
            vertices[i] = new double[]{flat[i * 2], flat[i * 2 + 1]};
        }
        return vertices;
    }
}
