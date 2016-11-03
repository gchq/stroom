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

package stroom.pipeline.server.writer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Resource;

import stroom.util.config.StroomProperties;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import stroom.node.server.NodeCache;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.SearchIdHolder;
import stroom.pipeline.state.StreamHolder;
import stroom.util.SystemPropertyUtil;
import stroom.util.spring.StroomScope;

@Component
@Scope(StroomScope.PROTOTYPE)
public class PathCreator {
    @Resource
    private FeedHolder feedHolder;
    @Resource
    private PipelineHolder pipelineHolder;
    @Resource
    private StreamHolder streamHolder;
    @Resource
    private SearchIdHolder searchIdHolder;
    @Resource
    private NodeCache nodeCache;

    private static final String[] NON_ENV_VARS = { "feed", "pipeline", "streamId", "searchId", "node", "year", "month",
            "day", "hour", "minute", "second", "millis", "ms", "uuid", "fileName", "fileStem", "fileExtension",
            StroomProperties.STROOM_TEMP };
    private static final Set<String> NON_ENV_VARS_SET = Collections
            .unmodifiableSet(new HashSet<>(Arrays.asList(NON_ENV_VARS)));

    public String replaceAll(String path) {
        path = replaceContextVars(path);
        path = replaceTimeVars(path);
        path = replaceUUIDVars(path);
        path = replaceSystemProperties(path);
        return path;
    }

    public String replaceContextVars(String path) {
        if (feedHolder != null && feedHolder.getFeed() != null) {
            path = replace(path, "feed", feedHolder.getFeed().getName());
        }
        if (pipelineHolder != null && pipelineHolder.getPipeline() != null) {
            path = replace(path, "pipeline", pipelineHolder.getPipeline().getName());
        }
        if (streamHolder != null && streamHolder.getStream() != null) {
            path = replace(path, "streamId", String.valueOf(streamHolder.getStream().getId()));
        }
        if (searchIdHolder != null && searchIdHolder.getSearchId() != null) {
            path = replace(path, "searchId", String.valueOf(searchIdHolder.getSearchId()));
        }
        if (nodeCache != null) {
            path = replace(path, "node", String.valueOf(nodeCache.getDefaultNode().getName()));
        }

        return path;
    }

    public static String replaceTimeVars(String path) {
        // Replace some of the path elements with system variables.
        final DateTime dateTime = new DateTime(DateTimeZone.UTC);
        path = replace(path, "year", dateTime.getYear(), 4);
        path = replace(path, "month", dateTime.getMonthOfYear(), 2);
        path = replace(path, "day", dateTime.getDayOfMonth(), 2);
        path = replace(path, "hour", dateTime.getHourOfDay(), 2);
        path = replace(path, "minute", dateTime.getMinuteOfHour(), 2);
        path = replace(path, "second", dateTime.getSecondOfMinute(), 2);
        path = replace(path, "millis", dateTime.getMillisOfSecond(), 3);
        path = replace(path, "ms", dateTime.getMillis(), 0);

        return path;
    }

    public static String replaceSystemProperties(String path) {
        // Replace stroom.temp
        path = replace(path, StroomProperties.STROOM_TEMP, StroomProperties.getProperty(StroomProperties.STROOM_TEMP));

        return SystemPropertyUtil.replaceSystemProperty(path, NON_ENV_VARS_SET);
    }

    public static String replaceUUIDVars(String path) {
        // Guard for UUID as creation is expensive.
        if (path.indexOf("${uuid}") != -1) {
            path = replace(path, "uuid", UUID.randomUUID().toString());
        }
        return path;
    }

    public static String replaceFileName(String path, final String fileName) {
        String fileStem = fileName;
        String fileExtension = "";
        final int index = fileName.lastIndexOf(".");
        if (index != -1) {
            fileStem = fileName.substring(0, index);
            fileExtension = fileName.substring(index + 1);
        }
        path = replace(path, "fileName", fileName);
        path = replace(path, "fileStem", fileStem);
        path = replace(path, "fileExtension", fileExtension);
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
}
