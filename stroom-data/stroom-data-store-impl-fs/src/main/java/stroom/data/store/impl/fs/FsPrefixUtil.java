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

package stroom.data.store.impl.fs;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import java.nio.file.Path;

/**
 * <p>
 * Utility to get prefix sequences.
 * </p>
 */
public final class FsPrefixUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsPrefixUtil.class);

    private static final String START_PREFIX = "000";
    private static final int PAD_SIZE = 3;

    private FsPrefixUtil() {
        // Private constructor.
    }

    /**
     * Pad a prefix.
     */
    public static String padId(final Long current) {
        if (current == null) {
            return START_PREFIX;
        } else {
            String output = Long.toString(current);
            final int remainder = output.length() % PAD_SIZE;
            output = switch (remainder) {
                case 0 -> output;
                case 1 -> "00" + output;
                case 2 -> "0" + output;
                default -> throw new IllegalStateException("Unexpected value: " + remainder);
            };
            return output;
        }
    }

    /**
     * Remove padding from the string, e.g. '000099' => 99
     *
     * @return The de-padded value, 0 if blank/null or -1 if not a number.
     */
    public static long dePadId(final String paddedId) {
        if (NullSafe.isBlankString(paddedId)) {
            return -1L;
        } else {
            final int len = paddedId.length();
            int startIdx = 0;
            while (startIdx < len) {
                if (paddedId.charAt(startIdx) == '0') {
                    startIdx++;
                } else {
                    break;
                }
            }
            final String dePaddedId = paddedId.substring(startIdx);
            if (dePaddedId.isBlank()) {
                return 0L;
            } else {
                try {
                    return Long.parseLong(dePaddedId);
                } catch (final NumberFormatException e) {
                    LOGGER.debug("Unable to convert '{}' to a long", dePaddedId, e);
                    return -1;
                }
            }
        }
    }

    /**
     * Given a string chop it up into parts and append to a path.
     */
    static Path appendIdPath(final Path path, final long id) {
        return appendIdPath(path, FsPrefixUtil.padId(id));
    }

    /**
     * Given a string chop it up into parts and append to a path.
     */
    public static Path appendIdPath(final Path path, final String id) {
        Path result = path;
        for (int i = 0; i < id.length() - PAD_SIZE; i += PAD_SIZE) {
            final String part = id.substring(i, i + PAD_SIZE);
            result = result.resolve(part);
        }
        return result;
    }
}
