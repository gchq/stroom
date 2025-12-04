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

package stroom.data.store.impl;

import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.data.zip.StroomFileNameUtil;
import stroom.data.zip.StroomZipEntry;
import stroom.data.zip.StroomZipFileType;
import stroom.data.zip.StroomZipOutputStream;
import stroom.data.zip.StroomZipOutputStreamImpl;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.MetaService;
import stroom.meta.api.StandardHeaderArguments;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LogItemProgress;
import stroom.util.shared.Message;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class DataDownloadTaskHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataDownloadTaskHandler.class);

    private static final String AGGREGATION_DELIMITER = "_";
    private static final String ZIP_EXTENSION = ".zip";

    private final TaskContextFactory taskContextFactory;
    private final Store streamStore;
    private final MetaService metaService;
    private final SecurityContext securityContext;
    private final AtomicLong fileCount = new AtomicLong(0);
    private String lastPossibleFileName;

    @Inject
    public DataDownloadTaskHandler(final TaskContextFactory taskContextFactory,
                                   final Store streamStore,
                                   final MetaService metaService,
                                   final SecurityContext securityContext) {
        this.taskContextFactory = taskContextFactory;
        this.streamStore = streamStore;
        this.metaService = metaService;
        this.securityContext = securityContext;
    }

    public DataDownloadResult downloadData(final FindMetaCriteria criteria,
                                           final Path outputDir,
                                           final String format,
                                           final DataDownloadSettings settings) {
        return taskContextFactory.contextResult("Download Data", taskContext ->
                downloadData(taskContext, criteria, outputDir, format, settings)).get();
    }

    public DataDownloadResult downloadData(final TaskContext taskContext,
                                           final FindMetaCriteria criteria,
                                           final Path outputDir,
                                           final String format,
                                           final DataDownloadSettings settings) {
        return securityContext.secureResult(() -> {
            final List<Meta> list = metaService.find(criteria).getValues();
            final DataDownloadResult result = new DataDownloadResult();
            if (list.isEmpty()) {
                return result;
            }

            long id = 0;

            StroomZipOutputStream stroomZipOutputStream = null;
            try {
                final LogItemProgress logItemProgress = new LogItemProgress(0, list.size());
                taskContext.info(() -> "Data " + logItemProgress);

                for (final Meta meta : list) {
                    try {
                        // Establish the output file name.
                        final AttributeMap metaMap = new AttributeMap();
                        metaMap.put(StandardHeaderArguments.FEED, meta.getFeedName());
                        metaMap.put("streamType", meta.getTypeName());
                        metaMap.put("streamId", String.valueOf(meta.getId()));
                        final String possibleFilename = StroomFileNameUtil.constructFilename(null,
                                0,
                                format,
                                metaMap,
                                ZIP_EXTENSION);
                        if (stroomZipOutputStream != null && !possibleFilename.equals(lastPossibleFileName)) {
                            stroomZipOutputStream.close();
                            stroomZipOutputStream = null;
                        }
                        lastPossibleFileName = possibleFilename;

                        // Open a zip output stream if we don't currently have one.
                        if (stroomZipOutputStream == null) {
                            stroomZipOutputStream = getStroomZipOutputStream(taskContext, outputDir, format, metaMap);
                            id = 0;
                        }

                        // Write to the output stream.
                        id = downloadStream(taskContext, meta.getId(), stroomZipOutputStream, id,
                                settings.getMaxFileParts());
                        result.incrementRecordsWritten();
                        logItemProgress.incrementProgress();

                        boolean startNewFile = false;
                        boolean hitMaxFileSize = false;
                        boolean hitMaxFileParts = false;
                        if (settings.getMaxFileSize() != null
                                && stroomZipOutputStream.getProgressSize() > settings.getMaxFileSize()) {
                            startNewFile = true;
                            hitMaxFileSize = true;
                        }
                        if (settings.getMaxFileParts() != null
                                && stroomZipOutputStream.getEntryCount() > settings.getMaxFileParts()) {
                            startNewFile = true;
                            hitMaxFileParts = true;
                        }
                        if (startNewFile) {
                            if (!settings.isMultipleFiles()) {
                                // Process no more !
                                result.setHitMaxFileParts(hitMaxFileParts);
                                result.setHitMaxFileSize(hitMaxFileSize);
                                break;
                            } else {
                                stroomZipOutputStream.close();
                                stroomZipOutputStream = null;
                            }
                        }
                    } catch (final RuntimeException e) {
                        LOGGER.error(e.getMessage(), e);
                        result.addMessage(new Message(Severity.WARNING, e.getMessage()));
                    }
                }

                if (stroomZipOutputStream != null) {
                    if (id == 0) {
                        stroomZipOutputStream.closeDelete();
                    } else {
                        stroomZipOutputStream.close();
                    }
                    stroomZipOutputStream = null;
                }

                return result;
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                closeDelete(stroomZipOutputStream);
                taskContext.info(() -> "done");
            }
        });
    }

    private long downloadStream(final TaskContext taskContext,
                                final long metaId,
                                final StroomZipOutputStream stroomZipOutputStream,
                                final long startId,
                                final Long maxParts) throws IOException {
        long id = startId;

        // Export Source
        try (final Source source = streamStore.openSource(metaId, true)) {
            id++;

            final long count = source.count();

            if (maxParts == null || count < maxParts) {
                for (int index = 0; index < count; index++) {
                    try (final InputStreamProvider inputStreamProvider = source.get(index)) {
                        final int pos = index;
                        taskContext.info(() -> "Data Input " + pos + "/" + count);

                        String baseName = StroomFileNameUtil.getIdPath(id);
                        if (count > 1) {
                            baseName += AGGREGATION_DELIMITER + (index + 1);
                        }

                        // Write out the manifest
                        if (index == 0) {
                            try (final OutputStream outputStream = stroomZipOutputStream
                                    .addEntry(StroomZipEntry.createFromBaseName(
                                            baseName,
                                            StroomZipFileType.MANIFEST).getFullName())) {
                                AttributeMapUtil.write(source.getAttributes(), outputStream);
                            }
                        }
                        try (final InputStream dataInputStream = inputStreamProvider.get()) {
                            streamToStream(dataInputStream,
                                    stroomZipOutputStream,
                                    baseName,
                                    StroomZipFileType.DATA);
                        }
                        try (final InputStream metaInputStream = inputStreamProvider.get(StreamTypeNames.META)) {
                            streamToStream(metaInputStream,
                                    stroomZipOutputStream,
                                    baseName,
                                    StroomZipFileType.META);
                        }
                        try (final InputStream contextInputStream = inputStreamProvider.get(StreamTypeNames.CONTEXT)) {
                            streamToStream(contextInputStream,
                                    stroomZipOutputStream,
                                    baseName,
                                    StroomZipFileType.CONTEXT);
                        }
                    }
                }
            }
        }
        return id;
    }

    private void streamToStream(final InputStream inputStream,
                                final StroomZipOutputStream zipOutputStream,
                                final String baseName,
                                final StroomZipFileType fileType) throws IOException {
        if (inputStream != null) {
            final StroomZipEntry stroomZipEntry = StroomZipEntry.createFromBaseName(baseName, fileType);
            try (final OutputStream outputStream = zipOutputStream.addEntry(stroomZipEntry.getFullName())) {
                StreamUtil.streamToStream(inputStream, outputStream);
            }
        }
    }

    private void closeDelete(final StroomZipOutputStream stroomZipOutputStream) {
        if (stroomZipOutputStream != null) {
            try {
                stroomZipOutputStream.closeDelete();
            } catch (final IOException e) {
                LOGGER.error("closeDelete() - Failed to delete " + stroomZipOutputStream);
            }
        }
    }

    private StroomZipOutputStream getStroomZipOutputStream(final TaskContext taskContext,
                                                           final Path outputDir,
                                                           final String format,
                                                           final AttributeMap attributeMap)
            throws IOException {
        final String filename = StroomFileNameUtil
                .constructFilename(null, fileCount.incrementAndGet(), format, attributeMap, ZIP_EXTENSION);
        final Path file = outputDir.resolve(filename);

        taskContext.info(() -> FileUtil.getCanonicalPath(file));

        final StroomZipOutputStreamImpl outputStream;

        // Create directories and files in a synchronized way so that the clean() method will not remove empty
        // directories that we are just about to write to.
        final Path dir = file.getParent();
        // Ensure parent dirs exist
        Files.createDirectories(dir);

        outputStream = new StroomZipOutputStreamImpl(file, taskContext);

        return outputStream;
    }
}
