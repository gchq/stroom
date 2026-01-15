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

package stroom.task.api;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class TaskProgressHandler implements Consumer<Long> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TaskProgressHandler.class);
    private static final long INTERVAL_MS = 1000;

    private final TaskContext taskContext;
    private final String prefix;
    private long totalBytes = 0;
    private long lastProgressTime = System.currentTimeMillis();

    public TaskProgressHandler(final TaskContext taskContext, final String prefix) {
        this.taskContext = taskContext;
        this.prefix = prefix;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    @Override
    public void accept(final Long bytes) {
        totalBytes += bytes;
        progress(totalBytes);
    }

    private void progress(final long totalBytes) {
        final long timeNow = System.currentTimeMillis();

        if (lastProgressTime + INTERVAL_MS < timeNow) {
            lastProgressTime = timeNow;
            final Supplier<String> messageSupplier = () ->
                    prefix + " - " + ModelStringUtil.formatIECByteSizeString(totalBytes);
            if (taskContext != null) {
                taskContext.info(messageSupplier);

                if (Thread.currentThread().isInterrupted()) {
                    throw new UncheckedIOException(new IOException("Progress Stopped"));
                }
            }
            LOGGER.debug(messageSupplier);
        }
    }
}
