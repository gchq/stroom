package stroom.node.server;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TestJMapHistogramService {

    public static final String CLASS_NAME_MATCH_REGEX = "^(sun\\..*|org\\.junit\\..*)$";

    public static final Semaphore semaphore = new Semaphore(0);


    @Test
    public void test() throws InterruptedException {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        String pid = name.split("@")[0];
        System.out.println("pid: " + pid);

        CommandLine command = new CommandLine("jmap");
        command.addArguments(new String[]{"-histo:live", pid}, false);

        DefaultExecutor executor = new DefaultExecutor();
        executor.setExitValue(0);
        ExecuteWatchdog watchdog = new ExecuteWatchdog(60000);
        executor.setWatchdog(watchdog);

        ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
        ByteArrayOutputStream stdErr = new ByteArrayOutputStream();
        PumpStreamHandler pumpStreamHandler = new PumpStreamHandler(stdOut, stdErr);
        executor.setStreamHandler(pumpStreamHandler);

        DefaultExecuteResultHandler executeResultHandler = new JMapResultHandler(watchdog, stdOut, stdErr);

        try {
            executor.execute(command, executeResultHandler);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Error executing command %s", command.toString()), e);
        }

        executeResultHandler.waitFor();


        semaphore.acquire();


    }

    private static class JMapResultHandler extends DefaultExecuteResultHandler {

        private final ExecuteWatchdog watchdog;
        private final ByteArrayOutputStream stdOut;
        private final ByteArrayOutputStream stdErr;

        public JMapResultHandler(final ExecuteWatchdog watchdog,
                                 final ByteArrayOutputStream stdOut,
                                 final ByteArrayOutputStream stdErr) {
            this.watchdog = watchdog;
            this.stdOut = stdOut;
            this.stdErr = stdErr;
        }

        @Override
        public void onProcessComplete(final int exitValue) {
            super.onProcessComplete(exitValue);
            System.out.println("Finished");
            String result;
            try {
                result = stdOut.toString(StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(String.format("Error extracting result as UTF-8"), e);
            }
            System.out.println(result);
            processResult(result);

            TestJMapHistogramService.semaphore.release();
        }

        @Override
        public void onProcessFailed(final ExecuteException e) {
            super.onProcessFailed(e);
            if (watchdog != null && watchdog.killedProcess()) {
                throw new RuntimeException("The print process timed out");
            } else {
                System.out.println("stdOut: " + stdOut.toString());
                System.out.println("stdErr: " + stdErr.toString());
                throw new RuntimeException("The print process failed to do : " + e.getMessage());
            }
        }

        public static void processResult(final String result) {


            try {
                Pattern matchPattern = Pattern.compile("\\s+\\d+:\\s+(?<instances>\\d+)\\s+(?<bytes>\\d+)\\s+(?<class>.*)");
                Predicate<String> matchPredicate = matchPattern.asPredicate();

                Pattern classNamePattern = Pattern.compile(CLASS_NAME_MATCH_REGEX);
                Predicate<String> classNamePredicate = classNamePattern.asPredicate();

                String[] lines = result.split("\\r?\\n");

                System.out.println("Line count: " + lines.length);

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

                System.out.println("List size: " + histogramEntries.size());




//            Pattern pattern = Pattern.compile(".*");

//            Matcher matcher = pattern.matcher(result);
//
//            while (matcher.find()) {
//                long bytes = Long.parseLong(matcher.group("bytes"));
//                String clazz = matcher.group("class");
//                System.out.printf("%s: %s\n", clazz, bytes);
//            }
            } catch (Exception e) {
                throw new RuntimeException(String.format("Error processing result string"), e);
            }
        }
    }

}


