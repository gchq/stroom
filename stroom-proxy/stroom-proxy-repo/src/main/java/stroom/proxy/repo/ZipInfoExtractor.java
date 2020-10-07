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

package stroom.proxy.repo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.zip.StroomZipFile;
import stroom.data.zip.StroomZipFileType;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.util.io.FileUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

class ZipInfoExtractor {
    private final Logger LOGGER = LoggerFactory.getLogger(ZipInfoExtractor.class);

    private final ErrorReceiver errorReceiver;

    ZipInfoExtractor(final ErrorReceiver errorReceiver) {
        this.errorReceiver = errorReceiver;
    }

    public ZipInfo extract(final Path path, final BasicFileAttributes attrs) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Getting zip info for  '" + FileUtil.getCanonicalPath(path) + "'");
        }

        AttributeMap attributeMap = null;
        long totalUncompressedSize = 0;
        int zipEntryCount = 0;

        try (final StroomZipFile stroomZipFile = new StroomZipFile(path)) {
            final Set<String> baseNameSet = stroomZipFile.getStroomZipNameSet().getBaseNameSet();
            zipEntryCount = baseNameSet.size();

            if (baseNameSet.isEmpty()) {
                errorReceiver.onError(path, "Unable to find any entry?");
            } else {
                for (final String sourceName : baseNameSet) {
                    // Extract meta data
                    if (attributeMap == null) {
                        try {
                            final InputStream metaStream = stroomZipFile.getInputStream(sourceName, StroomZipFileType.Meta);
                            if (metaStream == null) {
                                errorReceiver.onError(path, "Unable to find meta?");
                            } else {
                                attributeMap = new AttributeMap();
                                AttributeMapUtil.read(metaStream, attributeMap);
                            }
                        } catch (final RuntimeException e) {
                            errorReceiver.onError(path, e.getMessage());
                            LOGGER.error(e.getMessage(), e);
                        }
                    }

                    totalUncompressedSize += getRawEntrySize(stroomZipFile, sourceName, StroomZipFileType.Meta);
                    totalUncompressedSize += getRawEntrySize(stroomZipFile, sourceName, StroomZipFileType.Context);
                    totalUncompressedSize += getRawEntrySize(stroomZipFile, sourceName, StroomZipFileType.Data);
                }
            }
        } catch (final IOException | RuntimeException e) {
            // Unable to open file ... must be bad.
            errorReceiver.onError(path, e.getMessage());
            LOGGER.error(e.getMessage(), e);
        }

        // Get compressed size.
        Long totalCompressedSize = null;
        try {
            totalCompressedSize = Files.size(path);
        } catch (final IOException | RuntimeException e) {
            errorReceiver.onError(path, e.getMessage());
            LOGGER.error(e.getMessage(), e);
        }

        String feedName = null;

        if (attributeMap == null) {
            errorReceiver.onError(path, "Unable to find meta data");
        } else {
            feedName = attributeMap.get(StandardHeaderArguments.FEED);
            if (feedName == null || feedName.length() == 0) {
                errorReceiver.onError(path, "Unable to find feed in header??");
            }
        }

        String typeName = null;
        if (attributeMap != null) {
            typeName = attributeMap.get(StandardHeaderArguments.TYPE);
        }

        final FileSetKey key = new FileSetKey(feedName, typeName);
        final ZipInfo zipInfo = new ZipInfo(path,
                key,
                totalUncompressedSize,
                totalCompressedSize,
                attrs.lastModifiedTime().toMillis(),
                zipEntryCount);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Zip info for  '" + FileUtil.getCanonicalPath(path) + "' is " + zipInfo);
        }

        return zipInfo;
    }

    private long getRawEntrySize(final StroomZipFile stroomZipFile,
                                 final String sourceName,
                                 final StroomZipFileType fileType)
            throws IOException {
        final long size = stroomZipFile.getSize(sourceName, fileType);
        if (size == -1) {
            throw new IOException("Unknown raw file size");
        }

        return size;
    }
}
