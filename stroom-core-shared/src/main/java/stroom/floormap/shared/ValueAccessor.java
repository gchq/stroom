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

/**
 * Format-independent interface for reading, writing, and serialising
 * floor map entry values.
 *
 * <p>Each {@link ValueFormat} has a corresponding implementation.
 * In the GWT client, implementations use GWT's JSON and XML libraries.
 * In tests, a map-backed mock implementation can be used.</p>
 *
 * <p>Callers obtain an accessor from a factory appropriate to their
 * runtime context and then use the same API regardless of the
 * underlying format.</p>
 *
 * @see ParsedValue
 */
public interface ValueAccessor {

    /**
     * Parses a raw value string into a {@link ParsedValue}.
     *
     * @param raw the raw string (JSON or XML); may be {@code null}
     * @return the parsed value, or {@code null} if the input is
     *         {@code null}, empty, or unparseable
     */
    ParsedValue parse(String raw);

    /**
     * Creates a new empty value suitable for populating with
     * {@link #setString} and {@link #setArray}.
     *
     * <p>For JSON this creates an empty {@code {}}. For XML this
     * creates a document with an empty root element.</p>
     *
     * @param rootName the name of the root element (used by XML;
     *                 ignored by JSON)
     * @return a new, empty {@link ParsedValue}
     */
    ParsedValue createEmpty(String rootName);

    /**
     * Reads a string value at the given path.
     *
     * @param value the parsed value to read from
     * @param path  the format-specific path (e.g. {@code ".type"} for
     *              JSON, {@code "/entry/type"} for XML)
     * @return the string value, or {@code null} if not found
     */
    String getString(ParsedValue value, String path);

    /**
     * Writes a string value at the given path.
     *
     * @param value     the parsed value to write to
     * @param path      the format-specific path
     * @param textValue the string to write; if {@code null}, the
     *                  field is removed
     */
    void setString(ParsedValue value, String path, String textValue);

    /**
     * Reads a numeric array at the given path.
     *
     * <p>For JSON, this expects a {@code JSONArray} of numbers. For
     * XML, this expects comma-separated numbers in the element text
     * content.</p>
     *
     * <p><strong>All or nothing.</strong> If any element is not a number the
     * whole array is malformed and {@code null} is returned. Implementations
     * must not substitute a default for the offending element: callers such as
     * {@code FloorMapEntryParser.parseMatrix} cannot distinguish a fabricated
     * zero from a real one, so a partial parse silently produces valid-looking
     * but wrong geometry.</p>
     *
     * @param value the parsed value to read from
     * @param path  the format-specific path
     * @return the numeric array, or {@code null} if not found or
     *         malformed
     */
    double[] getArray(ParsedValue value, String path);

    /**
     * Whether a value exists at the given path at all, regardless of whether it
     * can be read as any particular type.
     *
     * <p>This exists to separate two states that the typed getters have to
     * conflate. {@link #getArray} returns {@code null} both for "there is
     * nothing here" and for "there is something here but it is not a numeric
     * array", and callers need to treat those very differently: a stream can
     * legitimately omit a field, in which case a sensible default applies
     * silently, whereas a field that is present but unreadable is corrupt data
     * that the user needs to be told about.</p>
     *
     * <p>An explicit JSON {@code null} counts as absent — it is the format's way
     * of saying "no value" — as does an attribute or element that is not there.
     * An empty string or an empty array counts as <em>present</em>: something was
     * written, it just cannot be used.</p>
     *
     * @param value the parsed value to inspect
     * @param path  the format-specific path
     * @return {@code true} if anything is present at {@code path}
     */
    boolean hasValue(ParsedValue value, String path);

    /**
     * Writes a numeric array at the given path.
     *
     * <p>For JSON, this writes a {@code JSONArray} of numbers. For
     * XML, this writes comma-separated numbers as element text
     * content.</p>
     *
     * @param value   the parsed value to write to
     * @param path    the format-specific path
     * @param numbers the numeric array to write
     */
    void setArray(ParsedValue value, String path, double[] numbers);

    /**
     * Reads a scalar numeric value at the given path.
     *
     * <p>For JSON, this expects a number (a numeric string is tolerated).
     * For XML, this expects a number in the element text content.</p>
     *
     * @param value the parsed value to read from
     * @param path  the format-specific path
     * @return the number, or {@code null} if not found or malformed
     */
    Double getNumber(ParsedValue value, String path);

    /**
     * Writes a scalar numeric value at the given path.
     *
     * <p>For JSON, this writes a number. For XML, this writes the number
     * as element text content.</p>
     *
     * @param value  the parsed value to write to
     * @param path   the format-specific path
     * @param number the number to write; if {@code null}, the field is
     *               removed
     */
    void setNumber(ParsedValue value, String path, Double number);

    /**
     * Serialises the parsed value back to a string.
     *
     * @param value the parsed value to serialise
     * @return the serialised string (JSON or XML)
     */
    String serialise(ParsedValue value);

    /**
     * Returns {@code true} if the given raw string looks like it
     * could be parsed by this accessor. Used for quick format
     * detection (e.g. starts with {@code "{"} for JSON or
     * {@code "<"} for XML).
     * @param raw the raw value string
     * @return {@code true} if this accessor can likely parse it
     */
    boolean canParse(String raw);
}
