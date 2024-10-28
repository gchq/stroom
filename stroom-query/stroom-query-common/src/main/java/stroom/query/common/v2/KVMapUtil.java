/*
 * Copyright 2024 Crown Copyright
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

package stroom.query.common.v2;

import stroom.util.NullSafe;
import stroom.util.shared.string.CIKey;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class KVMapUtil {

    private KVMapUtil() {
        // Utility class.
    }

    public static Map<CIKey, String> parse(final String string) {
        // Create a parameter map.
        if (NullSafe.isBlankString(string)) {
            return Collections.emptyMap();
        }

        final String trimmed = string.trim();

        final Map<CIKey, String> paramMap = new HashMap<>();
        final char[] chars = trimmed.toCharArray();

        boolean quot = false;
        final StringBuilder sb = new StringBuilder();

        String key = null;

        for (int i = 0; i < chars.length; i++) {
            switch (chars[i]) {
                case '\\':
                    if (quot) {
                        sb.append(chars[i]);
                    } else {
                        if (i + 1 < chars.length) {
                            i++;
                            sb.append(chars[i]);
                        } else {
                            sb.append(chars[i]);
                        }
                    }
                    break;
                case '"':
                    if (i + 1 < chars.length && chars[i + 1] == '"') {
                        i++;
                        sb.append(chars[i]);
                    } else {
                        quot = !quot;
                    }
                    break;
                case '=':
                    if (quot) {
                        sb.append(chars[i]);
                    } else {
                        final String text = sb.toString();

                        int index = text.lastIndexOf(' ');
                        if (index != -1) {
                            if (NullSafe.isNonEmptyString(key)) {
                                final String value = text.substring(0, index).trim();
                                paramMap.put(CIKey.of(key), value);
                            }

                            key = text.substring(index + 1).trim();
                        } else {
                            key = text.trim();
                        }

                        sb.setLength(0);
                    }
                    break;
                default:
                    sb.append(chars[i]);
            }
        }

        if (NullSafe.isNonEmptyString(key)) {
            final String value = sb.toString().trim();
            paramMap.put(CIKey.of(key), value);
        }

        return paramMap;
    }

    public static String replaceParameters(final String value,
                                           final Map<CIKey, String> paramMap) {
        final StringBuilder sb = new StringBuilder();

        int paramStart = -1;
        char[] chars = value.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            switch (chars[i]) {
                case '$':
                    if (paramStart == -1) {
                        int dollarCount = 0;
                        int bracketCount = 0;
                        for (int j = i; j < chars.length; j++) {
                            if (chars[j] == '$') {
                                dollarCount++;
                            } else if (chars[j] == '{') {
                                bracketCount++;
                                break;
                            } else {
                                break;
                            }
                        }

                        i += dollarCount - 1;
                        if (bracketCount == 1) {
                            final int size = dollarCount / 2;
                            for (int j = 0; j < size; j++) {
                                sb.append("$");
                            }

                            if (dollarCount % 2 != 0) {
                                paramStart = i;
                            }
                        } else {
                            final int size = dollarCount;
                            for (int j = 0; j < size; j++) {
                                sb.append("$");
                            }
                        }
                    }

                    break;
                case '}':
                    if (paramStart != -1) {
                        final String key = value.substring(paramStart + 2, i);
                        final String replacement = NullSafe.string(paramMap.get(CIKey.of(key)));
                        sb.append(replacement);

                        paramStart = -1;
                    } else {
                        sb.append(chars[i]);
                    }

                    break;

                default:
                    if (paramStart == -1) {
                        sb.append(chars[i]);
                    }
            }
        }

        return sb.toString();
    }
}
