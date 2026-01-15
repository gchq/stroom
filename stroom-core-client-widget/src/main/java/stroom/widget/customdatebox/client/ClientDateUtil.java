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

package stroom.widget.customdatebox.client;

import com.google.gwt.core.client.GWT;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ClientDateUtil {

    private static final Map<String, String> JAVA_TO_JS_CONVERSION_MAP = new HashMap<>();

    static {
        // The following conversions were taken from
        // https://github.com/MadMG/moment-jdateformatparser
        // which has the following licence

        // The MIT License (MIT)

        // Copyright (c) 2013 MadMG

        // Permission is hereby granted, free of charge, to any person obtaining a copy of
        // this software and associated documentation files (the "Software"), to deal in
        // the Software without restriction, including without limitation the rights to
        // use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
        // the Software, and to permit persons to whom the Software is furnished to do so,
        // subject to the following conditions:

        // The above copyright notice and this permission notice shall be included in all
        // copies or substantial portions of the Software.

        // THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
        // IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
        // FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
        // COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
        // IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
        // CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

        JAVA_TO_JS_CONVERSION_MAP.put("d", "D");
        JAVA_TO_JS_CONVERSION_MAP.put("dd", "DD");
        JAVA_TO_JS_CONVERSION_MAP.put("y", "YYYY");
        JAVA_TO_JS_CONVERSION_MAP.put("yy", "YY");
        JAVA_TO_JS_CONVERSION_MAP.put("yyy", "YYYY");
        JAVA_TO_JS_CONVERSION_MAP.put("yyyy", "YYYY");
        JAVA_TO_JS_CONVERSION_MAP.put("a", "a");
        JAVA_TO_JS_CONVERSION_MAP.put("A", "A");
        JAVA_TO_JS_CONVERSION_MAP.put("M", "M");
        JAVA_TO_JS_CONVERSION_MAP.put("MM", "MM");
        JAVA_TO_JS_CONVERSION_MAP.put("MMM", "MMM");
        JAVA_TO_JS_CONVERSION_MAP.put("MMMM", "MMMM");
        JAVA_TO_JS_CONVERSION_MAP.put("h", "h");
        JAVA_TO_JS_CONVERSION_MAP.put("hh", "hh");
        JAVA_TO_JS_CONVERSION_MAP.put("H", "H");
        JAVA_TO_JS_CONVERSION_MAP.put("HH", "HH");
        JAVA_TO_JS_CONVERSION_MAP.put("m", "m");
        JAVA_TO_JS_CONVERSION_MAP.put("mm", "mm");
        JAVA_TO_JS_CONVERSION_MAP.put("s", "s");
        JAVA_TO_JS_CONVERSION_MAP.put("ss", "ss");
        JAVA_TO_JS_CONVERSION_MAP.put("S", "SSS");
        JAVA_TO_JS_CONVERSION_MAP.put("SS", "SSS");
        JAVA_TO_JS_CONVERSION_MAP.put("SSS", "SSS");
        JAVA_TO_JS_CONVERSION_MAP.put("E", "ddd");
        JAVA_TO_JS_CONVERSION_MAP.put("EE", "ddd");
        JAVA_TO_JS_CONVERSION_MAP.put("EEE", "ddd");
        JAVA_TO_JS_CONVERSION_MAP.put("EEEE", "dddd");
        JAVA_TO_JS_CONVERSION_MAP.put("EEEEE", "dddd");
        JAVA_TO_JS_CONVERSION_MAP.put("EEEEEE", "dddd");
        JAVA_TO_JS_CONVERSION_MAP.put("D", "DDD");
        JAVA_TO_JS_CONVERSION_MAP.put("w", "W");
        JAVA_TO_JS_CONVERSION_MAP.put("ww", "WW");
        JAVA_TO_JS_CONVERSION_MAP.put("z", "ZZ");
        JAVA_TO_JS_CONVERSION_MAP.put("zzzz", "Z");
        JAVA_TO_JS_CONVERSION_MAP.put("Z", "ZZ");
        JAVA_TO_JS_CONVERSION_MAP.put("x", "ZZ"); // Added by at55612
        JAVA_TO_JS_CONVERSION_MAP.put("xx", "ZZ"); // Added by at55612
        JAVA_TO_JS_CONVERSION_MAP.put("xxx", "Z"); // Added by at55612
        JAVA_TO_JS_CONVERSION_MAP.put("X", "ZZ");
        JAVA_TO_JS_CONVERSION_MAP.put("XX", "ZZ");
        JAVA_TO_JS_CONVERSION_MAP.put("XXX", "Z");
        JAVA_TO_JS_CONVERSION_MAP.put("u", "E");
    }

    private ClientDateUtil() {
        // Utility class.
    }

    public static String toDateString(final Long ms) {
        if (ms == null) {
            return "";
        }
        return MomentJs.nativeToDateString(ms.doubleValue());
    }

    public static String toISOString(final Long ms) {
        if (ms == null) {
            return "";
        }
        return MomentJs.nativeToISOString(ms.doubleValue());
    }

    /**
     * Parses the supplied ISO string into local time
     */
    public static Long fromISOString(final String string) {
        Long millis = null;
        if (string != null && string.trim().length() > 0) {
            try {
                final double res = MomentJs.nativeFromISOString(string.trim());
                if (res > 0) {
                    millis = (long) res;
                }
            } catch (final RuntimeException e) {
                GWT.log(e.getMessage());
            }
        }

        return millis;
    }

    /**
     * Converts a java syntax date format string into a moment.js syntax date format
     * string. May not handle all cases so it is possible the output will not be valid
     * syntax.
     */
    public static Optional<String> convertJavaFormatToJs(final String javaFormatStr) {
        if (javaFormatStr != null) {
            try {
                final StringBuilder outputBuilder = new StringBuilder();
                final StringBuilder scratchBuilder = new StringBuilder();
                char lastChar = Character.MIN_VALUE;
                for (int i = 0; i < javaFormatStr.length(); i++) {
                    final char currChar = javaFormatStr.charAt(i);
                    parseChar(outputBuilder, scratchBuilder, lastChar, currChar);
                    lastChar = currChar;
                }
                // Force it to consider the last char seen
                parseChar(outputBuilder, scratchBuilder, lastChar, Character.MIN_VALUE);
                final String jsDateFormatStr = outputBuilder.toString();

                return Optional.of(jsDateFormatStr);
            } catch (final Exception e) {
                GWT.log("Unable to convert '" + javaFormatStr + "' due to: " + e.getMessage());
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    private static void parseChar(final StringBuilder outputBuilder,
                                  final StringBuilder scratchBuilder,
                                  final char lastChar,
                                  final char currChar) {
        final boolean isDifferentChar = lastChar != Character.MIN_VALUE && lastChar != currChar;

        if (isDifferentChar) {
            final String scratch = scratchBuilder.toString();
            scratchBuilder.setLength(0);
            final String jsForm = JAVA_TO_JS_CONVERSION_MAP.get(scratch);
            if (jsForm != null) {
                outputBuilder.append(jsForm);
            } else {
                // Not a java format str so just add it as is
                outputBuilder.append(scratch);
            }
        }
        // Build up a block of repeated chars
        scratchBuilder.append(currChar);
    }

    /**
     * Returns the date/time in local time. If the date string has no
     * timezone information it is assumed to be local time.
     * See https://momentjs.com/docs/#/parsing/utc/
     */
    public static Optional<Long> parseWithJavaFormat(final String string, final String format) {
        return convertJavaFormatToJs(format)
                .map(jsFormat -> parseWithJsFormat(string, jsFormat));
    }

    /**
     * Returns the date/time in local time. If the date string has no
     * timezone information it is assumed to be local time.
     * See https://momentjs.com/docs/#/parsing/utc/
     */
    public static Long parseWithJsFormat(final String string, final String format) {
        Long millis = null;
        if (string != null
                && string.trim().length() > 0
                && format != null
                && format.trim().length() > 0) {
            try {
                final double res = MomentJs.nativeFromCustomFormatString(string.trim(), format.trim());
                if (res > 0) {
                    millis = (long) res;
                }
            } catch (final Exception e) {
                GWT.log(e.getMessage());
            }
        }

        return millis;
    }
}
