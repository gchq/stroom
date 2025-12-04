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

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.receive.common.ReceiptIdGenerator;
import stroom.receive.common.StreamTargetStreamHandlers;
import stroom.receive.common.StroomStreamProcessor;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskProgressHandler;
import stroom.util.EntityServiceExceptionUtil;
import stroom.util.concurrent.UniqueId;
import stroom.util.io.StreamUtil;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.function.Consumer;

public class DataUploadTaskHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataUploadTaskHandler.class);

    private static final String FILE_SEPARATOR = ".";
    private static final String GZ = "GZ";

    private final TaskContextFactory taskContextFactory;
    private final SecurityContext securityContext;
    private final StreamTargetStreamHandlers streamHandlers;
    private final ReceiptIdGenerator receiptIdGenerator;

    @Inject
    DataUploadTaskHandler(final TaskContextFactory taskContextFactory,
                          final SecurityContext securityContext,
                          final StreamTargetStreamHandlers streamHandlers,
                          final ReceiptIdGenerator receiptIdGenerator) {
        this.taskContextFactory = taskContextFactory;
        this.securityContext = securityContext;
        this.streamHandlers = streamHandlers;
        this.receiptIdGenerator = receiptIdGenerator;
    }

    public void uploadData(final String fileName,
                           final Path file,
                           final String feedName,
                           final String streamTypeName,
                           final Long effectiveMs,
                           final String metaData) {
        taskContextFactory.context("Upload Data", taskContext ->
                uploadData(taskContext, fileName, file, feedName, streamTypeName, effectiveMs, metaData)).run();
    }

    private void uploadData(final TaskContext taskContext,
                            final String fileName,
                            final Path file,
                            final String feedName,
                            final String typeName,
                            final Long effectiveMs,
                            final String metaData) {
        securityContext.secure(() -> {
            taskContext.info(() -> fileName);
            if (feedName == null) {
                throw new EntityServiceException("Feed not set!");
            }
            if (typeName == null) {
                throw new EntityServiceException("Stream Type not set!");
            }
            if (fileName == null) {
                throw new EntityServiceException("File not set!");
            }

            final String name = fileName.toUpperCase();
            final Instant receivedTime = Instant.now();

            // Create an attribute map that will override all other attributes.
            final AttributeMap attributeMap = new AttributeMap(true);
            if (NullSafe.isNonBlankString(metaData)) {
                try {
                    AttributeMapUtil.read(metaData.getBytes(StreamUtil.DEFAULT_CHARSET), attributeMap);
                } catch (final IOException e) {
                    LOGGER.error("uploadData()", e);
                }
            }

            if (effectiveMs != null) {
                attributeMap.putDateTime(StandardHeaderArguments.EFFECTIVE_TIME, effectiveMs);
            }
            attributeMap.put(StandardHeaderArguments.REMOTE_FILE, fileName);
            attributeMap.put(StandardHeaderArguments.FEED, feedName);
            attributeMap.put(StandardHeaderArguments.TYPE, typeName);
            attributeMap.put(StandardHeaderArguments.USER_AGENT, "STROOM-UI");
            attributeMap.put("UploadedBy", securityContext.getUserIdentityForAudit());
            // Create a new receiptId for the request, so we can track progress and report back the
            // receiptId to the sender
            final UniqueId receiptId = receiptIdGenerator.generateId();
            AttributeMapUtil.addReceiptInfo(attributeMap, receivedTime, receiptId);

            final Consumer<Long> progressHandler = new TaskProgressHandler(taskContext, "Uploading");

            if (name.endsWith(FILE_SEPARATOR + StandardHeaderArguments.COMPRESSION_ZIP)) {
                attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_ZIP);
                uploadZipFile(file, feedName, typeName, attributeMap, progressHandler);

            } else if (name.endsWith(FILE_SEPARATOR + StandardHeaderArguments.COMPRESSION_GZIP) ||
                       name.endsWith(FILE_SEPARATOR + GZ)) {
                attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_GZIP);
                uploadStreamFile(file, feedName, typeName, attributeMap, progressHandler);

            } else {
                uploadStreamFile(file, feedName, typeName, attributeMap, progressHandler);
            }
        });
    }

    private void uploadZipFile(final Path zipFile,
                               final String feedName,
                               final String typeName,
                               final AttributeMap attributeMap,
                               final Consumer<Long> progressHandler) {
        streamHandlers.handle(feedName, typeName, attributeMap, handler -> {
            final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(
                    attributeMap,
                    handler,
                    progressHandler);
            stroomStreamProcessor.processZipFile(zipFile);
        });
    }

    private void uploadStreamFile(final Path file,
                                  final String feedName,
                                  final String typeName,
                                  final AttributeMap attributeMap,
                                  final Consumer<Long> progressHandler) {
        try (final InputStream inputStream = Files.newInputStream(file)) {
            streamHandlers.handle(feedName, typeName, attributeMap, handler -> {
                final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(
                        attributeMap,
                        handler,
                        progressHandler);
                stroomStreamProcessor.processInputStream(inputStream, "Upload");
            });
        } catch (final RuntimeException | IOException e) {
            throw EntityServiceExceptionUtil.create(e);
        }
    }
}
