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

package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.util.io.ByteSize;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.zip.ZipUtil;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class ZipWriter implements AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ZipWriter.class);

    private final ZipArchiveOutputStream zipArchiveOutputStream;
    private final byte[] buffer;
    /**
     * Here to aid logging only, may be null
     */
    private final Path path;

    public ZipWriter(final Path path, final byte[] buffer) throws IOException {
        this(ZipUtil.createOutputStream(new BufferedOutputStream(Files.newOutputStream(path))), buffer, path);
    }

    public ZipWriter(final OutputStream outputStream, final byte[] buffer) {
        this(ZipUtil.createOutputStream(outputStream), buffer, null);
    }

    public ZipWriter(final ZipArchiveOutputStream zipArchiveOutputStream,
                     final byte[] buffer) {
        this(zipArchiveOutputStream, buffer, null);
    }

    private ZipWriter(final ZipArchiveOutputStream zipArchiveOutputStream,
                      final byte[] buffer,
                      final Path path) {
        this.zipArchiveOutputStream = zipArchiveOutputStream;
        this.buffer = buffer;
        this.path = path;
    }

    public void writeDir(final String name) throws IOException {
        putArchiveEntry(new ZipArchiveEntry(name));
        closeArchiveEntry();
    }

    public void writeAttributeMap(final String name,
                                  final AttributeMap attributeMap) throws IOException {
        putArchiveEntry(new ZipArchiveEntry(name));
        try {
            AttributeMapUtil.write(attributeMap, zipArchiveOutputStream);
        } finally {
            closeArchiveEntry();
        }
    }

    /**
     * @return The number of bytes read.
     */
    public long writeString(final String name,
                            final String string) throws IOException {
        return writeStream(
                new ZipArchiveEntry(name),
                new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * @return The number of bytes read.
     */
    public long writeStream(final String name,
                            final InputStream inputStream) throws IOException {
        return writeStream(new ZipArchiveEntry(name), inputStream);
    }

    /**
     * @return The number of bytes read.
     */
    private long writeStream(final ZipArchiveEntry zipArchiveEntry,
                             final InputStream inputStream) throws IOException {
        final DurationTimer timer = LogUtil.startTimerIfDebugEnabled(LOGGER);
        putArchiveEntry(zipArchiveEntry);
        try {
            final long bytes = TransferUtil.transfer(inputStream, zipArchiveOutputStream, buffer);
            LOGGER.debug(() -> LogUtil.message(
                    "writeStream() - path: {}, zipArchiveEntry: {}, bytes: {}, duration: {}",
                    path,
                    zipArchiveEntry,
                    ByteSize.ofBytes(bytes),
                    timer));
            return bytes;
        } finally {
            closeArchiveEntry();
        }
    }

    public void writeRawStream(final ZipArchiveEntry sourceZipArchiveEntry,
                               final InputStream inputStream) throws IOException {
        writeRawStream(sourceZipArchiveEntry,
                new ZipArchiveEntry(sourceZipArchiveEntry.getName()),
                inputStream);
    }

    public void writeRawStream(final ZipArchiveEntry sourceZipArchiveEntry,
                               final String destEntryName,
                               final InputStream inputStream) throws IOException {
        writeRawStream(sourceZipArchiveEntry,
                new ZipArchiveEntry(destEntryName),
                inputStream);
    }

    public void writeRawStream(final ZipArchiveEntry sourceZipArchiveEntry,
                               final ZipArchiveEntry destZipArchiveEntry,
                               final InputStream inputStream) throws IOException {

        final DurationTimer timer = LogUtil.startTimerIfDebugEnabled(LOGGER);
        destZipArchiveEntry.setCompressedSize(sourceZipArchiveEntry.getCompressedSize());
        destZipArchiveEntry.setCrc(sourceZipArchiveEntry.getCrc());
        destZipArchiveEntry.setExternalAttributes(sourceZipArchiveEntry.getExternalAttributes());
        destZipArchiveEntry.setExtra(sourceZipArchiveEntry.getExtra());
        destZipArchiveEntry.setExtraFields(sourceZipArchiveEntry.getExtraFields());
        destZipArchiveEntry.setGeneralPurposeBit(sourceZipArchiveEntry.getGeneralPurposeBit());
        destZipArchiveEntry.setInternalAttributes(sourceZipArchiveEntry.getInternalAttributes());
        destZipArchiveEntry.setMethod(sourceZipArchiveEntry.getMethod());
        destZipArchiveEntry.setRawFlag(sourceZipArchiveEntry.getRawFlag());
        destZipArchiveEntry.setSize(sourceZipArchiveEntry.getSize());

        putRawArchiveEntry(destZipArchiveEntry, inputStream);
//        zipArchiveOutputStream.addRawArchiveEntry(destZipArchiveEntry, inputStream);
        LOGGER.debug(() -> LogUtil.message(
                "writeRawStream() - path: {}, zipArchiveEntry: {}, bytes: {}, duration: {}",
                path,
                destZipArchiveEntry,
                ZipUtil.getEntryUncompressedSize(sourceZipArchiveEntry)
                        .map(Objects::toString)
                        .orElse("?"),
                timer));
    }

    void putRawArchiveEntry(final ZipArchiveEntry zipArchiveEntry,
                            final InputStream inputStream) throws IOException {
        zipArchiveOutputStream.addRawArchiveEntry(zipArchiveEntry, inputStream);
    }

    void putArchiveEntry(final ZipArchiveEntry zipArchiveEntry) throws IOException {
        zipArchiveOutputStream.putArchiveEntry(zipArchiveEntry);
    }

    void closeArchiveEntry() throws IOException {
        zipArchiveOutputStream.closeArchiveEntry();
    }

    @Override
    public void close() throws IOException {
        zipArchiveOutputStream.close();
    }
}
