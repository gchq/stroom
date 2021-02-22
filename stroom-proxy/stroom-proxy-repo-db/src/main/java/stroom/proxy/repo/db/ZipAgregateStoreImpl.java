/*
 * Copyright 2019 Crown Copyright
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

package stroom.proxy.repo.db;

import stroom.db.util.JooqUtil;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.repo.ErrorReceiver;
import stroom.proxy.repo.ProxyRepositoryReaderConfig;
import stroom.proxy.repo.ZipInfoStore;
import stroom.util.io.FileUtil;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jooq.Record3;
import org.jooq.Record6;
import org.jooq.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import javax.inject.Inject;

import static stroom.proxy.repo.db.jooq.tables.ZipData.ZIP_DATA;
import static stroom.proxy.repo.db.jooq.tables.ZipEntry.ZIP_ENTRY;
//import static stroom.proxy.repo.db.jooq.tables.ZipSource.ZIP_SOURCE;
//import static stroom.proxy.repo.db.jooq.tables.ZipDest.ZIP_DEST;
//import static stroom.proxy.repo.db.jooq.tables.ZipDestData.ZIP_DEST_DATA;

class ZipAgregateStoreImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZipAgregateStoreImpl.class);

    private final ProxyRepoDbConnProvider connProvider;
    private final ZipInfoStoreDao zipInfoStoreDao;
    private final ProxyRepositoryReaderConfig proxyRepositoryReaderConfig;

    private ZipDataCollection currentCollection;

    @Inject
    ZipAgregateStoreImpl(final ZipInfoStoreDao zipInfoStoreDao,
                         final ProxyRepoDbConnProvider connProvider,
                         final ProxyRepositoryReaderConfig proxyRepositoryReaderConfig) {
        this.zipInfoStoreDao = zipInfoStoreDao;
        this.connProvider = connProvider;
        this.proxyRepositoryReaderConfig = proxyRepositoryReaderConfig;
    }

    public void aggregate() {
        // Start a transaction for all of the database changes.
        JooqUtil.context(connProvider, context -> {
            // Get all data items that have not been added to aggregate destinations.
            try (final Stream<Record6<Long, String, String, Long, String, Long>> stream = context
                    .select(ZIP_DATA.ID,
                            ZIP_DATA.NAME,
                            ZIP_DATA.FEEDNAME,
                            ZIP_ENTRY.ID,
                            ZIP_ENTRY.EXTENSION,
                            ZIP_ENTRY.BYTE_SIZE)
                    .from(ZIP_DATA)
                    .join(ZIP_ENTRY).on(ZIP_ENTRY.FK_ZIP_DATA_ID.eq(ZIP_DATA.ID))
//                    .and(ZIP_DATA.HAS_DEST.isFalse())
                    .orderBy(ZIP_DATA.FEEDNAME, ZIP_DATA.ID)
                    .stream()) {
                stream.forEach(record -> {
                            if (currentCollection != null && currentCollection.dataId != record.get(ZIP_DATA.ID)) {
                                addCollection(currentCollection);
                                currentCollection = null;
                            }

                    if (currentCollection == null) {
                        currentCollection = new ZipDataCollection(record.get(ZIP_DATA.ID),
                                record.get(ZIP_DATA.NAME),
                                record.get(ZIP_DATA.FEEDNAME));
                    }

                    final ZipDataEntry zipDataEntry = new ZipDataEntry(
                            record.get(ZIP_ENTRY.ID),
                            record.get(ZIP_ENTRY.EXTENSION),
                            record.get(ZIP_ENTRY.BYTE_SIZE));
                    currentCollection.addEntry(zipDataEntry);
                });
            }
        });

        // Add final entry.
        if (currentCollection != null) {
            addCollection(currentCollection);
            currentCollection = null;
        }

//            // See if this file has already had info extracted.
//            Long sourceId = zipInfoStoreDao.getSource(context, relativePath.toString());
//
//            // If we don't already have a source id then read the zip and add all entries to the DB.
//            if (sourceId == null) {
//                sourceId = zipInfoStoreDao.addSource(context, relativePath.toString());
//
//                try (final ZipFile zipFile = new ZipFile(Files.newByteChannel(path))) {
//
//                    final Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
//                    while (entries.hasMoreElements()) {
//                        final ZipArchiveEntry entry = entries.nextElement();
//
//                        // Skip directories
//                        if (!entry.isDirectory()) {
//                            final String fileName = entry.getName();
//
//                            // Split into stem and extension.
//                            int index = fileName.indexOf(".");
//                            if (index != -1) {
//                                final String dataName = fileName.substring(0, index);
//                                final String extension = fileName.substring(index + 1);
//
//                                // If this is a meta entry then get the feed name.
//                                String feedName = null;
//                                if ("meta".equalsIgnoreCase(extension)) {
//                                    try (final InputStream metaStream = zipFile.getInputStream(entry)) {
//                                        if (metaStream == null) {
//                                            errorReceiver.onError(path, "Unable to find meta?");
//                                        } else {
//                                            final AttributeMap attributeMap = new AttributeMap();
//                                            AttributeMapUtil.read(metaStream, attributeMap);
//                                            feedName = attributeMap.get(StandardHeaderArguments.FEED);
//                                        }
//                                    } catch (final RuntimeException e) {
//                                        errorReceiver.onError(path, e.getMessage());
//                                        LOGGER.error(e.getMessage(), e);
//                                    }
//                                }
//
//                                final long dataId = zipInfoStoreDao.addData(context, sourceId, dataName, feedName);
//                                zipInfoStoreDao.addEntry(context, sourceId, dataId, extension, entry.getSize());
//                            }
//                        }
//                    }
//                } catch (final IOException | RuntimeException e) {
//                    // Unable to open file ... must be bad.
//                    errorReceiver.onError(path, e.getMessage());
//                    LOGGER.error(e.getMessage(), e);
//                }
//            }
//
//            return sourceId;

    }

    private void addCollection(final ZipDataCollection collection) {
        final long maxStreamSize = proxyRepositoryReaderConfig.getMaxStreamSize();
        final long maxDestSize = Math.max(0, maxStreamSize - collection.totalSize);


//        JooqUtil.context(connProvider, context -> {
//            context
//                    .select(ZIP_DEST.ID, ZIP_DEST.FEEDNAME, ZIP_DEST.BYTE_SIZE, ZIP_DEST.CREATE_TIME_MS)
//                    .from(ZIP_DEST)
//                    .where(ZIP_DEST.FEEDNAME.eq(collection.feedName))
//                    .and(ZIP_DEST.BYTE_SIZE.le(maxDestSize))
//
//
//        });
    }

    private synchronized void closeOldDestinations() {





    }

    private synchronized void closeDestination(final long destinationId) {

    }

//    private void aggregateData(final long dataId, final String name, final String feedName) {
//        // Start a transaction for all of the database changes.
//        JooqUtil.transaction(connProvider, context -> {
//                    final Result<Record3<Long, String, Long>> result = context
//                            .select(ZIP_ENTRY.ID, ZIP_ENTRY.EXTENSION, ZIP_ENTRY.BYTE_SIZE)
//                            .from(ZIP_ENTRY)
//                            .where(ZIP_ENTRY.FK_ZIP_DATA_ID.eq(dataId))
//                            .fetch();
//
//
//
//    }

        private static class ZipDataEntry {
            private final long entryId;
            private final String extension;
            private final long size;

            public ZipDataEntry(final long entryId, final String extension, final long size) {
                this.entryId = entryId;
                this.extension = extension;
                this.size = size;
            }
        }

    private static class ZipDataCollection {
            private final long dataId;
            private final String name;
            private final String feedName;
            private final List<ZipDataEntry> entries;
            private long totalSize;

        public ZipDataCollection(final long dataId,
                                 final String name,
                                 final String feedName) {
            this.dataId = dataId;
            this.name = name;
            this.feedName = feedName;
            this.entries = new ArrayList<>();
        }

        public void addEntry(final ZipDataEntry entry) {
            entries.add(entry);
            totalSize += entry.size;

        }


    }
}