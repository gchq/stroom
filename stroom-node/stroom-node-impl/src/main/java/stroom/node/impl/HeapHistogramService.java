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

package stroom.node.impl;

import stroom.util.logging.LogUtil;

import com.google.common.base.Preconditions;
import com.sun.management.DiagnosticCommandMBean;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

/**
 * Class for generating a java heap map histogram using the gcClassHistogram action of the
 * {@link DiagnosticCommandMBean}
 */
@Singleton
@SuppressWarnings("unused")
class HeapHistogramService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HeapHistogramService.class);

    static final String CLASS_NAME_MATCH_REGEX_PROP_KEY = "stroom.node.status.heapHistogram.classNameMatchRegex";
    static final String ANON_ID_REGEX_PROP_KEY = "stroom.node.status.heapHistogram.classNameReplacementRegex";

    private static final String DIAGNOSTIC_COMMAND_MBEAN_OBJECT_NAME = "com.sun.management:type=DiagnosticCommand";

    private static final String ACTION_NAME = "gcClassHistogram";
    private static final String ID_REPLACEMENT = "--ID-REMOVED--";

    private static final int STRING_TRUNCATE_LIMIT = 200;

    private final Provider<HeapHistogramConfig> heapHistogramConfigProvider;
    private final Pattern lineMatchPattern;

    @SuppressWarnings("unused")
    @Inject
    HeapHistogramService(final Provider<HeapHistogramConfig> heapHistogramConfigProvider) {
        this.heapHistogramConfigProvider = heapHistogramConfigProvider;
        this.lineMatchPattern = Pattern.compile(
                "\\s*\\d+:\\s+(?<instances>\\d+)\\s+(?<bytes>\\d+)\\s+(?<class>.*)");
    }

    /**
     * Generates a heap histogram using the 'gcClassHistogram' MBean action.  Will
     * block until the action completes/fails.
     * list of {@link HeapHistogramEntry}
     */
    List<HeapHistogramEntry> generateHeapHistogram() {
        final Object output = getRawHistogramOutput();

        final List<HeapHistogramEntry> heapHistogramEntries;
        if (output == null) {
            LOGGER.warn("Heap histogram produced no output for action {}", ACTION_NAME);
            heapHistogramEntries = Collections.emptyList();
        } else if (output instanceof String) {
            heapHistogramEntries = processOutput((String) output);
        } else {
            throw new RuntimeException(LogUtil.message("Unexpected type {}", output.getClass().getName()));
        }

        return heapHistogramEntries;
    }

    private Object getRawHistogramOutput() {
        final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        final Object output;
        try {
            final ObjectName objectName = new ObjectName(DIAGNOSTIC_COMMAND_MBEAN_OBJECT_NAME);
            output = server.invoke(
                    objectName,
                    ACTION_NAME,
                    new Object[]{null},
                    new String[]{String[].class.getName()});
        } catch (final MalformedObjectNameException
                       | InstanceNotFoundException
                       | ReflectionException
                       | MBeanException e) {
            throw new RuntimeException(LogUtil.message("Error invoking action {}", ACTION_NAME), e);
        }
        return output;
    }

    private static String getTruncatedStr(final String str) {
        if (str != null && str.length() > STRING_TRUNCATE_LIMIT) {
            return str.substring(0, STRING_TRUNCATE_LIMIT) + "...TRUNCATED...";
        } else {
            return str;
        }
    }

    private Predicate<String> getClassNameMatchPredicate() {
        final String classNameRegexStr = heapHistogramConfigProvider.get().getClassNameMatchRegex();

        if (classNameRegexStr == null || classNameRegexStr.isEmpty()) {
            //no prop value so return an always true predicate
            return str -> true;
        } else {
            try {
                return Pattern.compile(classNameRegexStr).asPredicate();
            } catch (final RuntimeException e) {
                throw new RuntimeException(
                        LogUtil.message("Error compiling regex string [{}]", classNameRegexStr), e);
            }
        }
    }

    private Function<String, String> getClassReplacementMapper() {
        final String anonymousIdRegex = heapHistogramConfigProvider.get().getClassNameReplacementRegex();

        if (anonymousIdRegex == null || anonymousIdRegex.isEmpty()) {
            return Function.identity();
        } else {
            try {
                final Pattern pattern = Pattern.compile(anonymousIdRegex);
                return className -> pattern.matcher(className).replaceAll(ID_REPLACEMENT);
            } catch (final RuntimeException e) {
                LOGGER.error("Value [{}] for property [{}] is not valid regex",
                        anonymousIdRegex, "classNameReplacementRegex", e);
                return Function.identity();
            }
        }
    }

    private Function<String, Optional<HeapHistogramEntry>> buildLineToEntryMapper(
            final Function<String, String> classNameReplacer) {

        Preconditions.checkNotNull(classNameReplacer);
        return line -> {
            final Matcher matcher = lineMatchPattern.matcher(line);
            if (matcher.matches()) {
                //if this is a data row then extract the values of interest
                final long instances = Long.parseLong(matcher.group("instances"));
                final long bytes = Long.parseLong(matcher.group("bytes"));
                final String className = matcher.group("class");
                final String newClassName = classNameReplacer.apply(className);
                LOGGER.trace("className [{}], newClassName [{}]", className, newClassName);

                return Optional.of(new HeapHistogramEntry(newClassName, instances, bytes));
            } else {
                LOGGER.trace("Ignoring jamp histogram line [{}]", line);
                return Optional.empty();
            }
        };
    }

    private List<HeapHistogramEntry> processOutput(final String output) {
        Preconditions.checkNotNull(output);

        try {
            final Predicate<String> classNamePredicate = getClassNameMatchPredicate();
            final Function<String, String> classNameReplacer = getClassReplacementMapper();
            final Function<String, Optional<HeapHistogramEntry>> lineToEntryMapper =
                    buildLineToEntryMapper(classNameReplacer);

            final String[] lines = output.split("\\r?\\n");

            LOGGER.debug("processing {} lines of stdout", lines.length);

            final List<HeapHistogramService.HeapHistogramEntry> histogramEntries = Arrays
                    .stream(lines)
                    .map(lineToEntryMapper)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .filter(heapHistogramEntry ->
                            classNamePredicate.test(heapHistogramEntry.getClassName()))
                    .collect(Collectors.toList());

            LOGGER.debug("histogramEntries size [{}]", histogramEntries.size());
            if (histogramEntries.size() == 0) {
                LOGGER.error("Something has gone wrong filtering the heap histogram, zero entries returned");
            }
            return histogramEntries;

        } catch (final RuntimeException e) {
            throw new RuntimeException(LogUtil.message("Error processing output string [{}]",
                    getTruncatedStr(output)), e);
        }
    }

    static class HeapHistogramEntry {

        private final String className;
        private final long instances;
        private final long bytes;

        HeapHistogramEntry(final String className, final long instances, final long bytes) {
            this.className = Preconditions.checkNotNull(className);
            this.instances = instances;
            this.bytes = bytes;
        }

        String getClassName() {
            return className;
        }

        long getInstances() {
            return instances;
        }

        long getBytes() {
            return bytes;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final HeapHistogramEntry that = (HeapHistogramEntry) o;

            if (instances != that.instances) {
                return false;
            }
            if (bytes != that.bytes) {
                return false;
            }
            return className.equals(that.className);
        }

        @Override
        public int hashCode() {
            int result = className.hashCode();
            result = 31 * result + (int) (instances ^ (instances >>> 32));
            result = 31 * result + (int) (bytes ^ (bytes >>> 32));
            return result;
        }

        @Override
        public String toString() {
            return "HeapHistogramEntry{" +
                    "className='" + className + '\'' +
                    ", instances=" + instances +
                    ", bytes=" + bytes +
                    '}';
        }
    }
}
