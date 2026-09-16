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

import stroom.data.zip.StroomZipEntries;
import stroom.data.zip.StroomZipEntry;
import stroom.data.zip.StroomZipFileType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.app.handler.ZipEntryGroup.Entry;
import stroom.proxy.repo.FeedKeyInterner;
import stroom.receive.common.ContentTooLargeException;
import stroom.receive.common.InputStreamUtils;
import stroom.receive.common.StroomStreamException;
import stroom.util.exception.ThrowingConsumer;
import stroom.util.io.ByteSize;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.FeedKey;
import stroom.util.zip.ZipUtil;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A received zip, indexed: its entries grouped into the data entry and the sidecars that augment it,
 * each group's meta merged over the request headers, and each group's feed key known - all before a
 * byte of it is written anywhere.
 * <p>
 * The grouping rules are {@link StroomZipEntries}': an entry whose extension is not a known sidecar
 * extension is data, whatever it is called - a received zip may be a plain collection of log files
 * with no meta of its own - and {@code .meta}, {@code .mf} and {@code .ctx} (with their aliases)
 * attach to the data entry sharing their base name. A data entry with no meta gets one from the
 * headers. A sidecar with no data entry beside it describes nothing and is omitted, with a warning.
 * </p>
 * <p>
 * The zip is rejected, with a status code the sender can act on, rather than read partially: an
 * entry that cannot be read (encrypted, or an unsupported method), two entries with one name, a name
 * the grouping cannot parse, a manifest anywhere but first, or no data entries at all.
 * </p>
 */
final class ReceivedZip {

    /**
     * A {@code .meta} entry is a header block. The bound stops a hostile or corrupt entry being
     * inflated into the heap: DEFLATE reaches ratios of around 1000:1, so the request size cannot
     * bound it.
     */
    static final ByteSize MAX_META_ENTRY_SIZE = ByteSize.ofMebibytes(1);

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ReceivedZip.class);

    private final List<Group> groups;
    private final Set<FeedKey> feedKeys;

    private ReceivedZip(final List<Group> groups, final Set<FeedKey> feedKeys) {
        this.groups = Collections.unmodifiableList(groups);
        this.feedKeys = Collections.unmodifiableSet(feedKeys);
    }

    static ReceivedZip index(final ZipFile zipFile, final AttributeMap headers) throws IOException {
        final StroomZipEntries zipEntries = new StroomZipEntries();
        final Map<String, ZipArchiveEntry> byName = new HashMap<>();

        final Enumeration<ZipArchiveEntry> entries = zipFile.getEntriesInPhysicalOrder();
        while (entries.hasMoreElements()) {
            final ZipArchiveEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            final String name = entry.getName();
            if (!zipFile.canReadEntryData(entry)) {
                throw invalid(headers, StroomStatusCode.COMPRESSED_STREAM_INVALID,
                        "Zip entry '{}' cannot be read: it is encrypted or uses an unsupported "
                        + "compression method", name);
            }
            if (byName.putIfAbsent(name, entry) != null) {
                throw invalid(headers, StroomStatusCode.COMPRESSED_STREAM_INVALID,
                        "Zip contains more than one entry named '{}'", name);
            }
            try {
                zipEntries.addFile(name);
            } catch (final RuntimeException e) {
                throw invalid(headers, StroomStatusCode.INVALID_FORMAT,
                        "Zip entry '{}' cannot be accepted: {}", name, e.getMessage());
            }
        }

        final FeedKeyInterner interner = FeedKeyInterner.create();
        final List<Group> groups = new ArrayList<>();
        final Set<FeedKey> feedKeys = new LinkedHashSet<>();
        // Every group without a meta of its own gets the same one, so a zip of a hundred thousand
        // plain files holds one map, not a hundred thousand copies of the headers.
        final AttributeMap headerMeta = AttributeMapUtil.cloneAllowable(headers);
        for (final String baseName : zipEntries.getBaseNames()) {
            final Optional<ZipArchiveEntry> data = find(zipEntries, byName, baseName, StroomZipFileType.DATA);
            final Optional<ZipArchiveEntry> manifest = find(zipEntries, byName, baseName, StroomZipFileType.MANIFEST);
            final Optional<ZipArchiveEntry> meta = find(zipEntries, byName, baseName, StroomZipFileType.META);
            final Optional<ZipArchiveEntry> context = find(zipEntries, byName, baseName, StroomZipFileType.CONTEXT);

            if (data.isEmpty()) {
                LOGGER.warn(() -> LogUtil.message(
                        "Zip entries {} have no data entry beside them and are ignored (receipt id {})",
                        List.of(manifest, meta, context).stream()
                                .flatMap(Optional::stream)
                                .map(ZipArchiveEntry::getName)
                                .toList(),
                        headers.get(StandardHeaderArguments.RECEIPT_ID)));
                continue;
            }
            if (manifest.isPresent() && !groups.isEmpty()) {
                throw invalid(headers, StroomStatusCode.INVALID_FORMAT,
                        "Zip entry '{}' is a manifest that is not the first entry; a manifest describes "
                        + "the whole zip and can only come first", manifest.get().getName());
            }

            final AttributeMap groupMeta = meta.isPresent()
                    ? AttributeMapUtil.mergeAttributeMaps(headers, ThrowingConsumer.unchecked(target -> {
                        try (final InputStream in = InputStreamUtils.getBoundedInputStream(
                                zipFile.getInputStream(meta.get()), MAX_META_ENTRY_SIZE)) {
                            AttributeMapUtil.read(in, target);
                        } catch (final ContentTooLargeException e) {
                            throw invalid(headers, StroomStatusCode.CONTENT_TOO_LARGE,
                                    "Zip entry '{}' is larger than the {} a meta entry may be",
                                    meta.get().getName(), MAX_META_ENTRY_SIZE);
                        }
                    }))
                    : headerMeta;
            final FeedKey feedKey = interner.intern(
                    groupMeta.get(StandardHeaderArguments.FEED),
                    groupMeta.get(StandardHeaderArguments.TYPE));

            groups.add(new Group(feedKey, manifest.orElse(null), groupMeta, context.orElse(null), data.get()));
            feedKeys.add(feedKey);
        }

        if (groups.isEmpty()) {
            throw invalid(headers, StroomStatusCode.COMPRESSED_STREAM_INVALID, "Zip contains no data entries");
        }
        return new ReceivedZip(groups, feedKeys);
    }

    /**
     * The groups that hold data, in the order their base names first appear in the zip.
     */
    List<Group> groups() {
        return groups;
    }

    /**
     * The same zip with some feed keys renamed - the receipt policy may generate a feed for a group
     * that arrived without one - so that what is written names the feed the policy accepted.
     */
    ReceivedZip withFeedKeys(final Map<FeedKey, FeedKey> renamed) {
        if (renamed.isEmpty()) {
            return this;
        }
        // A meta shared between groups is copied once, and stays shared.
        final Map<AttributeMap, AttributeMap> copies = new IdentityHashMap<>();
        final List<Group> renamedGroups = new ArrayList<>(groups.size());
        final Set<FeedKey> renamedKeys = new LinkedHashSet<>();
        for (final Group group : groups) {
            final FeedKey to = renamed.get(group.feedKey());
            if (to == null) {
                renamedGroups.add(group);
                renamedKeys.add(group.feedKey());
            } else {
                final AttributeMap meta = copies.computeIfAbsent(group.meta(), original -> {
                    final AttributeMap copy = new AttributeMap(original);
                    AttributeMapUtil.addFeedAndType(copy, to.feed(), to.type());
                    return copy;
                });
                renamedGroups.add(new Group(to, group.manifest(), meta, group.context(), group.data()));
                renamedKeys.add(to);
            }
        }
        return new ReceivedZip(renamedGroups, renamedKeys);
    }

    /**
     * The distinct feed keys, in order of first appearance.
     */
    Set<FeedKey> feedKeys() {
        return feedKeys;
    }

    /**
     * Write the groups whose feed key is in {@code allowed} to a canonical proxy zip: numbered from
     * {@code 0000000001}, manifest then meta then context then data, data copied raw.
     *
     * @return One entry group per group written, in the order written, for {@code proxy.entries}.
     */
    List<ZipEntryGroup> copyTo(final ZipFile zipFile,
                               final Set<FeedKey> allowed,
                               final Path zipPath) throws IOException {
        final List<ZipEntryGroup> written = new ArrayList<>();
        try (final ProxyZipWriter writer = new ProxyZipWriter(zipPath, LocalByteBuffer.get())) {
            int count = 0;
            for (final Group group : groups) {
                if (!allowed.contains(group.feedKey())) {
                    continue;
                }
                final String baseName = NumericFileNameUtil.create(++count);
                final ZipEntryGroup out = new ZipEntryGroup(group.feedKey());
                out.setManifestEntry(copy(zipFile, writer, group.manifest(), baseName, StroomZipFileType.MANIFEST));

                final byte[] metaBytes = AttributeMapUtil.toByteArray(group.meta());
                final String metaName = baseName + StroomZipFileType.META.getDotExtension();
                writer.writeStream(metaName, new ByteArrayInputStream(metaBytes));
                out.setMetaEntry(new Entry(metaName, metaBytes.length));

                out.setContextEntry(copy(zipFile, writer, group.context(), baseName, StroomZipFileType.CONTEXT));
                out.setDataEntry(copy(zipFile, writer, group.data(), baseName, StroomZipFileType.DATA));
                written.add(out);
            }
        }
        return written;
    }

    /**
     * Copy an entry under its canonical name. Raw - neither inflated nor deflated - when the zip
     * declares its uncompressed size, in which case that declared size is what is recorded; inflated
     * and counted otherwise.
     */
    private static Entry copy(final ZipFile zipFile,
                              final ZipWriter writer,
                              final ZipArchiveEntry entry,
                              final String baseName,
                              final StroomZipFileType type) throws IOException {
        if (entry == null) {
            return null;
        }
        final String name = baseName + type.getDotExtension();
        final long size;
        if (ZipUtil.hasKnownUncompressedSize(entry)) {
            try (final InputStream in = zipFile.getRawInputStream(entry)) {
                writer.writeRawStream(entry, name, in);
            }
            size = Math.max(entry.getSize(), 0);
        } else {
            try (final InputStream in = zipFile.getInputStream(entry)) {
                size = writer.writeStream(name, in);
            }
        }
        return new Entry(name, size);
    }

    private static Optional<ZipArchiveEntry> find(final StroomZipEntries zipEntries,
                                                  final Map<String, ZipArchiveEntry> byName,
                                                  final String baseName,
                                                  final StroomZipFileType type) {
        return zipEntries.getByType(baseName, type)
                .map(StroomZipEntry::getFullName)
                .map(byName::get);
    }

    private static StroomStreamException invalid(final AttributeMap headers,
                                                 final StroomStatusCode statusCode,
                                                 final String message,
                                                 final Object... args) {
        return new StroomStreamException(statusCode, headers, LogUtil.message(message, args));
    }


    // --------------------------------------------------------------------------------


    /**
     * A data entry and what augments it. {@code meta} is the group's meta as it will be written:
     * the zip's own {@code .meta} merged over the allowable request headers, or the headers alone.
     */
    record Group(FeedKey feedKey,
                 ZipArchiveEntry manifest,
                 AttributeMap meta,
                 ZipArchiveEntry context,
                 ZipArchiveEntry data) {

    }
}
