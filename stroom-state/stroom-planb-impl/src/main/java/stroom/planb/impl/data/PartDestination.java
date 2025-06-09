package stroom.planb.impl.data;

import stroom.planb.impl.db.StatePaths;
import stroom.security.api.SecurityContext;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.PermissionException;
import stroom.util.string.StringIdUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class PartDestination {

    private final StagingFileStore fileStore;
    private final SecurityContext securityContext;

    private final Path receiveDir;
    private final AtomicLong receiveId = new AtomicLong();

    @Inject
    public PartDestination(final StagingFileStore fileStore,
                           final SecurityContext securityContext,
                           final StatePaths statePaths) {
        this.fileStore = fileStore;
        this.securityContext = securityContext;

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
     * @throws IOException
     */
    public void receiveRemotePart(final long createTime,
                                  final long metaId,
                                  final String fileHash,
                                  final String fileName,
                                  final InputStream inputStream) throws IOException {
        if (!securityContext.isProcessingUser()) {
            throw new PermissionException(securityContext.getUserRef(), "Only processing users can use this resource");
        }

        final FileDescriptor fileDescriptor = new FileDescriptor(createTime, metaId, fileHash);
        final String receiveFileName = StringIdUtil.idToString(receiveId.incrementAndGet()) +
                                       SequentialFileStore.ZIP_EXTENSION;
        final Path receiveFile = receiveDir.resolve(receiveFileName);
        StreamUtil.streamToFile(inputStream, receiveFile);
        fileStore.add(fileDescriptor, receiveFile);
    }

    public void receiveLocalPart(final FileDescriptor fileDescriptor,
                                 final Path sourcePath,
                                 final boolean allowMove)
            throws IOException {
        if (allowMove) {
            // If we allow move then we can allow the file store to move the file directly into the store.
            fileStore.add(fileDescriptor, sourcePath);

        } else {
            // Otherwise we need to copy the file to a temporary location first before it can be moved into the store.
            final String receiveFileName = StringIdUtil.idToString(receiveId.incrementAndGet()) +
                                           SequentialFileStore.ZIP_EXTENSION;
            final Path receiveFile = receiveDir.resolve(receiveFileName);
            Files.copy(sourcePath, receiveFile);
            fileStore.add(fileDescriptor, receiveFile);
        }
    }
}
