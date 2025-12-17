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

package stroom.query.common.v2.format;

import stroom.query.api.NumberFormatSettings;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValDuration;
import stroom.util.shared.NullSafe;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Objects;

public class NumberFormatter implements Formatter {

    private final NumberFormat numberFormat;

    private NumberFormatter(final NumberFormatSettings formatSettings) {
        this.numberFormat = NullSafe.get(formatSettings, NumberFormatter::getNumberFormat);
    }

    public static NumberFormatter create(final NumberFormatSettings formatSettings) {
        return new NumberFormatter(formatSettings);
    }

    @Override
    public String format(final Val value) {
        final String result;
        if (value == null || value.type().isNull()) {
            result = null;
        } else {
            final Number number = value.toNumber();

            if (number != null) {
                if (numberFormat != null) {
                    return numberFormat.format(number);
                } else {
                    return asStringWithNoFormatting(value);
                }
            } else {
                result = "";
            }
        }
        return result;
    }

    private static String asStringWithNoFormatting(final Val val) {
        Objects.requireNonNull(val);
        return switch (val) {
            case final ValDuration valDuration -> String.valueOf(valDuration.toLong());
            case final ValDate valDate -> String.valueOf(valDate.toLong());
            default -> val.toString();
        };
    }

    private static NumberFormat getNumberFormat(final NumberFormatSettings formatSettings) {
        Objects.requireNonNull(formatSettings);
        NumberFormat numberFormat = null;
        numberFormat = DecimalFormat.getNumberInstance();

        final boolean useSeparators = NullSafe.isTrue(formatSettings.getUseSeparator());
        numberFormat.setGroupingUsed(useSeparators);
        final int groupSize = useSeparators
                ? 3
                : 0;
        if (numberFormat instanceof final DecimalFormat decimalFormat) {
            decimalFormat.setGroupingSize(groupSize);
        }

        final int decimalPlaces = Objects.requireNonNullElse(formatSettings.getDecimalPlaces(), 0);
        if (numberFormat instanceof final DecimalFormat decimalFormat) {
            decimalFormat.setMinimumFractionDigits(decimalPlaces);
            decimalFormat.setMaximumFractionDigits(decimalPlaces);
        }
        return numberFormat;
    }
}
