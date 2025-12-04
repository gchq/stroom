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

package stroom.util.string;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class EncodingUtil {

    private static final Charset CHARSET = StandardCharsets.UTF_8;

    private EncodingUtil() {
        // Utility class.
    }

    public static byte[] asBytes(final String string) {
        if (string == null) {
            return null;
        }
        return string.getBytes(CHARSET);
    }

    public static String asString(final byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return new String(bytes, CHARSET);
    }
}
