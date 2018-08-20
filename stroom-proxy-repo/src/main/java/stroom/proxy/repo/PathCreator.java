package stroom.proxy.repo;

import org.apache.commons.lang.StringUtils;
import stroom.feed.MetaMap;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

class PathCreator {
    static String replaceTimeVars(String path) {
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

        String[] varsArr = new String[vars.size()];
        varsArr = vars.toArray(varsArr);
        return varsArr;
    }

    private static String replace(final String path, final String type, final long replacement, final int pad) {
        String value = String.valueOf(replacement);
        if (pad > 0) {
            value = StringUtils.leftPad(value, pad, '0');
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

    private static String replaceMetaMapVars(final String path, final MetaMap metaMap) {
        String result = path;
        if (metaMap != null) {
            final String[] vars = findVars(result);
            for (final String var : vars) {
                String value = metaMap.get(var);
                if (value != null) {
                    result = replace(result, var, value);
                }
            }
        }
        return result;
    }

    static String replaceAll(String template, final MetaMap metaMap) {
        template = replaceTimeVars(template);
        template = replaceMetaMapVars(template, metaMap);
        return template;
    }
}
