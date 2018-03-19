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

package stroom.pipeline.writer;

import com.google.common.base.Strings;
import stroom.node.NodeCache;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.SearchIdHolder;
import stroom.pipeline.state.StreamHolder;
import stroom.util.config.StroomProperties;

import javax.inject.Inject;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public class PathCreator {
    private static final String[] NON_ENV_VARS = {
            "feed",
            "pipeline",
            "streamId",
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
            StroomProperties.STROOM_TEMP};

    private static final Set<String> NON_ENV_VARS_SET = Collections
            .unmodifiableSet(new HashSet<>(Arrays.asList(NON_ENV_VARS)));

    private final FeedHolder feedHolder;
    private final PipelineHolder pipelineHolder;
    private final StreamHolder streamHolder;
    private final SearchIdHolder searchIdHolder;
    private final NodeCache nodeCache;

    @Inject
    PathCreator(final FeedHolder feedHolder,
                final PipelineHolder pipelineHolder,
                final StreamHolder streamHolder,
                final SearchIdHolder searchIdHolder,
                final NodeCache nodeCache) {
        this.feedHolder = feedHolder;
        this.pipelineHolder = pipelineHolder;
        this.streamHolder = streamHolder;
        this.searchIdHolder = searchIdHolder;
        this.nodeCache = nodeCache;
    }

    public static String replaceTimeVars(String path) {
        // Replace some of the path elements with system variables.
        final ZonedDateTime dateTime = ZonedDateTime.now(ZoneOffset.UTC);
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

    public static String replaceSystemProperties(String path) {
        // Replace stroom.temp
        path = replace(
                path,
                StroomProperties.STROOM_TEMP,
                () -> StroomProperties.getProperty(StroomProperties.STROOM_TEMP));

        return SystemPropertyUtil.replaceSystemProperty(path, NON_ENV_VARS_SET);
    }

    public static String replaceUUIDVars(String path) {
        path = replace(path, "uuid", () -> UUID.randomUUID().toString());
        return path;
    }

    public static String replaceFileName(String path, final String fileName) {

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

    private static String replace(final String path,
                                  final String type,
                                  final LongSupplier replacementSupplier,
                                  final int pad) {

        //convert the long supplier into a string supplier to prevent the
        //evaluation of the long supplier
        Supplier<String> stringReplacementSupplier = () -> {
            String value = String.valueOf(replacementSupplier.getAsLong());
            if (pad > 0) {
                value = Strings.padStart(value, pad, '0');
            }
            return value;
        };
        return replace(path, type, stringReplacementSupplier);
    }

    private static String replace(final String path,
                                  final String type,
                                  final Supplier<String> replacementSupplier) {
        String newPath = path;
        final String param = "${" + type + "}";
        int start = newPath.indexOf(param);
        while (start != -1) {
            final int end = start + param.length();
            newPath = newPath.substring(0, start) + replacementSupplier.get() + newPath.substring(end);
            start = newPath.indexOf(param, end);
        }

        return newPath;
    }

    public String replaceAll(String path) {
        path = replaceContextVars(path);
        path = replaceTimeVars(path);
        path = replaceUUIDVars(path);
        path = replaceSystemProperties(path);
        return path;
    }

    public String replaceContextVars(String path) {
        if (feedHolder != null && feedHolder.getFeed() != null) {
            path = replace(path, "feed", () -> feedHolder.getFeed().getName());
        }
        if (pipelineHolder != null && pipelineHolder.getPipeline() != null) {
            path = replace(path, "pipeline", () -> pipelineHolder.getPipeline().getName());
        }
        if (streamHolder != null && streamHolder.getStream() != null) {
            path = replace(path, "streamId", () -> streamHolder.getStream().getId(), 0);
        }
        if (searchIdHolder != null && searchIdHolder.getSearchId() != null) {
            path = replace(path, "searchId", () -> searchIdHolder.getSearchId());
        }
        if (nodeCache != null) {
            path = replace(path, "node", () -> nodeCache.getDefaultNode().getName());
        }

        return path;
    }
}
