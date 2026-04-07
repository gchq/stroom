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

package stroom.planb.impl.data;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.InputStream;

@AutoLogged(OperationType.UNLOGGED)
public class FileTransferResourceImpl implements FileTransferResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FileTransferResourceImpl.class);

    private final Provider<FileTransferService> fileTransferServiceProvider;

    @Inject
    public FileTransferResourceImpl(final Provider<FileTransferService> fileTransferServiceProvider) {
        this.fileTransferServiceProvider = fileTransferServiceProvider;
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public Response fetchSnapshot(final SnapshotRequest request) {
        LOGGER.debug(() -> "Snapshot request: " + request);
        try {
            // Check the status before we start streaming snapshot data as it is hard to capture meaningful errors mid
            // stream.
            fileTransferServiceProvider.get().checkSnapshotStatus(request);

            // Stream the snapshot content to the client as ZIP data
            final StreamingOutput streamingOutput = output -> {
                try {
                    fileTransferServiceProvider.get().fetchSnapshot(request, output);
                } catch (final Exception e) {
                    LOGGER.error(e::getMessage, e);
                    throw e;
                }
            };

            LOGGER.debug(() -> "Sending snapshot: " + request);
            return Response
                    .ok(streamingOutput, MediaType.APPLICATION_OCTET_STREAM)
                    .build();
        } catch (final NotModifiedException e) {
            LOGGER.debug(() -> "Snapshot not modified: " + request + " " + e.getMessage(), e);
            throw new WebApplicationException(e.getMessage(), Status.NOT_MODIFIED);
        } catch (final PermissionException e) {
            LOGGER.error(() -> "Snapshot permission exception : " + request + " " + e.getMessage(), e);
            throw new WebApplicationException(e.getMessage(), Status.UNAUTHORIZED);
        } catch (final Exception e) {
            LOGGER.debug(() -> "Snapshot not found: " + request + " " + e.getMessage(), e);
            throw new WebApplicationException(e.getMessage(), Status.NOT_FOUND);
        }
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public Response sendPart(final long createTime,
                             final long metaId,
                             final String fileHash,
                             final String fileName,
                             final boolean synchroniseMerge,
                             final InputStream inputStream) {
        final FileInfo fileInfo = new FileInfo(createTime, metaId, fileHash, fileName);
        try {
            LOGGER.debug(() -> "Receiving part: " + fileInfo);
            fileTransferServiceProvider.get().receivePart(
                    createTime,
                    metaId,
                    fileHash,
                    fileName,
                    synchroniseMerge,
                    inputStream);
            LOGGER.debug(() -> "Successfully received part: " + fileInfo);
            return Response
                    .ok()
                    .build();
        } catch (final PermissionException e) {
            LOGGER.error(LogUtil.message("Permission exception receiving part: " + fileInfo), e);
            return Response
                    .status(Status.UNAUTHORIZED.getStatusCode(), e.getMessage())
                    .build();
        } catch (final Exception e) {
            LOGGER.error(LogUtil.message("Exception receiving part: " + fileInfo), e);
            return Response
                    .status(Status.INTERNAL_SERVER_ERROR.getStatusCode(), e.getMessage())
                    .build();
        }
    }
}
