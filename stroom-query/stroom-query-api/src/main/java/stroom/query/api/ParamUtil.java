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

package stroom.query.api;


import stroom.query.api.token.BasicTokeniser;
import stroom.query.api.token.Token;
import stroom.query.api.token.TokenType;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ParamUtil {

    private static final String PARAM_START = "${";
    private static final String PARAM_END = "}";
    private static final String DEFAULT_VALUE_SEPARATOR = ":-";

    private ParamUtil() {
        // Utility class.
    }

    public static String create(final String key) {
        return PARAM_START + key + PARAM_END;
    }

    public static List<Param> parse(final String string) {
        // Create a parameter map.
        if (string == null) {
            return Collections.emptyList();
        }

        final String trimmed = string.trim();
        if (trimmed.isEmpty()) {
            return Collections.emptyList();
        }

        final List<Param> list = new ArrayList<>();
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

                        final int index = text.lastIndexOf(' ');
                        if (index != -1) {
                            if (key != null && !key.isEmpty()) {
                                final String value = text.substring(0, index).trim();
                                list.add(new Param(key, value));
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

        if (key != null && !key.isEmpty()) {
            final String value = sb.toString().trim();
            list.add(new Param(key, value));
        }

        return list;
    }

    public static String replaceParameters(final String value,
                                           final ParamValues paramValues) {
        return replaceParameters(value, paramValues, false);
    }

    public static String replaceParameters(final String value,
                                           final ParamValues paramValues,
                                           final boolean keepUnmatched) {
        if (NullSafe.isBlankString(value)) {
            return value;
        }

        final StringBuilder sb = new StringBuilder();
        final List<Token> tokens = BasicTokeniser.parse(value);
        for (final Token token : tokens) {
            if (TokenType.PARAM.equals(token.getTokenType())) {
                String key = token.getUnescapedText();
                String defaultValue = null;
                final int defaultValueIndex = key.indexOf(DEFAULT_VALUE_SEPARATOR);
                if (defaultValueIndex != -1) {
                    defaultValue = key.substring(defaultValueIndex + 2);
                    key = key.substring(0, defaultValueIndex);
                }

                final String paramValue = paramValues.getParamValue(key);
                if (paramValue != null) {
                    if (containsWhitespace(paramValue)) {
                        sb.append("'");
                        sb.append(paramValue);
                        sb.append("'");
                    } else {
                        sb.append(paramValue);
                    }
                } else if (defaultValue != null) {
                    sb.append(defaultValue);
                } else if (keepUnmatched) {
                    sb.append(token.getText());
                }
            } else {
                sb.append(token.getText());
            }
        }


//
//
//        int paramStart = -1;
//        char[] chars = value.toCharArray();
//        for (int i = 0; i < chars.length; i++) {
//            switch (chars[i]) {
//                case '$':
//                    if (paramStart == -1) {
//                        int dollarCount = 0;
//                        int bracketCount = 0;
//                        for (int j = i; j < chars.length; j++) {
//                            if (chars[j] == '$') {
//                                dollarCount++;
//                            } else if (chars[j] == '{') {
//                                bracketCount++;
//                                break;
//                            } else {
//                                break;
//                            }
//                        }
//
//                        i += dollarCount - 1;
//                        if (bracketCount == 1) {
//                            final int size = dollarCount / 2;
//                            for (int j = 0; j < size; j++) {
//                                sb.append("$");
//                            }
//
//                            if (dollarCount % 2 != 0) {
//                                paramStart = i;
//                            }
//                        } else {
//                            final int size = dollarCount;
//                            for (int j = 0; j < size; j++) {
//                                sb.append("$");
//                            }
//                        }
//                    }
//
//                    break;
//                case '}':
//                    if (paramStart != -1) {
//                        final String key = value.substring(paramStart + 2, i);
//                        String replacement = paramValues.get(key);
//                        if (replacement != null) {
//                            sb.append(replacement);
//                        }
//
//                        paramStart = -1;
//                    } else {
//                        sb.append(chars[i]);
//                    }
//
//                    break;
//
//                default:
//                    if (paramStart == -1) {
//                        sb.append(chars[i]);
//                    }
//            }
//        }

        return sb.toString();
    }

    public static boolean containsWhitespace(final String value) {
        if (value != null) {
            final char[] chars = value.toCharArray();
            for (final char c : chars) {
                if (Character.isWhitespace(c)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static List<String> getKeys(final String value) {
        if (NullSafe.isBlankString(value)) {
            return Collections.emptyList();
        }

        final List<String> keys = new ArrayList<>();
        final List<Token> tokens = BasicTokeniser.parse(value);
        for (final Token token : tokens) {
            if (TokenType.PARAM.equals(token.getTokenType())) {
                final String key = token.getUnescapedText();
                keys.add(key);
            }
        }

//
//
//        int paramStart = -1;
//        char[] chars = value.toCharArray();
//        for (int i = 0; i < chars.length; i++) {
//            switch (chars[i]) {
//                case '$':
//                    if (paramStart == -1) {
//                        int dollarCount = 0;
//                        int bracketCount = 0;
//                        for (int j = i; j < chars.length; j++) {
//                            if (chars[j] == '$') {
//                                dollarCount++;
//                            } else if (chars[j] == '{') {
//                                bracketCount++;
//                                break;
//                            } else {
//                                break;
//                            }
//                        }
//
//                        i += dollarCount - 1;
//                        if (bracketCount == 1) {
//                            if (dollarCount % 2 != 0) {
//                                paramStart = i;
//                            }
//                        }
//                    }
//
//                    break;
//                case '}':
//                    if (paramStart != -1) {
//                        final String key = value.substring(paramStart + 2, i);
//                        keys.add(key);
//
//                        paramStart = -1;
//                    }
//                    break;
//            }
//        }

        return keys;
    }


    public static Map<String, String> createParamMap(final List<Param> params) {
        // Create a parameter map.
        final Map<String, String> paramMap;
        if (params != null) {
            paramMap = new HashMap<>();
            for (final Param param : params) {
                if (param.getKey() != null && param.getValue() != null) {
                    paramMap.put(param.getKey(), param.getValue());
                }
            }
        } else {
            paramMap = Collections.emptyMap();
        }
        return paramMap;
    }

    public static ParamValues createParamValueFunction(final List<Param> params) {
        final Map<String, String> paramMap = createParamMap(params);
        return paramMap::get;
    }
}
