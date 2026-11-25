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

package stroom.query.language.functions;

import stroom.query.language.functions.TimeRounder.Origin;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;

/// Reads parameters that must be constant.
final class ConstantParam {

    private ConstantParam() {
        // Static methods only.
    }

    /// Gets the value of a parameter that is constant, however it was written.
    ///
    /// A literal arrives as a [Val]. A `param('key')` arrives as a [Function] whose generator has been replaced
    /// by a [StaticValueGen] holding the resolved value, which happens before any row is evaluated, so its value
    /// can be read here. Reading it with `param.toString()` would give the text of the function.
    ///
    /// @param param The parameter, or `null`.
    /// @return Its value, or `null` if it has none or it is computed per row.
    static Val getValue(final Param param) {
        if (param instanceof final Val val) {
            return val.type().isValue()
                    ? val
                    : null;
        } else if (param instanceof final Function function
                   && function.createGenerator() instanceof final StaticValueGen staticValueGen) {
            final Val val = staticValueGen.eval(null, null);
            return val.type().isValue()
                    ? val
                    : null;
        }
        return null;
    }

    /// Says why [#getValue(Param)] could not read a parameter, phrased for whoever wrote the query.
    ///
    /// Two failures look identical from the call site and need different answers: a `param('key')` that nobody
    /// supplied a value for, and an argument that genuinely varies per row. Telling a user with an unmapped
    /// parameter that their argument "must be a constant" sends them to fix the wrong thing.
    ///
    /// @param param The parameter.
    /// @return The reason.
    static String whyNotConstant(final Param param) {
        // A field has a constant generator until the fields are known.
        if (!isPerRow(param)
            && param instanceof final Function function
            && function.createGenerator() instanceof final StaticValueGen staticValueGen) {
            return staticValueGen.getValue() instanceof final ValErr valErr
                    ? valErr.getMessage()
                    : "no value was supplied for it";
        }
        return "it must be a literal or a query parameter, not something computed per row";
    }

    /// Gets an optional origin parameter.
    ///
    /// @param param The parameter, or `null`.
    /// @return The origin, or `null` if there is none. Text with no offset is in whichever time zone it is used in.
    /// @throws IllegalArgumentException If the origin is not a constant date.
    static Origin getOrigin(final Param param) {
        if (param == null || param instanceof ValNull || param instanceof Null) {
            return null;
        }
        final Val val = getValue(param);
        if (val == null) {
            throw new IllegalArgumentException("Invalid origin: " + param + " - " + whyNotConstant(param));
        }
        if (val instanceof ValDate
            || (val.type().isNumber() && !(val instanceof ValDuration) && DateReader.isLong(val))) {
            final Instant instant = Instant.ofEpochMilli(val.toLong());
            return zone -> instant.atZone(zone).toLocalDateTime();
        }
        if (val instanceof ValString) {
            final String text = val.toString().trim();
            try {
                if (TimeRounder.isNumber(text)) {
                    final Instant instant = Instant.ofEpochMilli(TimeRounder.parseMillis(text));
                    return zone -> instant.atZone(zone).toLocalDateTime();
                }
                if (DateReader.DATE_PATTERN.matcher(text).matches()) {
                    final LocalDateTime local = LocalDate.parse(text).atStartOfDay();
                    return zone -> local;
                }
                final TemporalAccessor temporal = DateUtil.DEFAULT_ISO_PARSER.parseBest(
                        text, ZonedDateTime::from, LocalDateTime::from);
                if (temporal instanceof final LocalDateTime local) {
                    return zone -> local;
                }
                final Instant instant = Instant.from(temporal);
                return zone -> instant.atZone(zone).toLocalDateTime();
            } catch (final DateTimeException | ArithmeticException e) {
                throw new IllegalArgumentException("Invalid origin: " + e.getMessage(), e);
            }
        }
        throw new IllegalArgumentException("Invalid origin: " + val + " - it must be a date");
    }

    /// Tells whether a parameter is computed per row, as it uses a field or an aggregate. A query parameter is not,
    /// but it has no value yet when an expression is parsed.
    ///
    /// @param param The parameter.
    /// @return True if it is.
    static boolean isPerRow(final Param param) {
        if (param instanceof Ref || (param instanceof final Function function && function.hasAggregate())) {
            return true;
        }
        if (param instanceof final Expression expression) {
            // A named expression of the query.
            return isPerRow(expression.getFunction());
        }
        return param instanceof final AbstractFunction function
               && function.params != null
               && Arrays.stream(function.params).anyMatch(ConstantParam::isPerRow);
    }
}
