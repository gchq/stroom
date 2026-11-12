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

package stroom.externaldatasource.impl;

import stroom.externaldatasource.shared.DataColumnType;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;

import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.util.StaticUtils;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/// Reads the values of LDAP attributes as the types of an LDAP External Data Source's attributes table.
///
/// - Text: every value, joined by `"; "`, as an attribute may have several, such as `memberOf`.
/// - Long, Double and Boolean: as for CSV, with Boolean taking LDAP's `TRUE` and `FALSE`.
/// - Date: GeneralizedTime, the syntax of LDAP times such as `modifyTimestamp`, or Active Directory's
///   FILETIME integers, such as `lastLogonTimestamp`; failing those, ISO 8601 as for CSV.
///
/// A typed attribute with more than one value is not of its type, as the values cannot be joined.
/// Binary values, such as `objectGUID` or `jpegPhoto`, are written in base64; they are only read as text.
final class LdapValues {

    /// The separator between the values of an attribute with several.
    static final String VALUE_SEPARATOR = "; ";

    // Active Directory FILETIME: 100 nanosecond intervals since 1601-01-01, written as digits.
    private static final Pattern FILETIME = Pattern.compile("^\\d{15,19}$");
    private static final long FILETIME_TICKS_PER_MILLI = 10_000L;
    private static final long FILETIME_EPOCH_OFFSET_MILLIS = 11_644_473_600_000L;
    // Active Directory writes 0 for never and Long.MAX_VALUE for no expiry; both mean no time.
    private static final String FILETIME_NEVER = "0";
    private static final String FILETIME_NO_EXPIRY = Long.toString(Long.MAX_VALUE);

    private LdapValues() {
    }

    /// Reads an attribute as its column's type.
    ///
    /// @param attribute The attribute, or `null` if the entry does not have it.
    /// @param type      The column's type, or `null` for [DataColumnType#TEXT].
    /// @return The value. A missing attribute is [ValNull].
    /// @throws IllegalArgumentException If the value is not of the column's type.
    static Val read(final Attribute attribute, final DataColumnType type) {
        if (attribute == null || !attribute.hasValue()) {
            return ValNull.INSTANCE;
        }
        final DataColumnType columnType = Objects.requireNonNullElse(type, DataColumnType.TEXT);
        final List<String> values = texts(attribute);
        if (columnType == DataColumnType.TEXT) {
            return ValString.create(String.join(VALUE_SEPARATOR, values));
        }
        if (isBinary(attribute)) {
            throw new IllegalArgumentException("Binary attribute " + attribute.getName()
                                               + " can only be read as text.");
        }
        if (values.size() > 1) {
            throw new IllegalArgumentException("Attribute " + attribute.getName() + " has " + values.size()
                                               + " values, so it can only be read as text.");
        }
        final String value = values.getFirst();
        if (columnType == DataColumnType.DATE) {
            return readDate(value);
        }
        return DataValues.read(value, columnType);
    }

    /// The values of an attribute as text, binary values in base64.
    ///
    /// @param attribute An attribute.
    /// @return Its values, in the order the directory gave them.
    static List<String> texts(final Attribute attribute) {
        if (isBinary(attribute)) {
            final List<String> values = new ArrayList<>();
            for (final byte[] bytes : attribute.getValueByteArrays()) {
                values.add(Base64.getEncoder().encodeToString(bytes));
            }
            return values;
        }
        return List.of(attribute.getValues());
    }

    /// Whether an attribute holds binary values: it has the `;binary` option, or a value that is not
    /// UTF-8 text.
    ///
    /// @param attribute An attribute.
    /// @return True if its values are binary.
    static boolean isBinary(final Attribute attribute) {
        if (attribute.hasOption("binary")) {
            return true;
        }
        for (final byte[] bytes : attribute.getValueByteArrays()) {
            if (!StaticUtils.isValidUTF8(bytes) || hasControlCharacter(bytes)) {
                return true;
            }
        }
        return false;
    }

    /// Guesses the type of an attribute from its values in some entries, for Detect Attributes.
    ///
    /// @param attributes The attribute as found in each entry that has it.
    /// @return Date if every value is GeneralizedTime; Text if any entry has several values; otherwise
    ///         what [DataValues#guessType(Collection)] makes of the values.
    static DataColumnType guessType(final Collection<Attribute> attributes) {
        final List<String> values = new ArrayList<>();
        for (final Attribute attribute : attributes) {
            if (attribute.size() > 1) {
                return DataColumnType.TEXT;
            }
            values.addAll(texts(attribute));
        }
        if (!values.isEmpty() && values.stream().allMatch(LdapValues::isGeneralizedTime)) {
            return DataColumnType.DATE;
        }
        return DataValues.guessType(values);
    }

    private static Val readDate(final String value) {
        final String trimmed = value.trim();
        if (isGeneralizedTime(trimmed)) {
            try {
                return ValDate.create(StaticUtils.decodeGeneralizedTime(trimmed).getTime());
            } catch (final ParseException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }
        if (FILETIME_NEVER.equals(trimmed) || FILETIME_NO_EXPIRY.equals(trimmed)) {
            return ValNull.INSTANCE;
        }
        if (FILETIME.matcher(trimmed).matches()) {
            final long ticks = Long.parseLong(trimmed);
            return ValDate.create(ticks / FILETIME_TICKS_PER_MILLI - FILETIME_EPOCH_OFFSET_MILLIS);
        }
        return DataValues.read(trimmed, DataColumnType.DATE);
    }

    // LDAP's GeneralizedTime (RFC 4517) starts with at least ten digits, yyyyMMddHH, and always ends
    // with Z or an offset, so a plain number, such as a FILETIME, is never taken for one.
    private static boolean isGeneralizedTime(final String value) {
        final String upper = value.trim().toUpperCase(Locale.ROOT);
        if (upper.length() < 11 || !upper.substring(0, 10).chars().allMatch(Character::isDigit)) {
            return false;
        }
        final String zone = upper.substring(10);
        if (!zone.endsWith("Z") && !zone.contains("+") && !zone.contains("-")) {
            return false;
        }
        try {
            StaticUtils.decodeGeneralizedTime(upper);
            return true;
        } catch (final ParseException e) {
            return false;
        }
    }

    // Text values hold no control characters other than tab, line feed and carriage return.
    private static boolean hasControlCharacter(final byte[] bytes) {
        for (final byte b : bytes) {
            if (b >= 0 && b < 0x20 && b != '\t' && b != '\n' && b != '\r') {
                return true;
            }
        }
        return false;
    }
}
