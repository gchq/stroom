/*
 * Copyright 2022 Crown Copyright
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

package stroom.proxy.app.event;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

class EventAppender {

    private final Path file;
    private final Instant createTime;
    private final EventStoreConfig eventStoreConfig;

    private OutputStream outputStream;
    private long eventCount = 0;
    private long byteCount = 0;

    public EventAppender(final Path file,
                         final Instant createTime,
                         final EventStoreConfig eventStoreConfig) {
        this.file = file;
        this.createTime = createTime;
        this.eventStoreConfig = eventStoreConfig;
    }

    public synchronized void write(final byte[] bytes) throws IOException {
        if (outputStream == null) {
            open();
        }

        outputStream.write(bytes);
        outputStream.flush();

        eventCount++;
        byteCount += bytes.length;
    }

    public synchronized void open() throws IOException {
        if (outputStream == null) {
            if (Files.isRegularFile(file)) {
                outputStream = new BufferedOutputStream(Files.newOutputStream(
                        file,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND));
            } else {
                outputStream = new BufferedOutputStream(Files.newOutputStream(
                        file,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE_NEW));
            }
        }
    }

    /**
     * Idempotent. The field is cleared <em>before</em> the close, so a close that fails part way
     * through its flush cannot leave this appender holding a stream that a second call would try to
     * close again - which is what happened, because clearing it afterwards was skipped by the throw.
     */
    public synchronized void close() throws IOException {
        final OutputStream toClose = outputStream;
        if (toClose != null) {
            outputStream = null;
            toClose.close();
        }
    }

    public synchronized boolean shouldRoll(final long addBytes) {
        return createTime.isBefore(Instant.now().minus(eventStoreConfig.getMaxAge()))
               || eventCount >= eventStoreConfig.getMaxEventCount()
               || byteCount + addBytes > eventStoreConfig.getMaxByteCount();
    }

    public synchronized Path closeAndGetFile() {
        try {
            close();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    @Override
    public String toString() {
        return "EventAppender{" +
               "file=" + file +
               ", createTime=" + createTime +
               ", eventCount=" + eventCount +
               ", byteCount=" + byteCount +
               '}';
    }
}
