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

package stroom.proxy.app.handler;

import stroom.data.zip.StroomZipFileType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.app.handler.ZipEntryGroup.Entry;
import stroom.util.logging.LogUtil;
import stroom.util.shared.FeedKey;
import stroom.util.zip.ZipUtil;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Writes a canonical proxy file group - {@code proxy.zip}, {@code proxy.entries} and
 * {@code proxy.meta} - from items copied out of other canonical zips.
 * <p>
 * An item is one base name's entries: the data entry and its sidecars. Items are numbered from
 * {@code 0000000001} in the order they are added, each entry is copied raw under its new name,
 * and {@code proxy.entries} is built from what was actually written so that it describes the whole
 * of the zip beside it. The split-zip stage adds one feed's items from one zip; the aggregate stage
 * adds a range of items from each of several zips. Neither needs to know how a group is laid out.
 * </p>
 * <p>
 * Nothing is complete until {@link #finish} returns. A writer closed without finishing leaves a
 * partial zip and entries file behind, which is only ever scratch inside a store write that its
 * caller discards.
 * </p>
 */
public final class CanonicalGroupWriter implements AutoCloseable {

    private final FileGroup fileGroup;
    private final ProxyZipWriter zipWriter;
    private final Writer entriesWriter;
    private long itemCount;
    private boolean finished;

    public CanonicalGroupWriter(final Path groupDir) throws IOException {
        this.fileGroup = new FileGroup(Objects.requireNonNull(groupDir, "groupDir"));
        this.zipWriter = new ProxyZipWriter(fileGroup.getZip(), LocalByteBuffer.get());
        try {
            this.entriesWriter = Files.newBufferedWriter(fileGroup.getEntries());
        } catch (final IOException | RuntimeException e) {
            try {
                zipWriter.close();
            } catch (final IOException | RuntimeException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    /**
     * Copy one item from {@code source} under the next number.
     *
     * @param source the zip the item's entries are in.
     * @param item   the item as the source's {@code proxy.entries} describes it.
     * @param key    the feed key the written item is recorded under.
     * @return the item as written.
     * @throws IOException if an entry the item names is not in the zip, or the copy fails.
     */
    public ZipEntryGroup add(final ZipFile source,
                             final ZipEntryGroup item,
                             final FeedKey key) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(item, "item");
        if (finished) {
            throw new IllegalStateException("The group has been finished");
        }
        final String baseName = NumericFileNameUtil.create(++itemCount);
        final ZipEntryGroup written = new ZipEntryGroup(key);
        written.setManifestEntry(copy(source, item.getManifestEntry(), baseName, StroomZipFileType.MANIFEST));
        written.setMetaEntry(copy(source, item.getMetaEntry(), baseName, StroomZipFileType.META));
        written.setContextEntry(copy(source, item.getContextEntry(), baseName, StroomZipFileType.CONTEXT));
        written.setDataEntry(copy(source, item.getDataEntry(), baseName, StroomZipFileType.DATA));
        written.write(entriesWriter);
        return written;
    }

    /**
     * Close the zip, which checks it is a valid proxy zip, and write {@code proxy.meta}.
     */
    public void finish(final AttributeMap meta) throws IOException {
        Objects.requireNonNull(meta, "meta");
        if (finished) {
            throw new IllegalStateException("The group has been finished");
        }
        finished = true;
        try {
            entriesWriter.close();
        } finally {
            zipWriter.close();
        }
        AttributeMapUtil.write(meta, fileGroup.getMeta());
    }

    @Override
    public void close() throws IOException {
        if (!finished) {
            finished = true;
            try {
                entriesWriter.close();
            } finally {
                zipWriter.close();
            }
        }
    }

    /**
     * Raw - neither inflated nor deflated - when the zip declares the entry's uncompressed size, in
     * which case that declared size is what is recorded; inflated and counted otherwise.
     */
    private Entry copy(final ZipFile source,
                       final Entry entry,
                       final String baseName,
                       final StroomZipFileType type) throws IOException {
        if (entry == null) {
            return null;
        }
        final ZipArchiveEntry zipEntry = source.getEntry(entry.getName());
        if (zipEntry == null) {
            throw new IOException(LogUtil.message(
                    "proxy.entries names '{}', which is not in the zip beside it", entry.getName()));
        }
        final String name = baseName + type.getDotExtension();
        final long size;
        if (ZipUtil.hasKnownUncompressedSize(zipEntry)) {
            try (final InputStream in = source.getRawInputStream(zipEntry)) {
                zipWriter.writeRawStream(zipEntry, name, in);
            }
            size = Math.max(zipEntry.getSize(), 0);
        } else {
            try (final InputStream in = source.getInputStream(zipEntry)) {
                size = zipWriter.writeStream(name, in);
            }
        }
        return new Entry(name, size);
    }
}
