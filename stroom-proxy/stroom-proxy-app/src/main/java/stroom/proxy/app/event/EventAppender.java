/*
 * Copyright 2026 Crown Copyright
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

import stroom.proxy.app.handler.Durability;
import stroom.util.logging.LogUtil;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One feed's open event file. While open the file carries the {@link #OPEN_SUFFIX}, so nothing on
 * disk that ends in {@code .log} is ever being written; {@link #close()} renames it to its final
 * name, which is what publishes it to the roll.
 * <p>
 * The file is opened with {@code DSYNC} when the durability requires it, so the flush in
 * {@link #write} reaches the disk rather than the OS: the sender is answered as soon as
 * {@code write} returns, and an acknowledgement must survive a power cut. Data only, not
 * metadata: the bytes are what make the event recoverable, not the file's length.
 * </p>
 */
class EventAppender {

    static final String OPEN_SUFFIX = ".open";

    private final Path file;
    private final Path openFile;
    private final Instant createTime;
    private final Duration maxAge;
    private final long maxEventCount;
    private final long maxByteCount;
    private final Durability durability;

    private OutputStream outputStream;
    private boolean closed;
    private long eventCount = 0;
    private long byteCount = 0;

    /**
     * @param file         The file's final name, which it takes on close.
     * @param maxByteCount The most this file may hold; a write that would exceed it is refused by
     *                     {@link #shouldRoll} first.
     */
    EventAppender(final Path file,
                  final Instant createTime,
                  final Duration maxAge,
                  final long maxEventCount,
                  final long maxByteCount,
                  final Durability durability) {
        this.file = file;
        this.openFile = openFileOf(file);
        this.createTime = createTime;
        this.maxAge = maxAge;
        this.maxEventCount = maxEventCount;
        this.maxByteCount = maxByteCount;
        this.durability = durability;
    }

    static Path openFileOf(final Path file) {
        return file.resolveSibling(file.getFileName() + OPEN_SUFFIX);
    }

    Path getFile() {
        return file;
    }

    synchronized void write(final byte[] bytes) throws IOException {
        if (closed) {
            throw new IOException(LogUtil.message("Event file '{}' is closed", file));
        }
        if (outputStream == null) {
            final List<StandardOpenOption> options = new ArrayList<>(3);
            options.add(StandardOpenOption.WRITE);
            options.add(StandardOpenOption.CREATE_NEW);
            if (durability.forcesEvents()) {
                options.add(StandardOpenOption.DSYNC);
            }
            outputStream = new BufferedOutputStream(Files.newOutputStream(
                    openFile, options.toArray(new StandardOpenOption[0])));
        }
        outputStream.write(bytes);
        outputStream.flush();
        eventCount++;
        byteCount += bytes.length;
    }

    synchronized boolean shouldRoll(final long addBytes) {
        return createTime.isBefore(Instant.now().minus(maxAge))
               || eventCount >= maxEventCount
               || byteCount + addBytes > maxByteCount;
    }

    /**
     * Close the stream and publish the file under its final name. Idempotent; a file that was never
     * written to leaves nothing behind.
     */
    synchronized void close() throws IOException {
        closed = true;
        final OutputStream toClose = outputStream;
        if (toClose != null) {
            outputStream = null;
            toClose.close();
            Files.move(openFile, file, StandardCopyOption.ATOMIC_MOVE);
        }
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
