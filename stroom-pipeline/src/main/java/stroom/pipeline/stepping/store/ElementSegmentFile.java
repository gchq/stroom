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
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * A single element's segmented data file: raw record bytes appended to a data file, with an
 * in-memory index of record end offsets for O(1) random access by record index.
 */
final class ElementSegmentFile {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElementSegmentFile.class);

    private final Path dataFile;
    private final FileChannel channel;
    // endOffsets.get(s) = exclusive end byte offset of segment s; segment s = recordIndex - baseRecordIndex.
    private final List<Long> endOffsets = new ArrayList<>();
    // The record index of the first record written (segment 0); may be non-zero (e.g. reader detectors
    // are 1-based). -1 until the first append.
    private long baseRecordIndex = -1;
    private long size;

    ElementSegmentFile(final Path dataFile) throws IOException {
        this.dataFile = dataFile;
        this.channel = FileChannel.open(dataFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE);
    }

    void append(final long recordIndex, final byte[] bytes) {
        if (baseRecordIndex < 0) {
            baseRecordIndex = recordIndex;
        }
        try {
            final ByteBuffer buffer = ByteBuffer.wrap(bytes);
            long position = size;
            while (buffer.hasRemaining()) {
                position += channel.write(buffer, position);
            }
            size = position;
            endOffsets.add(size);
        } catch (final IOException e) {
            throw new StepDataStoreException(LogUtil.message(
                    "Unable to write to stepping store file {}", FileUtil.getCanonicalPath(dataFile)), e);
        }
    }

    byte[] read(final long recordIndex) {
        final int index = (int) (recordIndex - baseRecordIndex);
        final long start = index == 0 ? 0L : endOffsets.get(index - 1);
        final long end = endOffsets.get(index);
        final int length = (int) (end - start);
        final ByteBuffer buffer = ByteBuffer.allocate(length);
        try {
            long position = start;
            while (buffer.hasRemaining()) {
                final int read = channel.read(buffer, position);
                if (read < 0) {
                    throw new StepDataStoreException(LogUtil.message(
                            "Unexpected end of stepping store file {} reading record {}",
                            FileUtil.getCanonicalPath(dataFile), recordIndex));
                }
                position += read;
            }
        } catch (final IOException e) {
            throw new StepDataStoreException(LogUtil.message(
                    "Unable to read stepping store file {}", FileUtil.getCanonicalPath(dataFile)), e);
        }
        return buffer.array();
    }

    long recordCount() {
        return endOffsets.size();
    }

    /**
     * @return the next expected (contiguous) record index, or -1 if nothing written yet.
     */
    long nextRecordIndex() {
        return baseRecordIndex < 0 ? -1 : baseRecordIndex + endOffsets.size();
    }

    /**
     * @return true if the given record index falls within the range written to this file.
     */
    boolean contains(final long recordIndex) {
        return baseRecordIndex >= 0
                && recordIndex >= baseRecordIndex
                && recordIndex < baseRecordIndex + endOffsets.size();
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
