/*
 * Copyright 2016 Crown Copyright
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.data.zip.StroomFileNameUtil;
import stroom.data.zip.StroomZipEntry;
import stroom.data.zip.StroomZipFileType;
import stroom.data.zip.StroomZipOutputStream;
import stroom.data.zip.StroomZipOutputStreamImpl;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.MetaService;
import stroom.meta.api.StandardHeaderArguments;
import stroom.meta.api.AttributeMap;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.security.api.SecurityContext;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskContext;
import stroom.util.io.BufferFactory;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LogItemProgress;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class DataDownloadTaskHandler extends AbstractTaskHandler<DataDownloadTask, DataDownloadResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataDownloadTaskHandler.class);

    private static final String AGGREGATION_DELIMITER = "_";
    private static final String ZIP_EXTENSION = ".zip";

    private final TaskContext taskContext;
    private final Store streamStore;
    private final MetaService metaService;
    private final SecurityContext securityContext;
    private final BufferFactory bufferFactory;

    private final AtomicLong fileCount = new AtomicLong(0);
    private String lastPossibleFileName;
    private String lastFeedName;

    @Inject
    public DataDownloadTaskHandler(final TaskContext taskContext,
                                   final Store streamStore,
                                   final MetaService metaService,
                                   final SecurityContext securityContext,
                                   final BufferFactory bufferFactory) {
        this.taskContext = taskContext;
        this.streamStore = streamStore;
        this.metaService = metaService;
        this.securityContext = securityContext;
        this.bufferFactory = bufferFactory;
    }

    @Override
    public DataDownloadResult exec(final DataDownloadTask task) {
        return securityContext.secureResult(() -> downloadData(task));
    }

    private DataDownloadResult downloadData(final DataDownloadTask task) {
        final FindMetaCriteria criteria = task.getCriteria();
        final DataDownloadSettings settings = task.getSettings();
        final List<Meta> list = metaService.find(criteria).getValues();
        final DataDownloadResult result = new DataDownloadResult();
        if (list.size() == 0) {
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
                    final String possibleFilename = StroomFileNameUtil.constructFilename(null, 0, task.getFormat(), metaMap, ZIP_EXTENSION);
                    if (stroomZipOutputStream != null && (!possibleFilename.equals(lastPossibleFileName) || !meta.getFeedName().equals(lastFeedName))) {
                        stroomZipOutputStream.close();
                        stroomZipOutputStream = null;
                    }
                    lastPossibleFileName = possibleFilename;
                    lastFeedName = meta.getFeedName();

                    // Open a zip output stream if we don't currently have one.
                    if (stroomZipOutputStream == null) {
                        stroomZipOutputStream = getStroomZipOutputStream(task.getOutputDir(), task.getFormat(), metaMap);
                        id = 0;
                    }

                    // Write to the output stream.
                    result.incrementRecordsWritten();
                    logItemProgress.incrementProgress();
                    id = downloadStream(taskContext, meta.getId(), stroomZipOutputStream, id,
                            settings.getMaxFileParts());

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
    }

    private long downloadStream(final TaskContext taskContext,
                                final long streamId,
                                final StroomZipOutputStream stroomZipOutputStream,
                                final long startId,
                                final Long maxParts) throws IOException {
        long id = startId;

        // Export Source
        try (final Source source = streamStore.openSource(streamId)) {
            id++;

            final long count = source.count();

            if (maxParts == null || count < maxParts) {
                for (int index = 0; index < count; index++) {
                    try (final InputStreamProvider inputStreamProvider = source.get(index)) {
                        final int pos = index;
                        taskContext.info(() -> "Data Input " + pos + "/" + count);

                        String basePartName = StroomFileNameUtil.getIdPath(id);
                        if (count > 1) {
                            basePartName += AGGREGATION_DELIMITER + (index + 1);
                        }

                        // Write out the manifest
                        if (index == 0) {
                            try (final OutputStream outputStream = stroomZipOutputStream
                                    .addEntry(new StroomZipEntry(null, basePartName, StroomZipFileType.Manifest).getFullName())) {
                                AttributeMapUtil.write(source.getAttributes(), outputStream);
                            }
                        }

                        try (final InputStream dataInputStream = inputStreamProvider.get()) {
                            streamToStream(dataInputStream, stroomZipOutputStream, basePartName, StroomZipFileType.Data);
                        }
                        try (final InputStream metaInputStream = inputStreamProvider.get(StreamTypeNames.META)) {
                            streamToStream(metaInputStream, stroomZipOutputStream, basePartName, StroomZipFileType.Meta);
                        }
                        try (final InputStream contextInputStream = inputStreamProvider.get(StreamTypeNames.CONTEXT)) {
                            streamToStream(contextInputStream, stroomZipOutputStream, basePartName, StroomZipFileType.Context);
                        }
                    }
                }
            }
        }
        return id;
    }

    private void streamToStream(final InputStream inputStream,
                                final StroomZipOutputStream zipOutputStream,
                                final String basePartName,
                                final StroomZipFileType fileType) throws IOException {
        if (inputStream != null) {
            final StroomZipEntry stroomZipEntry = new StroomZipEntry(null, basePartName, fileType);
            try (final OutputStream outputStream = zipOutputStream.addEntry(stroomZipEntry.getFullName())) {
                final byte[] buffer = bufferFactory.create();

                int len;
                while ((len = StreamUtil.eagerRead(inputStream, buffer)) != -1) {
                    outputStream.write(buffer, 0, len);
                }
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

    private StroomZipOutputStream getStroomZipOutputStream(final Path outputDir, final String format, final AttributeMap attributeMap)
            throws IOException {
        final String filename = StroomFileNameUtil.constructFilename(null, fileCount.incrementAndGet(), format,
                attributeMap, ZIP_EXTENSION);
        final Path file = outputDir.resolve(filename);

        taskContext.info(() -> FileUtil.getCanonicalPath(file));

        StroomZipOutputStreamImpl outputStream;

        // Create directories and files in a synchronized way so that the clean() method will not remove empty
        // directories that we are just about to write to.
        final Path dir = file.getParent();
        // Ensure parent dir's exist
        Files.createDirectories(dir);

        outputStream = new StroomZipOutputStreamImpl(file, taskContext, false);

        return outputStream;
    }
}
