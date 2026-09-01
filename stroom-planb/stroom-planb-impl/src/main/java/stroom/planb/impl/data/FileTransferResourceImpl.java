/*
 * Copyright 2025 Crown Copyright
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

import stroom.docstore.api.DocumentNotFoundException;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.IOException;
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
            // Open the snapshot before we commit to a response status, as it is hard to capture meaningful errors
            // mid stream. Anything that can fail must fail now, otherwise the client gets a successful response
            // with an empty or truncated body. See gh-5689.
            final InputStream snapshot = fileTransferServiceProvider.get().openSnapshot(request);

            // We now own an open snapshot. The streaming output takes over responsibility for closing it, but
            // only once we have successfully handed it to the response, so anything that fails in between has
            // to close it here or the file is leaked.
            try {
                // Stream the snapshot content to the client as ZIP data
                final StreamingOutput streamingOutput = output -> {
                    try (snapshot) {
                        StreamUtil.streamToStream(snapshot, output);
                    } catch (final Exception e) {
                        // The status has already been sent so we can't report this to the client as an error.
                        // Log it and let it abort the response, rather than quietly supplying a truncated
                        // snapshot.
                        LOGGER.error(() -> "Error streaming snapshot: " + request + " " + e.getMessage(), e);
                        throw e;
                    }
                };

                LOGGER.debug(() -> "Sending snapshot: " + request);
                return Response
                        .ok(streamingOutput, MediaType.APPLICATION_OCTET_STREAM)
                        .build();

            } catch (final Throwable t) {
                try {
                    snapshot.close();
                } catch (final IOException e) {
                    t.addSuppressed(e);
                }
                throw t;
            }
        } catch (final NotModifiedException e) {
            LOGGER.debug(() -> "Snapshot not modified: " + request + " " + e.getMessage(), e);
            throw error(Status.NOT_MODIFIED, e);
        } catch (final PermissionException e) {
            LOGGER.error(() -> "Snapshot permission exception: " + request + " " + e.getMessage(), e);
            throw error(Status.UNAUTHORIZED, e);
        } catch (final SnapshotNotFoundException | DocumentNotFoundException e) {
            // Expected, and usually transient, so log without a stack trace but do log it, as this is the only
            // place the reason is known.
            LOGGER.warn(() -> "Unable to supply snapshot: " + request + " " + e.getMessage());
            LOGGER.debug(e::getMessage, e);
            throw error(Status.NOT_FOUND, e);
        } catch (final Exception e) {
            // Anything else is a real failure and must not be reported to the client as a 404.
            LOGGER.error(() -> "Error supplying snapshot: " + request + " " + e.getMessage(), e);
            throw error(Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * Build a {@link WebApplicationException} that carries the reason in the response body. Note that the status
     * reason phrase alone is not enough, as the client can only ever see the generic phrase for the status code,
     * e.g. 'Not Found', which tells it nothing about what actually went wrong.
     */
    private WebApplicationException error(final Status status, final Exception e) {
        final String message = NullSafe.isBlankString(e.getMessage())
                ? e.getClass().getSimpleName()
                : e.getMessage().trim();
        return new WebApplicationException(Response
                .status(status)
                .entity(message)
                .type(MediaType.TEXT_PLAIN)
                .build());
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
