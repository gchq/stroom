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

import stroom.data.zip.StroomZipEntry;
import stroom.data.zip.StroomZipFileType;
import stroom.db.util.JooqUtil;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.repo.ErrorReceiver;
import stroom.proxy.repo.ZipInfoStore;
import stroom.util.io.FileUtil;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Optional;
import javax.inject.Inject;

class ZipInfoStoreImpl implements ZipInfoStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZipInfoStoreImpl.class);

    private final ProxyRepoDbConnProvider connProvider;
    private final ZipInfoStoreDao zipInfoStoreDao;

    @Inject
    ZipInfoStoreImpl(final ZipInfoStoreDao zipInfoStoreDao,
                     final ProxyRepoDbConnProvider connProvider) {
        this.zipInfoStoreDao = zipInfoStoreDao;
        this.connProvider = connProvider;
    }

    @Override
    public int store(final Path path, final Path relativePath, final ErrorReceiver errorReceiver) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Storing zip info for  '" + FileUtil.getCanonicalPath(path) + "'");
        }

        // Start a transaction for all of the database changes.
        return JooqUtil.transactionResult(connProvider, context -> {
            // See if this file has already had info extracted.
            Optional<Integer> optionalSourceId = zipInfoStoreDao.getSource(context, relativePath.toString());

            // If we don't already have a source id then read the zip and add all entries to the DB.
            return optionalSourceId.orElseGet(() -> {
                final int sourceId = zipInfoStoreDao.addSource(context, relativePath.toString());

                try (final ZipFile zipFile = new ZipFile(Files.newByteChannel(path))) {

                    final Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
                    while (entries.hasMoreElements()) {
                        final ZipArchiveEntry entry = entries.nextElement();

                        // Skip directories
                        if (!entry.isDirectory()) {
                            final String fileName = entry.getName();

                            // Split into stem and extension.
                            int index = fileName.indexOf(".");
                            if (index != -1) {
                                final String dataName = fileName.substring(0, index);
                                final String extension = fileName.substring(index).toLowerCase();

                                // If this is a meta entry then get the feed name.
                                String feedName = null;
                                String typeName = null;

                                int extensionType = -1;
                                if (StroomZipFileType.Meta.getExtension().equals(extension)) {
                                    // We need to be able to sort by extension so we can get meta data first.
                                    extensionType = 1;

                                    try (final InputStream metaStream = zipFile.getInputStream(entry)) {
                                        if (metaStream == null) {
                                            errorReceiver.onError(path, "Unable to find meta?");
                                        } else {
                                            final AttributeMap attributeMap = new AttributeMap();
                                            AttributeMapUtil.read(metaStream, attributeMap);
                                            feedName = attributeMap.get(StandardHeaderArguments.FEED);
                                            typeName = attributeMap.get(StandardHeaderArguments.TYPE);
                                        }
                                    } catch (final RuntimeException e) {
                                        errorReceiver.onError(path, e.getMessage());
                                        LOGGER.error(e.getMessage(), e);
                                    }
                                } else if (StroomZipFileType.Context.getExtension().equals(extension)) {
                                    extensionType = 2;
                                } else if (StroomZipFileType.Data.getExtension().equals(extension)) {
                                    extensionType = 3;
                                }

                                // Don't add unknown types.
                                if (extensionType != -1) {
                                    final int dataId = zipInfoStoreDao.addData(
                                            context,
                                            sourceId,
                                            dataName,
                                            feedName,
                                            typeName);
                                    zipInfoStoreDao.addEntry(context,
                                            dataId,
                                            extension,
                                            extensionType,
                                            entry.getSize());
                                }
                            }
                        }
                    }
                } catch (final IOException | RuntimeException e) {
                    // Unable to open file ... must be bad.
                    errorReceiver.onError(path, e.getMessage());
                    LOGGER.error(e.getMessage(), e);
                }

                return sourceId;
            });
        });
    }
}