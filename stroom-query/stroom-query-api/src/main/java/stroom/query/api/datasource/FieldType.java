/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.query.api.datasource;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PrimitiveValueConverter;
import stroom.util.shared.string.CIKey;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("TextBlockMigration") // Cos GWT
public enum FieldType implements HasDisplayValue, HasPrimitiveValue {
    ID(0,
            CIKey.internStaticKey("Id"),
            "id", "ID field type\n" +
                  "\n" +
                  "Represents the numeric identifier of a record or other Stroom entity.",
            true),
    BOOLEAN(1,
            CIKey.internStaticKey("Boolean"),
            "bool",
            "Boolean field type\n" +
            "\n" +
            "Accepts either 'true' or 'false' values.",
            false),
    INTEGER(2,
            CIKey.internStaticKey("Integer"),
            "int",
            "Integer field type\n" +
            "\n" +
            "Non-fractional numeric value supporting equality and range queries.",
            true),
    LONG(3,
            CIKey.internStaticKey("Long"),
            "long",
            "Long field type\n" +
            "\n" +
            "Non-fractional numeric value supporting equality and range queries.",
            true),
    FLOAT(4,
            CIKey.internStaticKey("Float"),
            "float",
            "Floating-point field type\n" +
            "\n" +
            "Decimal value supporting equality and range queries.",
            true),
    DOUBLE(5,
            CIKey.internStaticKey("Double"),
            "double",
            "Double-precision floating point field type\n" +
            "\n" +
            "Decimal value supporting equality and range queries.",
            true),
    // Dates are held in string form, so need to be parsed
    DATE(6,
            CIKey.internStaticKey("Date"),
            "date",
            "Date field type\n" +
            "\n" +
            "Accepts a text-based date, in ISO8601 date/time format: yyyy-MM-ddTHH:mm:ss[.SSS][Z].\n" +
            "Relative values are supported, including: now(), year(), month(), day().\n" +
            "\n" +
            "Examples (omit quotes):\n" +
            " * Current time plus 2 days: 'now() + 2d'\n" +
            " * Current time minus 1 hour: 'now() - 1h'\n" +
            " * Current time plus 2 weeks, minus 1 day 10 hours: 'now() + 2w - 1d10h'",
            false),
    TEXT(7,
            CIKey.internStaticKey("Text"),
            "text",
            "Text field type\n" +
            "\n" +
            " * Full-text search: matches against any of the provided search terms\n" +
            " * Case insensitive\n" +
            " * Typically ignores whitespace and punctuation (incl. hyphens, commas, periods and special " +
            "characters)\n" +
            "\n" +
            "Examples (omit single quotes):\n" +
            " * Match one or more terms in any order: 'the cat sat on the mat'\n" +
            " * Match an exact phrase (use double quotes): \"the cat sat\"",
            false),
    KEYWORD(8,
            CIKey.internStaticKey("Keyword"),
            "keyword",
            "Keyword field type\n" +
            "\n" +
            " * Supports exact matches, wildcards (*, ?) and dictionary lookups\n" +
            " * Case and whitespace sensitive\n" +
            "\n" +
            "Examples (omit quotes):\n" +
            " * Exact match: 'Joe.Bloggs1' or '12345'\n" +
            " * Starts with: 'the quick brown *'\n" +
            " * Ends with: '* lazy dog'\n" +
            " * Contains: '*cat sat*'\n" +
            " * Substitute a single character: 'Joe.?loggs1'",
            false),
    IPV4_ADDRESS(9,
            CIKey.internStaticKey("IpV4Address"),
            "ip",
            "IPv4 address field type\n" +
            "\n" +
            "Supports equality or range queries.\n" +
            "\n" +
            "Examples (omit quotes):\n" +
            " * Exact match: '192.168.1.2'\n" +
            " * CIDR comparison: '192.168.1.0/24'",
            true),
    DOC_REF(10,
            CIKey.internStaticKey("DocRef"),
            "docRef",
            "Document reference field type\n" +
            "\n" +
            "This is a reference to a Stroom object such as a Dictionary.\n" +
            "Click in the selection box to select the desired object.",
            false),
    USER_REF(11,
            CIKey.internStaticKey("UserRef"),
            "userRef",
            "User reference field type\n" +
            "\n" +
            "This is a reference to a Stroom user.\n" +
            "Click in the selection box to select the desired user.",
            false),
    DENSE_VECTOR(12,
                 CIKey.internStaticKey("DenseVector"),
            "denseVector",
            "Dense vector embedding field type\n" +
            "\n" +
            "Supports vector search using algorithms such as k nearest neighbour.\n" +
            "Documents are considered if they are semantically relevant to your search query.\n" +
            "\n" +
            "Examples (omit quotes):\n" +
            " * 'messages relating to Blockchain technology'\n" +
            " * 'recreational activity'\n" +
            " * 'medical facilities'",
            false);

    public static final List<FieldType> TYPES = Arrays.stream(values())
            .collect(Collectors.toList());

    private static final Map<CIKey, FieldType> TYPE_NAME_TO_FIELD_MAP = Arrays.stream(values())
            .collect(Collectors.toMap(
                    fieldType -> fieldType.typeName,
                    Function.identity()));

    private static final PrimitiveValueConverter<FieldType> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(FieldType.class, values());

    public static FieldType fromTypeId(final byte typeId) {
        return PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(typeId);
    }

    public static FieldType fromTypeId(final int typeId) {
        return fromTypeId((byte) typeId);
    }

    /**
     * Get the {@link FieldType} using its name/displayValue (same thing) in
     * a case-insensitive way.
     *
     * @param displayValue The display name AKA type name.
     * @return The corresponding {@link FieldType} or null if not known.
     */
    public static FieldType fromDisplayValue(final String displayValue) {
        return fromTypeName(displayValue);
    }

    /**
     * Get the {@link FieldType} using its name/displayValue (same thing) in
     * a case-insensitive way.
     *
     * @param typeName The type name AKA display value.
     * @return The corresponding {@link FieldType} or null if not known.
     */
    public static FieldType fromTypeName(final String typeName) {
        return NullSafe.get(
                typeName,
                CIKey::of,
                TYPE_NAME_TO_FIELD_MAP::get);
    }

    private final int typeId;
    private final CIKey typeName;
    private final String shortTypeName;
    private final String description;
    private final boolean numeric;

    FieldType(final int typeId,
              final CIKey typeName,
              final String shortTypeName,
              final String description,
              final boolean numeric) {
        this.typeId = typeId;
        this.typeName = Objects.requireNonNull(typeName);
        this.shortTypeName = shortTypeName;
        this.description = description;
        this.numeric = numeric;
    }

    /**
     * Same as {@link FieldType#getPrimitiveValue()} except not cast to a byte.
     */
    public int getTypeId() {
        return typeId;
    }

    /**
     * Same as {@link FieldType#getTypeId()} except cast to a byte.
     */
    @Override
    public byte getPrimitiveValue() {
        return (byte) typeId;
    }

    /**
     * Same as {@link FieldType#getDisplayValue()}
     *
     * @return The type name, AKA the display value.
     */
    public String getTypeName() {
        return typeName.get();
    }

    public String getShortTypeName() {
        return shortTypeName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Same as {@link FieldType#getTypeName()}
     *
     * @return The type name, AKA the display value.
     */
    @Override
    public String getDisplayValue() {
        return getTypeName();
    }

    public boolean isNumeric() {
        return numeric;
    }
}
