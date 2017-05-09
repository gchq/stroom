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

package stroom.util.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PropertyUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyUtil.class);

    public static int toInt(final String string, final int defaultValue) {
        int value = defaultValue;

        if (string != null && string.length() > 0) {
            try {
                value = Integer.parseInt(string);
            } catch (final NumberFormatException e) {
                LOGGER.error("Unable to parse value '" + string + "', using default of '"
                        + defaultValue + "' instead", e);
            }
        }

        return value;
    }

    public static long toLong(final String string, final long defaultValue) {
        long value = defaultValue;

        if (string != null && string.length() > 0) {
            try {
                value = Long.parseLong(string);
            } catch (final NumberFormatException e) {
                LOGGER.error("Unable to parse value '" + string + "', using default of '"
                        + defaultValue + "' instead", e);
            }
        }

        return value;
    }

    public static boolean toBoolean(final String string, final boolean defaultValue) {
        boolean value = defaultValue;

        if (string != null && string.length() > 0) {
            if (string.equalsIgnoreCase("TRUE")) {
                value = true;
            } else if (string.equalsIgnoreCase("FALSE")) {
                value = false;
            } else {
                try {
                    throw new NumberFormatException();
                } catch (final NumberFormatException e) {
                    LOGGER.error("Unable to parse value '" + string + "', using default of '"
                            + defaultValue + "' instead", e);
                }
            }
        }

        return value;
    }
}
