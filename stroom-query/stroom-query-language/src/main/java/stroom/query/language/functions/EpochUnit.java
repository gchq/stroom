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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;

/// A unit that the time since the epoch is counted in.
enum EpochUnit {
    SECOND("s", 3),
    MILLISECOND("ms", 0),
    MICROSECOND("us", -3),
    NANOSECOND("ns", -6);

    private final String name;
    // The power of ten that turns this unit into milliseconds.
    private final int scale;

    EpochUnit(final String name, final int scale) {
        this.name = name;
        this.scale = scale;
    }

    /// Finds a unit by its name.
    ///
    /// @param name The name, such as `ms`.
    /// @return The unit.
    /// @throws DateTimeException If there is no unit with that name.
    static EpochUnit fromName(final String name) {
        for (final EpochUnit unit : values()) {
            if (unit.name.equals(name)) {
                return unit;
            }
        }
        throw new DateTimeException("Unknown unit '" + name + "'. Use one of s, ms, us, ns");
    }

    /// Turns an amount of this unit into milliseconds. Any part of a millisecond is dropped.
    ///
    /// @param amount The amount.
    /// @return The milliseconds.
    /// @throws ArithmeticException If there are too many.
    long toMillis(final BigDecimal amount) {
        return amount.scaleByPowerOfTen(scale).setScale(0, RoundingMode.FLOOR).longValueExact();
    }

    /// Turns milliseconds into this unit. Any part of a unit is dropped.
    ///
    /// @param millis The milliseconds.
    /// @return The amount of this unit.
    /// @throws ArithmeticException If there are too many.
    long fromMillis(final long millis) {
        return BigDecimal.valueOf(millis).scaleByPowerOfTen(-scale).setScale(0, RoundingMode.FLOOR).longValueExact();
    }
}
