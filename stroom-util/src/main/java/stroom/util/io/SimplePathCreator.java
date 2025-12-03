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

package stroom.util.io;

import stroom.util.shared.NullSafe;

import com.google.common.base.Strings;
import jakarta.inject.Inject;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public class SimplePathCreator implements PathCreator {

    private static final String STROOM_TEMP = "stroom.temp";
    private static final String STROOM_HOME = "stroom.home";
    private static final String[] NON_ENV_VARS = {
            "feed",
            "pipeline",
            "sourceId",
            "streamId", // TODO : DEPRECATED ALIAS FOR SOURCE ID.
            "partNo",
            "streamNo", // TODO : DEPRECATED ALIAS FOR PART NO.
            "searchId",
            "node",
            "year",
            "month",
            "day",
            "hour",
            "minute",
            "second",
            "millis",
            "ms",
            "uuid",
            "fileName",
            "fileStem",
            "fileExtension",
            STROOM_HOME,
            STROOM_TEMP};

    private static final Set<String> NON_ENV_VARS_SET = Set.of(NON_ENV_VARS);

    private final TempDirProvider tempDirProvider;
    private final HomeDirProvider homeDirProvider;

    @Inject
    public SimplePathCreator(final HomeDirProvider homeDirProvider,
                             final TempDirProvider tempDirProvider) {
        this.homeDirProvider = homeDirProvider;
        this.tempDirProvider = tempDirProvider;
    }

    @Override
    public String replaceTimeVars(final String path) {
        // Replace some of the path elements with time variables.
        final ZonedDateTime dateTime = ZonedDateTime.now(ZoneOffset.UTC);
        return replaceTimeVars(path, dateTime);
    }

    @Override
    public String replaceTimeVars(String path, final ZonedDateTime dateTime) {
        // Replace some of the path elements with time variables.
        path = replace(path, "year", dateTime::getYear, 4);
        path = replace(path, "month", dateTime::getMonthValue, 2);
        path = replace(path, "day", dateTime::getDayOfMonth, 2);
        path = replace(path, "hour", dateTime::getHour, 2);
        path = replace(path, "minute", dateTime::getMinute, 2);
        path = replace(path, "second", dateTime::getSecond, 2);
        path = replace(path, "millis", () -> dateTime.toInstant().toEpochMilli(), 3);
        path = replace(path, "ms", () -> dateTime.toInstant().toEpochMilli(), 0);

        return path;
    }

    @Override
    public String replaceSystemProperties(String path) {
        if (path != null) {
            path = replace(
                    path,
                    STROOM_HOME,
                    () -> FileUtil.getCanonicalPath(homeDirProvider.get()));
            path = replace(
                    path,
                    STROOM_TEMP,
                    () -> FileUtil.getCanonicalPath(tempDirProvider.get()));
            path = FileUtil.replaceHome(path);

            path = SystemPropertyUtil.replaceSystemProperty(path, NON_ENV_VARS_SET);
        }
        return path;
    }

    @Override
    public Path toAppPath(String pathString) {
        if (pathString == null) {
            pathString = "";
        } else {
            pathString = pathString.trim();
        }

        pathString = replaceSystemProperties(pathString);
        return toAbsolutePath(pathString);
    }

    @Override
    public String replaceUUIDVars(String path) {
        path = replace(path, "uuid", () -> UUID.randomUUID().toString());
        return path;
    }

    @Override
    public String replaceFileName(String path, final String fileName) {
        path = replace(path, "fileName", () -> fileName);

        path = replace(path, "fileStem", () -> {
            String fileStem = fileName;
            final int index = fileName.lastIndexOf(".");
            if (index != -1) {
                fileStem = fileName.substring(0, index);
            }
            return fileStem;
        });

        path = replace(path, "fileExtension", () -> {
            String fileExtension = "";
            final int index = fileName.lastIndexOf(".");
            if (index != -1) {
                fileExtension = fileName.substring(index + 1);
            }
            return fileExtension;
        });
        return path;
    }

    @Override
    public String[] findVars(final String path) {
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

    @Override
    public boolean containsVars(final String path) {
        if (NullSafe.isNonBlankString(path)) {
            char lastChar = 0;
            boolean foundStart = false;
            final char[] arr = path.toCharArray();
            for (final char c : arr) {
                if (c == '{' && lastChar == '$') {
                    foundStart = true;
                } else if (foundStart && c == '}') {
                    return true;
                }
                lastChar = c;
            }
        }
        return false;
    }

    @Override
    public String replace(final String path,
                          final String var,
                          final LongSupplier replacementSupplier,
                          final int pad) {

        //convert the long supplier into a string supplier to prevent the
        //evaluation of the long supplier
        final Supplier<String> stringReplacementSupplier = () -> {
            String value = String.valueOf(replacementSupplier.getAsLong());
            if (pad > 0) {
                value = Strings.padStart(value, pad, '0');
            }
            return value;
        };
        return replace(path, var, stringReplacementSupplier);
    }

    public String replace(final String path,
                          final Map<String, Supplier<String>> varToReplacementSupplierMap) {
        if (NullSafe.isNonBlankString(path)) {
            String output = path;
            for (final Entry<String, Supplier<String>> entry : varToReplacementSupplierMap.entrySet()) {
                output = replace(output, entry.getKey(), entry.getValue());
            }
            return output;
        } else {
            return path;
        }
    }

    @Override
    public String replace(final String str,
                          final String var,
                          final Supplier<String> replacementSupplier) {
        String newPath = str;
        final String param = "${" + var + "}";
        int start = newPath.indexOf(param);
        while (start != -1) {
            final int end = start + param.length();
            // Could consider re-using the replacement value to save calling the supplier
            // multiple times if the var is used >1 time.
            newPath = newPath.substring(0, start) + replacementSupplier.get() + newPath.substring(end);
            start = newPath.indexOf(param, start);
        }

        return newPath;
    }

    @Override
    public String replaceAll(String path) {
        path = replaceContextVars(path);
        path = replaceTimeVars(path);
        path = replaceUUIDVars(path);
        path = replaceSystemProperties(path);
        return path;
    }

    @Override
    public String replaceContextVars(final String path) {
        return path;
    }

    @Override
    public String toString() {
        return "PathCreator{" +
               "tempDir=" + tempDirProvider.get() +
               ", homeDir=" + homeDirProvider.get() +
               '}';
    }

    private Path toAbsolutePath(final String pathString) {
        Path path = Paths.get(pathString);
        path = path.toAbsolutePath();

        // If this isn't an absolute path then make it so.
        if (!pathString.equals(path.toString())) {
            path = homeDirProvider.get().resolve(pathString).toAbsolutePath();
        }
        return path.normalize();
    }

}
