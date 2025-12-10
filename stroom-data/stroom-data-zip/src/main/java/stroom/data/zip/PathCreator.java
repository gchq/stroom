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

package stroom.data.zip;

import stroom.meta.api.AttributeMap;

import com.google.common.base.Strings;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

class PathCreator {

    static String replaceTimeVars(final String path) {
        // Replace some of the path elements with system variables.
        final ZonedDateTime dateTime = ZonedDateTime.now(ZoneOffset.UTC);
        return replaceTimeVars(path, dateTime);
    }

    static String replaceTimeVars(String path, final ZonedDateTime dateTime) {
        // Replace some of the path elements with system variables.
        path = replace(path, "year", dateTime.getYear(), 4);
        path = replace(path, "month", dateTime.getMonthValue(), 2);
        path = replace(path, "day", dateTime.getDayOfMonth(), 2);
        path = replace(path, "hour", dateTime.getHour(), 2);
        path = replace(path, "minute", dateTime.getMinute(), 2);
        path = replace(path, "second", dateTime.getSecond(), 2);
        path = replace(path, "millis", dateTime.toInstant().toEpochMilli(), 3);
        path = replace(path, "ms", dateTime.toInstant().toEpochMilli(), 0);

        return path;
    }

    static String[] findVars(final String path) {
        final List<String> vars = new ArrayList<>();
        final char[] arr = path.toCharArray();
        char lastChar = 0;
        int start = -1;
        for (int i = 0; i < arr.length; i++) {
            final char c = arr[i];
            if (start == -1 && c == '{' && lastChar == '$') {
                start = i + 1;
            } else if (start != -1 && c == '}') {
                vars.add(new String(arr, start, i - start));
                start = -1;
            }

            lastChar = c;
        }

        return vars.toArray(new String[0]);
    }

    private static String replace(final String path, final String type, final long replacement, final int pad) {
        String value = String.valueOf(replacement);
        if (pad > 0) {
            value = Strings.padStart(value, pad, '0');
        }
        return replace(path, type, value);
    }

    static String replace(final String path, final String type, final String replacement) {
        String newPath = path;
        final String param = "${" + type + "}";
        int start = newPath.indexOf(param);
        while (start != -1) {
            final int end = start + param.length();
            newPath = newPath.substring(0, start) + replacement + newPath.substring(end);
            start = newPath.indexOf(param, start);
        }

        return newPath;
    }

    private static String replaceAttributeMapVars(final String path, final AttributeMap attributeMap) {
        String result = path;
        if (attributeMap != null) {
            final String[] vars = findVars(result);
            for (final String var : vars) {
                final String value = attributeMap.get(var);
                if (value != null) {
                    result = replace(result, var, value);
                }
            }
        }
        return result;
    }

    static String replaceAll(String template, final AttributeMap attributeMap) {
        template = replaceTimeVars(template);
        template = replaceAttributeMapVars(template, attributeMap);
        return template;
    }
}
