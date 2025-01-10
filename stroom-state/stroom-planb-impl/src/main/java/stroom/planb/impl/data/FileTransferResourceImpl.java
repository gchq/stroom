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

package stroom.planb.impl.data;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
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
    public StreamingOutput fetchSnapshot(final SnapshotRequest request) {
        return output -> fileTransferServiceProvider.get().fetchSnapshot(request, output);
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public boolean sendPart(final long createTime,
                            final long metaId,
                            final String fileHash,
                            final String fileName,
                            final InputStream inputStream) {
        try {
            fileTransferServiceProvider.get().receivePart(createTime, metaId, fileHash, fileName, inputStream);
            return true;
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
            throw new RuntimeException(e);
        }
    }
}
