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

package stroom.dataconnection.impl;

import stroom.query.api.DateTimeSettings;
import stroom.query.api.Param;
import stroom.query.api.TimeRange;
import stroom.util.shared.NullSafe;

import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.util.StaticUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/// The LDAP search filter of a query, with its named placeholders found.
///
/// A placeholder is written as in SQL, `:name`, and takes the value of the query parameter of that
/// name; `:from` and `:to` take the ends of the query's time range, as GeneralizedTime unless a format
/// follows in braces, as in `:from{epochMillis}`. Each value is escaped as an LDAP filter value
/// (RFC 4515), so it can only ever be matched, never change the filter.
///
/// A colon also appears in extensible match filters, such as `(cn:caseExactMatch:=Fred)`, and in values
/// such as times and URLs. So a colon only starts a placeholder where a value starts or continues
/// after a wildcard: straight after `=`, `~=`, `>=`, `<=` or `*`. Elsewhere it is part of the filter.
/// A value that must start with a colon followed by a letter writes the colon as `\3a`.
///
/// A value is escaped for the filter, not for a DN. So in a DN-valued assertion such as
/// `(member=uid=:user,ou=People,dc=example,dc=org)` a parameter holding a comma could compare a
/// different DN, though never change the filter. Give the whole DN as one parameter instead, as in
/// `(member=:memberDn)`.
///
/// @param filter       The filter as the user wrote it, wrapped in brackets if they left them off.
/// @param parts        The filter in order: literal text as [String] and placeholders as
///                     [SqlPlaceholder].
/// @param placeholders The placeholders in the order they appear.
record LdapFilterTemplate(String filter, List<Object> parts, List<SqlPlaceholder> placeholders) {

    static final String NO_FILTER = "The query has no LDAP filter. Give one in a sql clause, as in "
                                    + "sql \"\"\"(objectClass=person)\"\"\".";

    /// Copies the lists, so the record cannot change.
    LdapFilterTemplate {
        parts = List.copyOf(parts);
        placeholders = List.copyOf(placeholders);
    }

    /// Finds the placeholders in a filter.
    ///
    /// @param filter The filter as the user wrote it.
    /// @return The filter with its placeholders found.
    /// @throws IllegalArgumentException If the filter is blank or a placeholder's format is not closed.
    static LdapFilterTemplate parse(final String filter) {
        if (NullSafe.isBlankString(filter)) {
            throw new IllegalArgumentException(NO_FILTER);
        }
        final String trimmed = filter.trim();
        final String bracketed = trimmed.startsWith("(")
                ? trimmed
                : "(" + trimmed + ")";
        final List<Object> parts = new ArrayList<>();
        final List<SqlPlaceholder> placeholders = new ArrayList<>();
        final StringBuilder literal = new StringBuilder();
        int index = 0;
        while (index < bracketed.length()) {
            final char c = bracketed.charAt(index);
            if (c == ':' && startsValue(bracketed, index) && isNameStart(bracketed, index + 1)) {
                int end = index + 1;
                while (end < bracketed.length() && isNamePart(bracketed.charAt(end))) {
                    end++;
                }
                final String name = bracketed.substring(index + 1, end);
                String format = null;
                if (end < bracketed.length() && bracketed.charAt(end) == '{') {
                    final int close = bracketed.indexOf('}', end);
                    if (close < 0) {
                        throw new IllegalArgumentException("The format of placeholder :" + name
                                                           + " has no closing }.");
                    }
                    format = bracketed.substring(end + 1, close);
                    end = close + 1;
                }
                if (!literal.isEmpty()) {
                    parts.add(literal.toString());
                    literal.setLength(0);
                }
                final SqlPlaceholder placeholder = new SqlPlaceholder(name, format);
                parts.add(placeholder);
                placeholders.add(placeholder);
                index = end;
            } else {
                literal.append(c);
                index++;
            }
        }
        if (!literal.isEmpty()) {
            parts.add(literal.toString());
        }
        return new LdapFilterTemplate(bracketed, parts, placeholders);
    }

    /// @return True if the filter takes the query's time range through `:from` or `:to`.
    boolean usesTimeRange() {
        return placeholders.stream().anyMatch(SqlPlaceholder::isTimeRange);
    }

    /// Puts each placeholder's value into the filter and checks the result is a valid filter.
    ///
    /// @param timeRange        The query's time range, or `null`.
    /// @param params           The query's parameters, or `null`.
    /// @param dateTimeSettings Settings for reading relative times such as `now() - 1d`.
    /// @return The filter, ready to search with.
    /// @throws IllegalArgumentException If a placeholder has no value, or the filter is not valid.
    Filter resolve(final TimeRange timeRange,
                   final List<Param> params,
                   final DateTimeSettings dateTimeSettings) {
        // The values are worked out as they are for SQL, so parameters and time formats behave the same.
        final List<Object> values = SqlParameterBinder.resolveValues(
                new ParsedSql(filter, filter, placeholders),
                timeRange,
                params,
                dateTimeSettings);
        final StringBuilder sb = new StringBuilder();
        int valueIndex = 0;
        for (final Object part : parts) {
            if (part instanceof final String text) {
                sb.append(text);
            } else {
                sb.append(encode(values.get(valueIndex)));
                valueIndex++;
            }
        }
        return create(sb.toString());
    }

    /// Checks a filter with no placeholder values, as when the fields of a query are listed.
    ///
    /// @throws IllegalArgumentException If the filter is not valid with a value in each placeholder.
    void check() {
        final StringBuilder sb = new StringBuilder();
        for (final Object part : parts) {
            sb.append(part instanceof final String text
                    ? text
                    : "x");
        }
        create(sb.toString());
    }

    // An Instant is written as GeneralizedTime, the syntax of LDAP times such as modifyTimestamp.
    private static String encode(final Object value) {
        return switch (value) {
            case final Instant instant -> StaticUtils.encodeGeneralizedTime(Date.from(instant));
            case final Long number -> number.toString();
            case final String text -> Filter.encodeValue(text);
            default -> throw new IllegalStateException("Unexpected placeholder value type " + value.getClass());
        };
    }

    private static Filter create(final String filter) {
        try {
            return Filter.create(filter);
        } catch (final LDAPException e) {
            throw new IllegalArgumentException("The LDAP filter is not valid: " + e.getMessage(), e);
        }
    }

    // A value starts after '=' (which ends '~=', '>=' and '<=' too) and continues after a '*'.
    private static boolean startsValue(final String filter, final int colonIndex) {
        if (colonIndex == 0) {
            return false;
        }
        final char previous = filter.charAt(colonIndex - 1);
        return previous == '=' || previous == '*';
    }

    private static boolean isNameStart(final String filter, final int index) {
        if (index >= filter.length()) {
            return false;
        }
        final char c = filter.charAt(index);
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isNamePart(final char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
