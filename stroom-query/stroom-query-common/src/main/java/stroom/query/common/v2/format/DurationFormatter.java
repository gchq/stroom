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

package stroom.query.common.v2.format;

import stroom.query.api.DurationFormatSettings;
import stroom.query.api.DurationPattern;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDuration;
import stroom.query.language.functions.ValDurationUtil;

import java.util.function.UnaryOperator;

/// Shows values as durations. A number is milliseconds, and text can be of a duration. A value that is not a duration
/// is shown as it is.
public class DurationFormatter implements Formatter {

    private final UnaryOperator<Val> formatter;

    private DurationFormatter(final UnaryOperator<Val> formatter) {
        this.formatter = formatter;
    }

    /// Creates a formatter.
    ///
    /// @param settings How to show durations, or null to show them exactly.
    /// @return The formatter.
    public static DurationFormatter create(final DurationFormatSettings settings) {
        if (settings == null) {
            return new DurationFormatter(UnaryOperator.identity());
        }
        final UnaryOperator<Val> formatter = switch (settings.getStyle()) {
            case EXACT -> UnaryOperator.identity();
            case ISO -> ValDurationUtil::formatISODuration;
            case PATTERN -> createPatternFormatter(settings.getPattern());
        };
        return new DurationFormatter(formatter);
    }

    private static UnaryOperator<Val> createPatternFormatter(final String pattern) {
        final DurationPattern durationPattern;
        try {
            durationPattern = DurationPattern.parse(pattern);
        } catch (final IllegalArgumentException e) {
            // The dialog checks patterns, but a request may not have come from it. The durations are still of use.
            return UnaryOperator.identity();
        }
        return duration -> ValDurationUtil.formatDuration(duration, durationPattern);
    }

    @Override
    public String format(final Val value) {
        if (value == null) {
            return null;
        }
        final Val duration = ValDurationUtil.toDuration(value);
        return duration instanceof ValDuration
                ? formatter.apply(duration).toString()
                : value.toString();
    }
}
