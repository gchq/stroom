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

package stroom.pipeline.stepping.store;

import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single element's segmented data file: raw record bytes appended to a data file, with an
 * in-memory index of record end offsets for O(1) random access by record index.
 */
final class ElementSegmentFile {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElementSegmentFile.class);

    private final Path dataFile;
    // Not final: a FileChannel is an InterruptibleChannel, so the JDK CLOSES it - permanently, for every
    // user - when a thread is interrupted while blocked in I/O on it. Stepping interrupts tasks (session
    // teardown, and anything outside stepping that terminates tasks), and this file is shared by every sweep
    // in the session, so one interrupt could otherwise leave the whole session unreadable. It is reopened on
    // demand instead; see channel().
    private FileChannel channel;
    // recordIndex -> {start offset, length}. Keyed by record index rather than derived from position, so the
    // file can hold RECORDS IT WAS NEVER GIVEN IN ORDER, and gaps between them. A full sweep still writes
    // 0,1,2..., but materialising one record on demand (see stepping-design.md §11) writes record N into an
    // otherwise empty file, and later record M with nothing in between. Nothing here assumes contiguity;
    // callers that require it enforce it themselves.
    private final Map<Long, long[]> extents = new LinkedHashMap<>();
    // The record index of the first record written (segment 0); may be non-zero (e.g. reader detectors
    // are 1-based). -1 until the first append.
    private long baseRecordIndex = -1;
    private long size;

    ElementSegmentFile(final Path dataFile) throws IOException {
        this.dataFile = dataFile;
        this.channel = open(dataFile);
    }

    private static FileChannel open(final Path dataFile) throws IOException {
        return FileChannel.open(dataFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE);
    }

    /**
     * @return an open channel, reopening it if an interrupt closed it underneath us.
     * <p>
     * Reopening is safe because everything positional lives in memory - {@code size} is the append position
     * and {@code endOffsets} the record index - so a fresh channel resumes exactly where the old one left
     * off. It deliberately does <b>not</b> reopen for a thread that is itself interrupted: that thread is
     * being asked to stop, so it should fail and unwind rather than resurrect the channel only for the JDK
     * to close it again on the next call.
     */
    private FileChannel channel() throws IOException {
        if (!channel.isOpen()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new ClosedChannelException();
            }
            LOGGER.debug(() -> LogUtil.message(
                    "Reopening stepping store file {} after an interrupt closed it",
                    FileUtil.getCanonicalPath(dataFile)));
            channel = open(dataFile);
        }
        return channel;
    }

    void append(final long recordIndex, final byte[] bytes) {
        if (baseRecordIndex < 0) {
            baseRecordIndex = recordIndex;
        }
        try {
            final ByteBuffer buffer = ByteBuffer.wrap(bytes);
            long position = size;
            while (buffer.hasRemaining()) {
                position += channel().write(buffer, position);
            }
            extents.put(recordIndex, new long[]{size, position - size});
            size = position;
        } catch (final IOException e) {
            throw new StepDataStoreException(LogUtil.message(
                    "Unable to write to stepping store file {}: {}: {}",
                    FileUtil.getCanonicalPath(dataFile), e.getClass().getSimpleName(), e.getMessage()), e);
        }
    }

    byte[] read(final long recordIndex) {
        final long[] extent = extents.get(recordIndex);
        if (extent == null) {
            throw new StepDataStoreException(LogUtil.message(
                    "Stepping store file {} holds no record {}",
                    FileUtil.getCanonicalPath(dataFile), recordIndex));
        }
        final long start = extent[0];
        final int length = (int) extent[1];
        final ByteBuffer buffer = ByteBuffer.allocate(length);
        try {
            long position = start;
            while (buffer.hasRemaining()) {
                final int read = channel().read(buffer, position);
                if (read < 0) {
                    throw new StepDataStoreException(LogUtil.message(
                            "Unexpected end of stepping store file {} reading record {}",
                            FileUtil.getCanonicalPath(dataFile), recordIndex));
                }
                position += read;
            }
        } catch (final IOException e) {
            throw new StepDataStoreException(LogUtil.message(
                    "Unable to read stepping store file {} record {} (base {}, segments {}): {}: {}",
                    FileUtil.getCanonicalPath(dataFile), recordIndex, baseRecordIndex, extents.size(),
                    e.getClass().getSimpleName(), e.getMessage()), e);
        }
        return buffer.array();
    }

    /**
     * @return the highest record index held, or -1 if none. Not the same as the base plus the count once a
     * file holds records materialised individually, which may have gaps.
     */
    long maxRecordIndex() {
        return extents.keySet().stream().mapToLong(Long::longValue).max().orElse(-1);
    }

    /**
     * @return the lowest record index held, or -1 if none.
     */
    long minRecordIndex() {
        return extents.keySet().stream().mapToLong(Long::longValue).min().orElse(-1);
    }

    private boolean outOfBandWritten;

    long recordCount() {
        return extents.size();
    }

    /**
     * @return the next expected (contiguous) record index, or -1 if nothing written yet. Only meaningful
     * while {@link #isContiguouslyWritten()} - once a record has been materialised out of band the file has
     * holes and "the next index" is not a property it has.
     */
    long nextRecordIndex() {
        return baseRecordIndex < 0 ? -1 : baseRecordIndex + extents.size();
    }

    /**
     * Records that this file has received a record written <b>out of band</b> - materialised on demand for
     * one step rather than appended by a sweep walking the stream.
     */
    void markOutOfBandWrite() {
        outOfBandWritten = true;
    }

    /**
     * @return true if every record in this file was appended in sequence, so "the next record index" is a
     * meaningful thing to assert about. False once anything has been materialised on demand: a sweep and a
     * single-record replay share the un-fingerprinted state file (and may share an element file, when a sweep
     * follows a replay under the same fingerprint), and the sweep's records legitimately do not follow the
     * replay's. Also false if the file simply has holes, which covers a file re-opened from disk after such a
     * write.
     */
    boolean isContiguouslyWritten() {
        return !outOfBandWritten && (baseRecordIndex < 0 || recordCount() == maxRecordIndex() - baseRecordIndex + 1);
    }

    /**
     * @return true if the given record index falls within the range written to this file.
     */
    boolean contains(final long recordIndex) {
        // Asks what is actually here, not what the range implies - a file with holes has records inside its
        // span that it does not hold.
        return extents.containsKey(recordIndex);
    }

    long size() {
        return size;
    }

    Path dataFile() {
        return dataFile;
    }

    void closeQuietly() {
        try {
            channel.close();
        } catch (final IOException e) {
            LOGGER.debug(() -> LogUtil.message(
                    "Error closing stepping store file {}", FileUtil.getCanonicalPath(dataFile)), e);
        }
    }
}
