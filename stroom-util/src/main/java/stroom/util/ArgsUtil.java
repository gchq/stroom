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

package stroom.util;

import java.util.Map;

public final class ArgsUtil {

    private ArgsUtil() {
        // Utility class.
    }

    public static Map<String, String> parse(final String[] args) {
        final CIStringHashMap map = new CIStringHashMap();
        for (final String arg : args) {
            final String[] split = arg.split("=");
            if (split.length > 1) {
                map.put(split[0], split[1]);
            } else {
                map.put(split[0], "");
            }
        }
        return map;
    }
}
