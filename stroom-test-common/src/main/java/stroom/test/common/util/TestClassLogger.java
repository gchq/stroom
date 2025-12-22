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

package stroom.test.common.util;

import stroom.test.common.util.db.DbTestUtil;
import stroom.util.logging.LogUtil;

import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TestClassLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestClassLogger.class);

    // Can't use createTempDir as it needs to be the same on multiple JVMs
    // Create this dir if you want test class logging to happen
    private static final Path TEST_LOG_DIR = Path.of("/tmp", "stroomTestLog");

    // Assume true until we check if the dir is present, else false
    private static volatile boolean doTestClassLogging = true;

    private static final Map<Long, List<String>> THREAD_ID_TO_LOG_MAP = new ConcurrentHashMap<>();

    /**
     * Logs the test class and method to a file with the time it was started.
     * The file name contains the gradle worker id and the thread id, so you
     * can see which test classes/methods are running on which jvms/threads.
     * Useful in diagnosing concurrency issues.
     */
    public static void logTestClasses(final TestInfo testInfo) {
        if (doTestClassLogging) {
            // As this is running on multiple jvms, it is non-trivial to clear out the dir
            // before we run so need to do it manually
            try {
                if (!Files.isDirectory(TEST_LOG_DIR)) {
                    doTestClassLogging = false;
                }
            } catch (final Exception e) {
                doTestClassLogging = false;
            }
            final long threadId = Thread.currentThread().getId();
            final String logLine = Instant.now().toString()
                    + " - "
                    + testInfo.getTestMethod()
                    .map(m -> m.getDeclaringClass().getName() + "#" + m.getName()).orElse("UNKNOWN");

            // Log the line against this thread id
            THREAD_ID_TO_LOG_MAP.computeIfAbsent(threadId, k -> new ArrayList<>())
                    .add(logLine);
        }
    }

    /**
     * Writes one file per gradle worker and thread containing all the test classes and methods
     */
    public static void writeTestClassesLogToDisk() {
        if (Files.isDirectory(TEST_LOG_DIR)) {
            THREAD_ID_TO_LOG_MAP.forEach((threadId, logLines) -> {
                final String filename = "worker-"
                        + DbTestUtil.getGradleWorker()
                        + "_thread-"
                        + threadId
                        + ".log";
                final Path logFilePath = TEST_LOG_DIR.resolve(filename);
                try {
                    LOGGER.info("Writing test classes log to file {}", logFilePath);
                    Files.write(
                            logFilePath,
                            logLines,
                            new StandardOpenOption[]{
                                    StandardOpenOption.CREATE,
                                    StandardOpenOption.APPEND});
                } catch (final IOException e) {
                    throw new RuntimeException(LogUtil.message("Error writing file {}: {}",
                            logFilePath.toAbsolutePath(), e.getMessage()), e);
                }
            });
        }
    }
}
