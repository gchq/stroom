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

package stroom.data.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.data.receipt.BufferFactory;
import stroom.entity.shared.BaseResultList;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaService;
import stroom.proxy.repo.StroomFileNameUtil;
import stroom.proxy.repo.StroomZipEntry;
import stroom.proxy.repo.StroomZipFileType;
import stroom.proxy.repo.StroomZipOutputStream;
import stroom.proxy.repo.StroomZipOutputStreamImpl;
import stroom.security.Security;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskContext;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LogItemProgress;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;

class DataDownloadTaskHandler extends AbstractTaskHandler<DataDownloadTask, DataDownloadResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataDownloadTaskHandler.class);

    private static final String AGGREGATION_DELIMITER = "_";

    private final TaskContext taskContext;
    private final Store streamStore;
    private final MetaService metaService;
    private final Security security;
    private final BufferFactory bufferFactory;

    @Inject
    DataDownloadTaskHandler(final TaskContext taskContext,
                            final Store streamStore,
                            final MetaService metaService,
                            final Security security,
                            final BufferFactory bufferFactory) {
        this.taskContext = taskContext;
        this.streamStore = streamStore;
        this.metaService = metaService;
        this.security = security;
        this.bufferFactory = bufferFactory;
    }

    @Override
    public DataDownloadResult exec(final DataDownloadTask task) {
        return security.secureResult(() -> {
            taskContext.info(task.getFile().toString());
            return downloadData(task.getCriteria(), task.getFile(), task.getSettings());
        });
    }

    private DataDownloadResult downloadData(final FindMetaCriteria findMetaCriteria,
                                            Path data,
                                            final StreamDownloadSettings settings) {
        final BaseResultList<Meta> list = metaService.find(findMetaCriteria);

        final DataDownloadResult result = new DataDownloadResult();

        if (list.size() == 0) {
            return result;
        }

        StroomZipOutputStream stroomZipOutputStream = null;
        try {
            stroomZipOutputStream = new StroomZipOutputStreamImpl(data, taskContext, false);

            long id = 0;
            long fileCount = 0;

            final String fileBasePath = FileUtil.getCanonicalPath(data).substring(0, FileUtil.getCanonicalPath(data).lastIndexOf(".zip"));

            final LogItemProgress logItemProgress = new LogItemProgress(0, list.size());
            taskContext.info("Data {}", logItemProgress);

            for (final Meta meta : list) {
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
                        fileCount++;
                        data = Paths.get(fileBasePath + "_" + fileCount + ".zip");
                        stroomZipOutputStream = new StroomZipOutputStreamImpl(data, taskContext, false);
                    }
                }

            }

            if (id == 0) {
                stroomZipOutputStream.closeDelete();
            } else {
                stroomZipOutputStream.close();
            }
            stroomZipOutputStream = null;

            return result;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            closeDelete(data, stroomZipOutputStream);
            taskContext.info("done");
        }
    }

    private long downloadStream(final TaskContext taskContext,
                                final long streamId,
                                final StroomZipOutputStream stroomZipOutputStream,
                                final long startId,
                                final Long maxParts) throws IOException {
        long id = startId;

        // Export Source
        try (final Source source = streamStore.openStreamSource(streamId)) {
            id++;

            final long count = source.count();

            if (maxParts == null || count < maxParts) {
                long part = -1;
                if (count > 1) {
                    part = 0;
                }
                for (int index = 0; index < count; index++) {
                    try (final InputStreamProvider inputStreamProvider = source.get(index)) {
                        taskContext.info("Data Input {}/{}", index, count);

                        String basePartName = StroomFileNameUtil.getIdPath(id);

                        if (part != -1) {
                            part++;
                            basePartName += AGGREGATION_DELIMITER + part;
                        }

                        // Write out the manifest
                        if (part == 1 || part == -1) {
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

    private void closeDelete(final Path data, final StroomZipOutputStream stroomZipOutputStream) {
        if (stroomZipOutputStream != null) {
            try {
                stroomZipOutputStream.closeDelete();
            } catch (final IOException e) {
                LOGGER.error("closeDelete() - Failed to delete " + data);
            }
        }
    }
}
