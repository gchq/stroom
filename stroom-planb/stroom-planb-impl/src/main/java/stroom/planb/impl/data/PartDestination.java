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

import stroom.planb.impl.db.StatePaths;
import stroom.security.api.SecurityContext;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PermissionException;
import stroom.util.string.StringIdUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class PartDestination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PartDestination.class);

    private final SecurityContext securityContext;
    private final Provider<MergeProcessor> mergeProcessorProvider;

    private final Path receiveDir;
    private final AtomicLong receiveId = new AtomicLong();

    @Inject
    public PartDestination(final SecurityContext securityContext,
                           final StatePaths statePaths,
                           final Provider<MergeProcessor> mergeProcessorProvider) {
        this.securityContext = securityContext;
        this.mergeProcessorProvider = mergeProcessorProvider;

        // Create the receive directory.
        receiveDir = statePaths.getReceiveDir();
        FileUtil.ensureDirExists(receiveDir);
        if (!FileUtil.deleteContents(receiveDir)) {
            throw new RuntimeException("Unable to delete contents of: " + FileUtil.getCanonicalPath(receiveDir));
        }
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
    public void receiveRemotePart(final long createTime,
                                  final long metaId,
                                  final String fileHash,
                                  final String fileName,
                                  final boolean synchroniseMerge,
                                  final InputStream inputStream) throws IOException {
        final FileInfo fileInfo = new FileInfo(createTime, metaId, fileHash, fileName);
        LOGGER.debug(() -> "Receiving remote part: " + fileInfo);

        if (!securityContext.isProcessingUser()) {
            throw new PermissionException(securityContext.getUserRef(), "Only processing users can use this resource");
        }

        final FileDescriptor fileDescriptor = new FileDescriptor(createTime, metaId, fileHash);
        final String receiveFileName = StringIdUtil.idToString(receiveId.incrementAndGet()) +
                                       SequentialFileStore.ZIP_EXTENSION;
        final Path receiveFile = receiveDir.resolve(receiveFileName);
        StreamUtil.streamToFile(inputStream, receiveFile);

        mergeProcessorProvider.get().add(fileDescriptor, receiveFile, synchroniseMerge);
    }

    public void receiveLocalPart(final FileDescriptor fileDescriptor,
                                 final Path sourcePath,
                                 final boolean allowMove,
                                 final boolean synchroniseMerge) throws IOException {
        final FileInfo fileInfo = fileDescriptor.getInfo(sourcePath);
        LOGGER.debug(() -> "Receiving remote part: " + fileInfo);

        final MergeProcessor mergeProcessor = mergeProcessorProvider.get();
        if (allowMove) {
            // If we allow move then we can allow the file store to move the file directly into the store.
            mergeProcessor.add(fileDescriptor, sourcePath, synchroniseMerge);

        } else {
            // Otherwise we need to copy the file to a temporary location first before it can be moved into the store.
            final String receiveFileName = StringIdUtil.idToString(receiveId.incrementAndGet()) +
                                           SequentialFileStore.ZIP_EXTENSION;
            final Path receiveFile = receiveDir.resolve(receiveFileName);
            Files.copy(sourcePath, receiveFile);
            mergeProcessor.add(fileDescriptor, receiveFile, synchroniseMerge);
        }
    }
}
