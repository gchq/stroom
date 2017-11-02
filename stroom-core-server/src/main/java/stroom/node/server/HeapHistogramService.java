package stroom.node.server;

import com.google.common.base.Preconditions;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;
import org.springframework.stereotype.Component;
import stroom.util.logging.StroomLogger;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
@Component
public class HeapHistogramService {

    private static final StroomLogger LOGGER = StroomLogger.getLogger(HeapHistogramService.class);

    static final String CLASS_NAME_MATCH_REGEX_PROP_KEY = "stroom.node.status.heapHistogram.classNameMatchRegex";
    static final String JMAP_EXECUTABLE_PROP_KEY = "stroom.node.status.heapHistogram.jMapExecutable";

    public static final int STRING_TRUNCATE_LIMIT = 200;

    private final StroomPropertyService stroomPropertyService;

    @SuppressWarnings("unused")
    @Inject
    public HeapHistogramService(final StroomPropertyService stroomPropertyService) {
        this.stroomPropertyService = stroomPropertyService;
    }

    /**
     * Asynchronous method to build a heap histogram using the 'jmap' binary. Once the jmap command has been
     * executed, the method will return.
     *
     * @param entriesConsumer A consumer of the heap histogram entries that will be called once the full histogram has
     *                        been produced
     */
    public void buildHeapHistogram(final Consumer<List<HeapHistogramEntry>> entriesConsumer) {
        //get stroom's pid
        String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];

        String executable = getExecutable();

        CommandLine command = new CommandLine(executable);
        command.addArguments(new String[]{"-histo:live", pid}, false);

        DefaultExecutor executor = new DefaultExecutor();
        executor.setExitValue(0);
        ExecuteWatchdog watchdog = new ExecuteWatchdog(120000);
        executor.setWatchdog(watchdog);

        //ensure the process is killed if stroom is shutting down
        executor.setProcessDestroyer(new ShutdownHookProcessDestroyer());

        ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
        ByteArrayOutputStream stdErr = new ByteArrayOutputStream();
        PumpStreamHandler pumpStreamHandler = new PumpStreamHandler(stdOut, stdErr);
        executor.setStreamHandler(pumpStreamHandler);

        DefaultExecuteResultHandler executeResultHandler = new JMapResultHandler(
                watchdog, stdOut, stdErr, entriesConsumer, stroomPropertyService);

        try {
            LOGGER.debug("Executing a heap histogram using command [%s]", command.toString());
            executor.execute(command, executeResultHandler);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Error executing command %s", command.toString()), e);
        }
    }

    public List<HeapHistogramEntry> buildHeapHistogram() {
        String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];

        String executable = getExecutable();
        CommandLine command = new CommandLine(executable);
        command.addArguments(new String[]{"-histo:live", pid}, false);

        DefaultExecutor executor = new DefaultExecutor();
        executor.setExitValue(0);
        ExecuteWatchdog watchdog = new ExecuteWatchdog(120000);
        executor.setWatchdog(watchdog);
        //ensure the process is killed if stroom is shutting down
        executor.setProcessDestroyer(new ShutdownHookProcessDestroyer());

        ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
        ByteArrayOutputStream stdErr = new ByteArrayOutputStream();
        PumpStreamHandler pumpStreamHandler = new PumpStreamHandler(stdOut, stdErr);
        executor.setStreamHandler(pumpStreamHandler);

        List<HeapHistogramEntry> heapHistogramEntries = null;
        try {
            LOGGER.debug("Executing a heap histogram using command [%s]", command.toString());
            int exitCode = executor.execute(command);
            if (executor.isFailure(exitCode) && watchdog.killedProcess()) {
                // it was killed on purpose by the watchdog
            } else if (exitCode == 0) {
                heapHistogramEntries = processSuccess(stdOut, stdErr);
            } else {
                logError(stdOut, stdErr, watchdog);
               heapHistogramEntries = Collections.emptyList();
            }
        } catch (Exception e) {
            logError(stdOut, stdErr, watchdog);
            throw new RuntimeException(String.format("Error executing command %s", command.toString()), e);
        }
        return heapHistogramEntries;
    }

    private String getExecutable() {
        String executable = stroomPropertyService.getProperty(JMAP_EXECUTABLE_PROP_KEY);

        if (executable == null || executable.isEmpty()) {
            throw new RuntimeException(String.format("Property %s has no value", JMAP_EXECUTABLE_PROP_KEY));
        }
        return executable;
    }

    private void logError(final ByteArrayOutputStream stdOut,
                          final ByteArrayOutputStream stdErr,
                          final ExecuteWatchdog watchdog) {
        if (watchdog != null && watchdog.killedProcess()) {
            LOGGER.error("The jmap call timed out");
        } else {
            String stdOutStr;
            String stdErrStr;
            try {
                stdOutStr = getTruncatedStr(getStringOutput(stdOut));
            } catch (Exception e) {
                stdOutStr = "Unable to get stdOut str due to error " + e.getMessage();
            }
            try {
                stdErrStr = getTruncatedStr(getStringOutput(stdErr));
            } catch (Exception e) {
                stdErrStr = "Unable to get stdErr str due to error " + e.getMessage();
            }
            LOGGER.error("The jmap call failed with stdout [%s] and stderr [%s]", stdOutStr, stdErrStr);
        }

    }

    private List<HeapHistogramEntry> processSuccess(final ByteArrayOutputStream stdOut,
                                                    final ByteArrayOutputStream stdErr) {
        String result;
        String error;
        List<HeapHistogramEntry> heapHistogramEntries = null;
        try {
            result = getStringOutput(stdOut);
            error = getStringOutput(stdErr);

            if (error != null && !error.isEmpty()) {
                throw new RuntimeException(String.format("jmap completed with exit code 0 but stderr is not empty [%s]",
                        getTruncatedStr(error)));
            } else if (result == null || result.isEmpty()) {
                throw new RuntimeException("jmap completed with exit code 0 but stdout is empty");
            }
            heapHistogramEntries = processStdOut(result);

        } catch (RuntimeException e) {
            LOGGER.error("Error handling result of jmap call", e);
        }
        return heapHistogramEntries;
    }

    private String getStringOutput(final ByteArrayOutputStream outputStream) {
        try {
            return outputStream.toString(StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Error extracting stream output as UTF-8", e);
        }
    }

    private static String getTruncatedStr(final String str) {
        if (str != null && str.length() > STRING_TRUNCATE_LIMIT) {
            return str.substring(0, STRING_TRUNCATE_LIMIT) + "...TRUNCATED...";
        } else {
            return str;
        }
    }

    private static Predicate<String> getClassNameMatchPredicate(final StroomPropertyService stroomPropertyService) {
        String classNameRegexStr = stroomPropertyService.getProperty(CLASS_NAME_MATCH_REGEX_PROP_KEY);

        if (classNameRegexStr == null || classNameRegexStr.isEmpty()) {
            //no prop value so return an always true predicate
            return str -> true;
        } else {
            try {
                return Pattern.compile(classNameRegexStr).asPredicate();
            } catch (Exception e) {
                throw new RuntimeException(String.format("Error compiling regex string [%s]", classNameRegexStr), e);
            }
        }
    }

    private List<HeapHistogramEntry> processStdOut(final String stdOut) {
        Preconditions.checkNotNull(stdOut);

        try {
            Pattern matchPattern = Pattern.compile("\\s+\\d+:\\s+(?<instances>\\d+)\\s+(?<bytes>\\d+)\\s+(?<class>.*)");
            Predicate<String> classNamePredicate = getClassNameMatchPredicate(stroomPropertyService);

            String[] lines = stdOut.split("\\r?\\n");

            LOGGER.debug("processing %s lines of stdout", lines.length);

            List<HeapHistogramService.HeapHistogramEntry> histogramEntries = Arrays.stream(lines)
                    .map(line -> {
                        Matcher matcher = matchPattern.matcher(line);
                        if (matcher.matches()) {
                            //if this is a data row then extract the values of interest
                            final long instances = Long.parseLong(matcher.group("instances"));
                            final long bytes = Long.parseLong(matcher.group("bytes"));
                            final String className = matcher.group("class");
                            return new HeapHistogramService.HeapHistogramEntry(className, instances, bytes);
                        } else {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .filter(heapHistogramEntry -> classNamePredicate.test(heapHistogramEntry.getClassName()))
                    .collect(Collectors.toList());

            LOGGER.debug("histogramEntries size [%s]", histogramEntries.size());
            if (histogramEntries.size() == 0) {
                LOGGER.error("Something has gone wrong filtering the heap histogram, zero entries returned");
            }

            return histogramEntries;

        } catch (Exception e) {
            throw new RuntimeException(String.format("Error processing stdOut string [%s]",
                    getTruncatedStr(stdOut)), e);
        }
    }


    private static class JMapResultHandler extends DefaultExecuteResultHandler {

        private final ExecuteWatchdog watchdog;
        private final ByteArrayOutputStream stdOut;
        private final ByteArrayOutputStream stdErr;
        private final Consumer<List<HeapHistogramEntry>> entriesConsumer;
        private final StroomPropertyService stroomPropertyService;

        public JMapResultHandler(final ExecuteWatchdog watchdog,
                                 final ByteArrayOutputStream stdOut,
                                 final ByteArrayOutputStream stdErr,
                                 final Consumer<List<HeapHistogramEntry>> entriesConsumer,
                                 final StroomPropertyService stroomPropertyService) {
            this.watchdog = watchdog;
            this.stdOut = stdOut;
            this.stdErr = stdErr;
            this.entriesConsumer = entriesConsumer;
            this.stroomPropertyService = stroomPropertyService;
        }

        private String getStdOutStr() {
            try {
                return stdOut.toString(StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("Error extracting stdOut as UTF-8", e);
            }
        }

        private String getStdErrStr() {
            try {
                return stdErr.toString(StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("Error extracting stdErr as UTF-8", e);
            }
        }

        @Override
        public void onProcessComplete(final int exitValue) {
            super.onProcessComplete(exitValue);
            String result;
            String error;
            List<HeapHistogramEntry> heapHistogramEntries = null;
            try {
                result = getStdOutStr();
                error = getStdErrStr();

                if (error != null && !error.isEmpty()) {
                    throw new RuntimeException(String.format("jmap completed with exit code 0 but stderr is not empty [%s]",
                            getTruncatedStr(error)));
                } else if (result == null || result.isEmpty()) {
                    throw new RuntimeException("jmap completed with exit code 0 but stdout is empty");
                }
                heapHistogramEntries = processStdOut(result);
                entriesConsumer.accept(heapHistogramEntries);
            } catch (RuntimeException e) {
                LOGGER.error("Error handling result of jmap call", e);
            }
        }

        @Override
        public void onProcessFailed(final ExecuteException e) {
            super.onProcessFailed(e);
            if (watchdog != null && watchdog.killedProcess()) {
                LOGGER.error("The jmap call timed out");
            } else {
                String stdOutStr;
                String stdErrStr;
                try {
                    stdOutStr = getTruncatedStr(getStdOutStr());
                } catch (Exception e1) {
                    stdOutStr = "Unable to get stdOut str due to error " + e.getMessage();
                }
                try {
                    stdErrStr = getTruncatedStr(getStdErrStr());
                } catch (Exception e1) {
                    stdErrStr = "Unable to get stdErr str due to error " + e.getMessage();
                }
                LOGGER.error("The jmap call failed with stdout [%s] and stderr [%s]", stdOutStr, stdErrStr);
            }
        }


        private List<HeapHistogramEntry> processStdOut(final String stdOut) {
            Preconditions.checkNotNull(stdOut);

            try {
                Pattern matchPattern = Pattern.compile("\\s+\\d+:\\s+(?<instances>\\d+)\\s+(?<bytes>\\d+)\\s+(?<class>.*)");
                Predicate<String> classNamePredicate = getClassNameMatchPredicate(stroomPropertyService);

                String[] lines = stdOut.split("\\r?\\n");

                LOGGER.debug("processing %s lines of stdout", lines.length);

                List<HeapHistogramService.HeapHistogramEntry> histogramEntries = Arrays.stream(lines)
                        .map(line -> {
                            Matcher matcher = matchPattern.matcher(line);
                            if (matcher.matches()) {
                                //if this is a data row then extract the values of interest
                                final long instances = Long.parseLong(matcher.group("instances"));
                                final long bytes = Long.parseLong(matcher.group("bytes"));
                                final String className = matcher.group("class");
                                return new HeapHistogramService.HeapHistogramEntry(className, instances, bytes);
                            } else {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .filter(heapHistogramEntry -> classNamePredicate.test(heapHistogramEntry.getClassName()))
                        .collect(Collectors.toList());

                LOGGER.debug("histogramEntries size [%s]", histogramEntries.size());
                if (histogramEntries.size() == 0) {
                    LOGGER.error("Something has gone wrong filtering the heap histogram, zero entries returned");
                }

                return histogramEntries;

            } catch (Exception e) {
                throw new RuntimeException(String.format("Error processing stdOut string [%s]",
                        getTruncatedStr(stdOut)), e);
            }
        }
    }

    static class HeapHistogramEntry {
        private final String className;
        private final long instances;
        private final long bytes;

        public HeapHistogramEntry(final String className, final long instances, final long bytes) {
            this.className = Preconditions.checkNotNull(className);
            this.instances = instances;
            this.bytes = bytes;
        }

        public String getClassName() {
            return className;
        }

        public long getInstances() {
            return instances;
        }

        public long getBytes() {
            return bytes;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final HeapHistogramEntry that = (HeapHistogramEntry) o;

            if (instances != that.instances) return false;
            if (bytes != that.bytes) return false;
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
