package stroom.util.zip;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PathCreator {

    private static final Logger LOGGER = LoggerFactory.getLogger(PathCreator.class);

    public static String replaceTimeVars(String path) {
        // Replace some of the path elements with system variables.
        final ZonedDateTime dateTime = ZonedDateTime.now(ZoneOffset.UTC);
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

    public static String[] findVars(final String path) {
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

    private static String replace(final String path, final String type, final String replacement) {
        String newPath = path;
        final String param = "${" + type + "}";
        int start = newPath.indexOf(param);
        while (start != -1) {
            final int end = start + param.length();
            newPath = newPath.substring(0, start) + replacement + newPath.substring(end);
            start = newPath.indexOf(param, end);
        }

        return newPath;
    }

    private static String getHeaderMapValue(final HeaderMap headerMap, final String key, final String defaultValue) {
        if (headerMap != null) {
            return headerMap.getOrDefault(key, defaultValue);
        } else {
            return "";
        }
    }

    public static String replace(final String template, final HeaderMap headerMap, final int maxLength) {
        if (template == null || template.isEmpty()) {
            return "";
        } else {
            boolean isRedacted = false;
            String[] vars = findVars(template);
            int varCount = vars.length;
            String result = "";
            //attempt to replace all vars with the resolved value from the headerMap
            //however if the resulting string is too long then try again but this time replace
            //the last var with "", and so on until all vars are replaced with ""
            for (int i = varCount; i >= 0; i--) {
                int j = 1;
                result = template;
                for (String var : vars) {
                    String value;
                    if (j <= i && j > 0) {
                        value = getHeaderMapValue(headerMap, var, "");
                    } else {
                        value = "";
                        isRedacted = true;
                    }
                    result = replace(result, var, value);
                    j++;
                }
                if (result.length() <= maxLength) {
                    //all vars rerplaced, and length is good so break out of the loop
                    break;
                }
            }
            if (isRedacted) {
                //TODO java 7 code
//                    StringBuilder stringBuilder =  new StringBuilder();
//                    for (String var : vars){
//                        String val = headerMap.getOrDefault(var, "NOT_FOUND");
//                        stringBuilder.append(var);
//                        stringBuilder.append(" ");
//                        stringBuilder.append(", ");
//                    }
//                    if (stringBuilder.length() > 0){
//                        stringBuilder.deleteCharAt(stringBuilder.length() - 1);
//                    }
//                    String varValues = stringBuilder.toString();
                String varValues = Arrays.stream(vars)
                        .sequential()
                        .map(var -> var + "=" + headerMap.getOrDefault(var, "NOT_FOUND"))
                        .collect(Collectors.joining(","));
                LOGGER.warn(String.format("Could not replace all variables in the template [%s] with values [%s]",
                        template, varValues));
            }
            if (result.length() > maxLength) {
                return "";
            } else {
                return result;
            }
        }
    }

    public String replaceAll(String template, final HeaderMap headerMap, final int maxLength) {
        template = replaceTimeVars(template);
        template = replace(template, headerMap, maxLength);
        return template;
    }
}
