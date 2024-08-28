/*
 * Copyright 2024 Crown Copyright
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

package stroom.core.tools;

import stroom.util.ArgsUtil;
import stroom.util.date.DateUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.string.CIKey;
import stroom.util.thread.CustomThreadFactory;
import stroom.util.thread.StroomThreadGroup;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.HttpsURLConnection;

public class BenchmarkDataFeed {

    private final AtomicInteger connectedCount = new AtomicInteger(0);
    private final AtomicInteger sendingCount = new AtomicInteger(0);
    private final AtomicLong sendSize = new AtomicLong(0);
    private String serverUrl = "http://somehost/stroom/datafeed";
    private String feed = "TEST_FEED";
    private String compression = "None";
    private boolean debug = false;
    private int batchSize = 10;
    private Long batchFileSize = 1000L;
    private int batchChunkedLength = -1;
    private ExecutorService threadPoolExecutor;
    private long batchStartTime;
    private long batchStopTime;
    private ConcurrentLinkedQueue<DataFeedResult> batchResults;

    public static void main(final String[] args) throws IOException {
        final BenchmarkDataFeed benchmarkDataFeed = new BenchmarkDataFeed();
        final Map<CIKey, String> map = ArgsUtil.parse(args);
        benchmarkDataFeed.setOptionalArgs(map);
        benchmarkDataFeed.run();
    }

    private void log(final String msg) {
        System.out.println("INFO  : " + msg);
    }

    private void logError(final String msg) {
        System.out.println("ERROR : " + msg);
    }

    private void logDebug(final String msg) {
        if (debug) {
            System.out.println("DEBUG : " + msg);
        }
    }

    public void setOptionalArgs(final Map<CIKey, String> args) {
        batchSize = readOptionalArgument(args, "BatchSize", batchSize);
        batchFileSize = readOptionalArgument(args, "BatchFileSize", batchFileSize);
        serverUrl = readOptionalArgument(args, "ServerUrl", serverUrl);
        feed = readOptionalArgument(args, "Feed", feed);
        compression = readOptionalArgument(args, "Compression", compression);
        batchChunkedLength = readOptionalArgument(args, "BatchChunkedLength", batchChunkedLength);
        debug = readOptionalArgument(args, "Debug", debug);
    }

    private String readOptionalArgument(final Map<CIKey, String> args, final String name, final String defValue) {
        final CIKey key = CIKey.of(name);
        if (!args.containsKey(key)) {
            return defValue;
        }
        final String val = args.get(key);
        log("Set " + name + "=" + val);
        return val;
    }

    private int readOptionalArgument(final Map<CIKey, String> args, final String name, final int defValue) {
        final CIKey key = CIKey.of(name);
        if (!args.containsKey(key)) {
            return defValue;
        }
        final int val = Integer.parseInt(args.get(key));
        log("Set " + name + "=" + val);
        return val;
    }

    private long readOptionalArgument(final Map<CIKey, String> args, final String name, final long defValue) {
        final CIKey key = CIKey.of(name);
        if (!args.containsKey(key)) {
            return defValue;
        }
        final long val = Long.parseLong(args.get(key));
        log("Set " + name + "=" + val);
        return val;
    }

    private boolean readOptionalArgument(final Map<CIKey, String> args, final String name, final boolean defValue) {
        final CIKey key = CIKey.of(name);
        if (!args.containsKey(key)) {
            return defValue;
        }
        final boolean val = Boolean.parseBoolean(args.get(key));
        log("Set " + name + "=" + val);
        return val;
    }

    private byte[] buildSampleData() {
        final StringBuilder builder = new StringBuilder();
        for (int i = 1; i < 1000; i++) {
            builder.append("BenchmarkDataFeed,")
                    .append(i)
                    .append(",")
                    .append(DateUtil.createNormalDateTimeString())
                    .append(
                            "\n");
        }
        return builder.toString().getBytes(StreamUtil.DEFAULT_CHARSET);

    }

    public void sendFile() {
        final long startTime = System.currentTimeMillis();
        boolean connected = false;
        boolean sent = false;
        try {
            logDebug("Sending to " + serverUrl);

            final URL url = new URL(serverUrl);
            final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            if (connection instanceof HttpsURLConnection) {
                ((HttpsURLConnection) connection).setHostnameVerifier((arg0, arg1) -> true);
            }
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/audit");
            connection.setDoOutput(true);
            connection.setDoInput(true);

            if (batchChunkedLength > 0) {
                connection.setChunkedStreamingMode(batchChunkedLength);
            }

            connection.addRequestProperty("Feed", feed);
            if (compression.equalsIgnoreCase("gzip")) {
                connection.addRequestProperty("Compression", "gzip");
            }

            connection.connect();

            connectedCount.incrementAndGet();
            connected = true;

            final byte[] sampleData = buildSampleData();

            OutputStream out = connection.getOutputStream();

            if (compression.equalsIgnoreCase("gzip")) {
                out = new GZIPOutputStream(out);
            }

            long bytesWritten = 0;
            while (bytesWritten < batchFileSize) {
                if (bytesWritten == 0) {
                    sendingCount.incrementAndGet();
                    sent = true;
                }
                final int writeSize = (int) Math.min(batchFileSize, sampleData.length);

                out.write(sampleData, 0, writeSize);
                bytesWritten += writeSize;
                sendSize.addAndGet(writeSize);
            }

            out.flush();
            out.close();

            final int response = connection.getResponseCode();
            final String msg = connection.getResponseMessage();

            connection.disconnect();

            final DataFeedResult result = new DataFeedResult();
            result.time = System.currentTimeMillis() - startTime;
            result.response = response;
            result.message = msg;
            batchResults.add(result);

            logDebug("Finished in " + result.time + " response was " + result.response);

        } catch (final IOException | RuntimeException e) {
            final DataFeedResult result = new DataFeedResult();
            result.time = System.currentTimeMillis() - startTime;
            result.response = -1;
            result.message = e.getMessage();
            batchResults.add(result);

            logDebug("Exception " + e.getMessage());

            if (debug) {
                e.printStackTrace();
            }
        } finally {
            if (connected) {
                connectedCount.decrementAndGet();
            }
            if (sent) {
                sendingCount.decrementAndGet();
            }
            if (threadPoolExecutor == null) {
                batchStopTime = System.currentTimeMillis();
            }
        }

    }

    public Runnable buildClient() {
        return () -> {
            do {
                sendFile();
            } while (isBatchRunning());
            logDebug("Finished");
        };
    }

    public void startBatch() {
        if (threadPoolExecutor != null) {
            logError("Batch already running");
            return;
        }
        log("Start Batch");

        batchStartTime = System.currentTimeMillis();
        batchStopTime = 0;
        batchResults = new ConcurrentLinkedQueue<>();

        final CustomThreadFactory threadFactory = new CustomThreadFactory("BatchQueue", StroomThreadGroup.instance(),
                Thread.NORM_PRIORITY - 1);
        threadFactory.setDaemon(true);

        threadPoolExecutor = Executors.newFixedThreadPool(batchSize, threadFactory);

        for (int i = 0; i < batchSize; i++) {
            threadPoolExecutor.submit(buildClient());
        }
        log("Submitted " + batchSize + " jobs");

    }

    private String formatBytes(final int bytes) {
        return bytes + " (" + ModelStringUtil.formatIECByteSizeString((long) bytes) + ")";
    }

    private String formatBytes(final long bytes) {
        return bytes + " (" + ModelStringUtil.formatIECByteSizeString(bytes) + ")";
    }

    public void statusConfig() {
        log("ServerUrl=" + serverUrl);
        log("BatchSize=" + formatBytes(batchSize));
        log("BatchFileSize=" + formatBytes(batchFileSize));
        log("BatchChunkedLength=" + formatBytes(batchChunkedLength));
        if (threadPoolExecutor != null) {
            log("ThreadPoolExecutor.shutdown=" + threadPoolExecutor.isShutdown() + ",terminated="
                    + threadPoolExecutor.isTerminated());
        }
        log("ConnectedCount=" + connectedCount);
        log("SendingCount=" + sendingCount);
        log("SendSize=" + formatBytes(sendSize.longValue()));

    }

    public void statusBatch() {
        if (batchResults == null) {
            log("batchResults null");
            return;
        }
        long count = 0;
        long totalTime = 0;
        long maxTime = 0;

        final HashMap<Integer, Integer> responseMap = new HashMap<>();

        final Set<String> messageSet = new HashSet<>();

        for (final DataFeedResult dataFeedResult : batchResults) {
            count++;
            totalTime += dataFeedResult.time;
            if (dataFeedResult.time > maxTime) {
                maxTime = dataFeedResult.time;
            }
            if (!responseMap.containsKey(dataFeedResult.response)) {
                responseMap.put(dataFeedResult.response, 1);
            } else {
                final int value = responseMap.get(dataFeedResult.response);
                responseMap.put(dataFeedResult.response, value + 1);
            }
            if (dataFeedResult.message != null) {
                messageSet.add(dataFeedResult.message);
            }
        }

        final long timeSoFar = threadPoolExecutor != null
                ? System.currentTimeMillis()
                : batchStopTime;
        final long secondsSoFar = (timeSoFar - batchStartTime) / 1000;

        log("Count    " + count);
        if (count > 0) {
            log("Avg Time " + (totalTime / count));
        }
        log("Max Time " + maxTime);

        if (secondsSoFar > 0) {
            final long bytesPerSecond = (count * batchFileSize) / secondsSoFar;
            final long filesPerSecond = count / secondsSoFar;

            log("Bytes ps " + formatBytes(bytesPerSecond));
            log("Files ps " + filesPerSecond);
        }

        for (final Entry<Integer, Integer> entry : responseMap.entrySet()) {
            log("Response " + entry.getKey() + " Count " + entry.getValue());
        }

        if (!messageSet.isEmpty()) {
            log("Message Set " + messageSet);
        }

    }

    public boolean isBatchRunning() {
        return threadPoolExecutor != null && !threadPoolExecutor.isShutdown();
    }

    public void stopBatch() {
        if (threadPoolExecutor == null) {
            logError("Batch not running");
            return;
        }
        log("Stop Batch");

        threadPoolExecutor.shutdownNow();
        threadPoolExecutor = null;
    }

    public void run() throws IOException {
        try (InputStreamReader reader = new InputStreamReader(System.in, StreamUtil.DEFAULT_CHARSET)) {
            final BufferedReader readerB = new BufferedReader(reader);

            String line = "";
            while (line != null && !line.equals("QUIT")) {
                System.out.print("> ");

                line = readerB.readLine();

                if (line != null) {
                    line = line.trim();
                    processCommand(line);
                }
            }
        }
    }

    public void processCommand(final String line) {
        final String upperLine = line.toUpperCase();
        if (upperLine.startsWith("SET ")) {
            final String[] args = line.substring(4).split(" ");
            final Map<CIKey, String> map = ArgsUtil.parse(args);
            setOptionalArgs(map);
        }
        if (upperLine.startsWith("START")) {
            startBatch();
        }
        if (upperLine.startsWith("STOP")) {
            stopBatch();
        }
        if (upperLine.startsWith("QUIT")) {
            log("Quit");
            System.exit(0);
        }

        if (upperLine.isEmpty() || upperLine.startsWith("STATUS")) {
            statusConfig();
            statusBatch();
        }
    }

    private static class DataFeedResult {

        public int response;
        public long time;
        public String message;
    }
}
