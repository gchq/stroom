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

package stroom.data.store.impl.fs;

/**
 * <p>
 * Utility to get prefix sequences.
 * </p>
 */
final class FileSystemPrefixUtil {
    private static final String START_PREFIX = "000";
    private static final int PAD_SIZE = 3;

    private FileSystemPrefixUtil() {
        // Private constructor.
    }

    /**
     * <p>
     * Pad a prefix.
     * </p>
     */
    public static String padId(final Long current) {
        if (current == null) {
            return START_PREFIX;
        }
        StringBuilder buffer = new StringBuilder();
        buffer.append(current);

        while ((buffer.length() % PAD_SIZE) != 0) {
            buffer.insert(0, "0");
        }
        return buffer.toString();

    }

    /**
     * Given a string chop it up into 3 parts separately by '/'.
     */
    public static String buildIdPath(final String id) {
        if (id.length() == PAD_SIZE) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        int startPos = 0;
        while (startPos < id.length() - PAD_SIZE) {
            builder.append(id.charAt(startPos));
            startPos++;
            if (startPos < id.length()) {
                builder.append(id.charAt(startPos));
                startPos++;
                if (startPos < id.length()) {
                    builder.append(id.charAt(startPos));
                    builder.append('/');
                    startPos++;
                }
            }
        }
        // Drop last '/'
        if (builder.charAt(builder.length() - 1) == '/') {
            builder.setLength(builder.length() - 1);
        }
        return builder.toString();
    }
}
