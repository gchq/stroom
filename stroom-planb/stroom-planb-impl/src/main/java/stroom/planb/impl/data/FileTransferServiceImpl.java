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

import stroom.security.api.SecurityContext;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;

@Singleton
public class FileTransferServiceImpl implements FileTransferService {

    private final PartDestination partReceiver;
    private final SecurityContext securityContext;
    private final ShardManager shardManager;


    @Inject
    public FileTransferServiceImpl(final PartDestination partReceiver,
                                   final SecurityContext securityContext,
                                   final ShardManager shardManager) {
        this.partReceiver = partReceiver;
        this.securityContext = securityContext;
        this.shardManager = shardManager;
    }

    /**
     * Check that we can supply a snapshot and open it ready for streaming.
     *
     * @param request The request for a snapshot.
     * @return The snapshot, which the caller must close.
     */
    @Override
    public InputStream openSnapshot(final SnapshotRequest request) {
        if (!securityContext.isProcessingUser()) {
            throw new PermissionException(securityContext.getUserRef(), "Only processing users can use this resource");
        }
        return shardManager.openSnapshot(request);
    }

    /**
     * Receive a part file to add to an existing shard.
     *
     * @param createTime
     * @param metaId
     * @param fileHash
     * @param fileName
     * @param inputStream
     * @param synchroniseMerge
     * @throws IOException
     */
    @Override
    public void receivePart(final long createTime,
                            final long metaId,
                            final String fileHash,
                            final String fileName,
                            final boolean synchroniseMerge,
                            final InputStream inputStream) throws IOException {
        partReceiver.receiveRemotePart(createTime, metaId, fileHash, fileName, synchroniseMerge, inputStream);
    }
}
