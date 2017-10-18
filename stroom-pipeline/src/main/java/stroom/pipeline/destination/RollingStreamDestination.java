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

package stroom.pipeline.destination;

import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.StreamTarget;
import stroom.streamstore.server.fs.serializable.RASegmentOutputStream;
import stroom.streamstore.shared.StreamAttributeConstants;
import stroom.util.logging.StroomLogger;
import stroom.util.zip.HeaderMap;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicLong;

public class RollingStreamDestination extends RollingDestination {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(RollingStreamDestination.class);

    private static final int ONE_MINUTE = 60000;

    private final StreamKey key;

    private final long frequency;
    private final long maxSize;
    private final StreamStore streamStore;
    private final StreamTarget streamTarget;
    private final String nodeName;
    private final long creationTime;

    private volatile long lastFlushTime;
    private volatile byte[] footer;

    private volatile boolean rolled;

    private final ByteCountOutputStream outputStream;
    private final RASegmentOutputStream segmentOutputStream;
    private final AtomicLong recordCount = new AtomicLong();

    public RollingStreamDestination(final StreamKey key,
                                    final long frequency,
                                    final long maxSize,
                                    final StreamStore streamStore,
                                    final StreamTarget streamTarget,
                                    final String nodeName,
                                    final long creationTime) throws IOException {
        this.key = key;

        this.frequency = frequency;
        this.maxSize = maxSize;
        this.streamStore = streamStore;
        this.streamTarget = streamTarget;
        this.nodeName = nodeName;
        this.creationTime = creationTime;

        if (key.isSegmentOutput()) {
            segmentOutputStream = new RASegmentOutputStream(streamTarget);
            outputStream = new ByteCountOutputStream(segmentOutputStream);
        } else {
            segmentOutputStream = null;
            outputStream = new ByteCountOutputStream(streamTarget.getOutputStream());
        }
    }

    @Override
    Object getKey() {
        return key;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return getOutputStream(null, null);
    }

    @Override
    public OutputStream getOutputStream(final byte[] header, final byte[] footer) throws IOException {
        try {
            if (!rolled) {
                // this.header = header;
                this.footer = footer;

                // If we haven't written yet then create the output stream and
                // write a header if we have one.
                if (header != null && header.length > 0 && outputStream.getBytesWritten() == 0) {
                    // Write the header.
                    write(header);
                }

                // Insert a segment marker before we write the next record regardless of whether the header has actually
                // been written. This is because we always make an allowance for the existence of a header in a segmented
                // stream when viewing data.
                insertSegmentMarker();

                recordCount.incrementAndGet();

                return outputStream;
            }
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
            throw new IOException(t.getMessage(), t);
        }

        return null;
    }

    @Override
    boolean tryFlushAndRoll(final boolean force, final long currentTime) throws IOException {
        IOException exception = null;

        try {
            if (!rolled) {
                // Flush the output if we need to.
                if (force || shouldFlush(currentTime)) {
                    try {
                        flush();
                    } catch (final Throwable e) {
                        exception = handleException(exception, e);
                    }
                }

                // Roll the output if we need to.
                if (force || shouldRoll(currentTime)) {
                    try {
                        roll();
                    } catch (final Throwable e) {
                        exception = handleException(exception, e);
                    }
                }
            }
        } catch (final Throwable t) {
            exception = handleException(exception, t);
        }

        if (exception != null) {
            throw exception;
        }

        return rolled;
    }

    private boolean shouldFlush(final long currentTime) {
        final long lastFlushTime = this.lastFlushTime;
        this.lastFlushTime = currentTime;
        return lastFlushTime > 0 && currentTime - lastFlushTime > ONE_MINUTE;
    }

    private boolean shouldRoll(final long currentTime) {
        final long oldestAllowed = currentTime - frequency;
        return creationTime < oldestAllowed || outputStream.getBytesWritten() > maxSize;
    }

    private void roll() throws IOException {
        rolled = true;
        IOException exception = null;

        try {
            // Writing a segment marker here ensures there is always a marker written before the footer regardless or
            // whether a footer is actually written. We do this because we always make an allowance for a footer for data
            // display purposes.
            insertSegmentMarker();
        } catch (final Throwable e) {
            exception = handleException(exception, e);
        }

        // If we have written then write a footer if we have one.
        if (footer != null && footer.length > 0) {
            // Write the footer.
            try {
                write(footer);
            } catch (final Throwable e) {
                exception = handleException(exception, e);
            }
        }

        // Try and close the output stream.
        try {
            close();
        } catch (final Throwable e) {
            exception = handleException(exception, e);
        }

        // Write meta data to stream target.
        final HeaderMap headerMap = new HeaderMap();
        headerMap.put(StreamAttributeConstants.REC_WRITE, recordCount.toString());
        headerMap.put(StreamAttributeConstants.NODE, nodeName);
        streamTarget.getAttributeMap().putAll(headerMap);
        streamStore.closeStreamTarget(streamTarget);

        if (exception != null) {
            throw exception;
        }
    }

    private void write(final byte[] bytes) throws IOException {
        outputStream.write(bytes, 0, bytes.length);
    }

    private void flush() throws IOException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Flushing: %s", key);
        }
        outputStream.flush();
    }

    private void close() throws IOException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Closing: %s", key);
        }
        outputStream.close();
    }

    private void insertSegmentMarker() throws IOException {
        // Add a segment marker to the output stream if we are segmenting.
        if (segmentOutputStream != null) {
            segmentOutputStream.addSegment();
        }
    }

    @Override
    public String toString() {
        return key.toString();
    }

    private IOException handleException(final IOException existingException, final Throwable newException) {
        LOGGER.error(newException.getMessage(), newException);

        if (existingException != null) {
            return existingException;
        }

        if (newException instanceof IOException) {
            return (IOException) newException;
        }

        return new IOException(newException.getMessage(), newException);
    }
}
