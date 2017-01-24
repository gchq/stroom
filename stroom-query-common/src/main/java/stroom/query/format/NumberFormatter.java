/*
 * Copyright 2016 Crown Copyright
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

package stroom.query.format;

import stroom.dashboard.expression.TypeConverter;
import stroom.query.api.NumberFormat;

public class NumberFormatter implements Formatter {
    private final NumberFormat numberFormat;

    private NumberFormatter(final NumberFormat numberFormat) {
        this.numberFormat = numberFormat;
    }

    public static NumberFormatter create(final NumberFormat numberFormat) {
        return new NumberFormatter(numberFormat);
    }

    @Override
    public String format(final Object value) {
        if (value == null) {
            return null;
        }

        final Double dbl = TypeConverter.getDouble(value);
        if (dbl != null) {
            final String string = TypeConverter.doubleToString(dbl);

            if (numberFormat != null) {
                final int index = string.indexOf(".");

                String p1 = string;
                String p2 = "";
                if (index != -1) {
                    p1 = string.substring(0, index);
                    p2 = string.substring(index + 1);
                }

                // Add separator chars to i part.
                if (numberFormat.getUseSeparator()) {
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
                if (p2.length() > numberFormat.getDecimalPlaces()) {
                    p2 = p2.substring(0, numberFormat.getDecimalPlaces());
                } else if (p2.length() < numberFormat.getDecimalPlaces()) {
                    final StringBuilder sb = new StringBuilder();
                    sb.append(p2);
                    for (int i = 0; i < numberFormat.getDecimalPlaces() - p2.length(); i++) {
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
