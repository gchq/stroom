/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.format;

import stroom.dashboard.expression.v1.TypeConverter;
import stroom.dashboard.shared.FormatSettings;
import stroom.dashboard.shared.NumberFormatSettings;

public class NumberFormatter implements Formatter {
    private final NumberFormatSettings numberFormatSettings;

    private NumberFormatter(final NumberFormatSettings numberFormatSettings) {
        this.numberFormatSettings = numberFormatSettings;
    }

    public static NumberFormatter create(final FormatSettings settings) {
        NumberFormatSettings numberFormatSettings = null;
        if (settings != null && settings instanceof NumberFormatSettings) {
            numberFormatSettings = (NumberFormatSettings) settings;
        }
        return new NumberFormatter(numberFormatSettings);
    }

    @Override
    public String format(final Object value) {
        if (value == null) {
            return null;
        }

        final Double dbl = TypeConverter.getDouble(value);
        if (dbl != null) {
            final String string = TypeConverter.doubleToString(dbl);

            if (numberFormatSettings != null) {
                final int index = string.indexOf(".");

                String p1 = string;
                String p2 = "";
                if (index != -1) {
                    p1 = string.substring(0, index);
                    p2 = string.substring(index + 1);
                }

                // Add separator chars to i part.
                if (numberFormatSettings.getUseSeparator()) {
                    final StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < p1.length(); i++) {
                        sb.append(p1.charAt(i));

                        final int pos = p1.length() - i - 1;
                        if (pos > 0 && pos % 3 == 0) {
                            sb.append(",");
                        }
                    }
                    p1 = sb.toString();
                }

                // Trim or pad decimal part.
                if (p2.length() > numberFormatSettings.getDecimalPlaces()) {
                    p2 = p2.substring(0, numberFormatSettings.getDecimalPlaces());
                } else if (p2.length() < numberFormatSettings.getDecimalPlaces()) {
                    final StringBuilder sb = new StringBuilder();
                    sb.append(p2);
                    for (int i = 0; i < numberFormatSettings.getDecimalPlaces() - p2.length(); i++) {
                        sb.append('0');
                    }
                    p2 = sb.toString();
                }

                if (p2.length() == 0) {
                    return p1;
                }

                return p1 + "." + p2;
            }

            return string;
        }
        return value.toString();
    }
}
