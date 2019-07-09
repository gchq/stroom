/*
 * Copyright 2017 Crown Copyright
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
import stroom.util.io.CloseableUtil;
import stroom.util.io.StreamProgressMonitor;
import stroom.util.shared.ModelStringUtil;
import stroom.util.thread.BufferFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

public final class ProxyFileHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyFileHandler.class);

    public Long processFeedFile(final List<? extends StroomStreamHandler> stroomStreamHandlerList,
                                final Path file,
                                final StreamProgressMonitor streamProgress,
                                final long startSequence) throws IOException {
        long entrySequence = startSequence;
        StroomZipFile stroomZipFile = null;
        boolean bad = true;

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("processFeedFile() - " + file);
        }

        IOException exception = null;
        try {
            stroomZipFile = new StroomZipFile(file);

            for (final String sourceName : stroomZipFile.getStroomZipNameSet().getBaseNameSet()) {
                bad = false;

                final String targetName = StroomFileNameUtil.getIdPath(entrySequence++);

                sendEntry(stroomStreamHandlerList, stroomZipFile, sourceName, streamProgress,
                        new StroomZipEntry(null, targetName, StroomZipFileType.Meta));
                sendEntry(stroomStreamHandlerList, stroomZipFile, sourceName, streamProgress,
                        new StroomZipEntry(null, targetName, StroomZipFileType.Context));
                sendEntry(stroomStreamHandlerList, stroomZipFile, sourceName, streamProgress,
                        new StroomZipEntry(null, targetName, StroomZipFileType.Data));
            }
        } catch (final IOException io) {
            exception = io;
        } finally {
            CloseableUtil.close(stroomZipFile);
        }

        if (exception != null) {
            ErrorFileUtil.addErrorMessage(file, exception.getMessage(), bad);
            throw exception;
        }

        return entrySequence;
    }

//    private long getRawContentSize(final Path file) throws IOException {
//        if (LOGGER.isDebugEnabled()) {
//            LOGGER.debug("Getting raw content size for  '" + FileUtil.getCanonicalPath(file) + "'");
//        }
//
//        long totalSize = 0;
//
//        try (final StroomZipFile stroomZipFile = new StroomZipFile(file)) {
//            for (final String sourceName : stroomZipFile.getStroomZipNameSet().getBaseNameSet()) {
//                totalSize += getRawEntrySize(stroomZipFile, sourceName, StroomZipFileType.Meta);
//                totalSize += getRawEntrySize(stroomZipFile, sourceName, StroomZipFileType.Context);
//                totalSize += getRawEntrySize(stroomZipFile, sourceName, StroomZipFileType.Data);
//            }
//        }
//
//        if (LOGGER.isDebugEnabled()) {
//            LOGGER.debug("Raw content size for  '" + FileUtil.getCanonicalPath(file) + "' is " + ModelStringUtil.formatIECByteSizeString(totalSize));
//        }
//
//        return totalSize;
//    }
//
//    private long getRawEntrySize(final StroomZipFile stroomZipFile,
//                                 final String sourceName,
//                                 final StroomZipFileType fileType)
//            throws IOException {
//        final long size = stroomZipFile.getSize(sourceName, fileType);
//        if (size == -1) {
//            throw new IOException("Unknown raw file size");
//        }
//
//        return size;
//    }

    private void sendEntry(final List<? extends StroomStreamHandler> requestHandlerList, final StroomZipFile stroomZipFile,
                           final String sourceName, final StreamProgressMonitor streamProgress,
                           final StroomZipEntry targetEntry)
            throws IOException {
        final InputStream inputStream = stroomZipFile.getInputStream(sourceName, targetEntry.getStroomZipFileType());
        sendEntry(requestHandlerList, inputStream, streamProgress, targetEntry);
    }

    private void sendEntry(final List<? extends StroomStreamHandler> stroomStreamHandlerList, final InputStream inputStream,
                           final StreamProgressMonitor streamProgress, final StroomZipEntry targetEntry)
            throws IOException {
        if (inputStream != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("sendEntry() - " + targetEntry);
            }
            final byte[] buffer = BufferFactory.create();
            for (final StroomStreamHandler stroomStreamHandler : stroomStreamHandlerList) {
                stroomStreamHandler.handleEntryStart(targetEntry);
            }
            int read;
            long totalRead = 0;
            while ((read = inputStream.read(buffer)) != -1) {
                totalRead += read;
                streamProgress.progress(read);
                for (final StroomStreamHandler stroomStreamHandler : stroomStreamHandlerList) {
                    stroomStreamHandler.handleEntryData(buffer, 0, read);
                }
            }
            for (final StroomStreamHandler stroomStreamHandler : stroomStreamHandlerList) {
                stroomStreamHandler.handleEntryEnd();
            }

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("sendEntry() - " + targetEntry + " " + ModelStringUtil.formatIECByteSizeString(totalRead));
            }
            if (totalRead == 0) {
                LOGGER.warn("sendEntry() - " + targetEntry + " IS BLANK");
            }
            LOGGER.debug("sendEntry() - {} size is {}", targetEntry, totalRead);

        }
    }
}
