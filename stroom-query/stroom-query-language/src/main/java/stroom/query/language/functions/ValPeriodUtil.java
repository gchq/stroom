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

import stroom.query.language.token.TimeLiteral;
import stroom.util.shared.NullSafe;
import stroom.util.shared.time.SimpleDuration;

import java.time.DateTimeException;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/// Reads and writes periods as text.
final class ValPeriodUtil {

    private ValPeriodUtil() {
        // Static methods only.
    }

    /// Gets a period from a value.
    ///
    /// @param val The value.
    /// @return A period, or an error if the value is not a period or the text of one. A null or an error is
    /// returned as it is and blank text gives null.
    static Val parsePeriod(final Val val) {
        return switch (val) {
            case final ValPeriod valPeriod -> valPeriod;
            case final ValString valString -> parsePeriod(valString.toString());
            default -> val.type().isValue()
                    ? ValErr.create("A " + val.type().getName() + " can't be read as a period")
                    : val;
        };
    }

    private static Val parsePeriod(final String text) {
        if (NullSafe.isBlankString(text)) {
            return ValNull.INSTANCE;
        }
        try {
            return ValPeriod.create(parse(text.trim()));
        } catch (final DateTimeException | ArithmeticException e) {
            return ValErr.create(e.getMessage());
        }
    }

    private static Period parse(final String text) {
        final String upper = text.toUpperCase(Locale.ROOT);
        if (upper.startsWith("P") || upper.startsWith("-P") || upper.startsWith("+P")) {
            if (upper.contains("T")) {
                throw new DateTimeException("'" + text + "' has hours, minutes or seconds so is not a period. "
                                            + "Use parseDuration for durations");
            }
            try {
                return Period.parse(text);
            } catch (final DateTimeParseException e) {
                throw new DateTimeException("'" + text + "' is not a period, such as P1Y2M3D or 1y2M3d", e);
            }
        }

        Period period = Period.ZERO;
        for (final SimpleDuration part : TimeLiteral.parseCompound(text)) {
            if (!TimeLiteral.isPeriod(part)) {
                throw new DateTimeException("'" + part + "' in '" + text + "' is not a period. Use parseDuration "
                                            + "for durations");
            }
            period = period.plus(TimeLiteral.toPeriod(part));
        }
        return period;
    }

    /// Formats a period as ISO 8601 text.
    ///
    /// @param val A period, or the text of one.
    /// @return The text, such as `P1Y2M`, or an error if the value is not a period or the text of one. A null
    /// or an error is returned as it is and blank text gives null.
    static Val formatPeriod(final Val val) {
        final Val period = parsePeriod(val);
        return period instanceof ValPeriod
                ? ValString.create(period.toString())
                : period;
    }
}
