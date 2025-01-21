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
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class FileTransferServiceImpl implements FileTransferService {

    private final SequentialFileStore fileStore;
    private final SecurityContext securityContext;
    private final ShardManager shardManager;

    private final Path receiveDir;
    private final AtomicLong receiveId = new AtomicLong();

    @Inject
    public FileTransferServiceImpl(final SequentialFileStore fileStore,
                                   final SecurityContext securityContext,
                                   final ShardManager shardManager,
                                   final StatePaths statePaths) {
        this.fileStore = fileStore;
        this.securityContext = securityContext;
        this.shardManager = shardManager;

        // Create the receive directory.
        receiveDir = statePaths.getReceiveDir();
        FileUtil.ensureDirExists(receiveDir);
        if (!FileUtil.deleteContents(receiveDir)) {
            throw new RuntimeException("Unable to delete contents of: " + FileUtil.getCanonicalPath(receiveDir));
        }
    }

    /**
     * Determine if we are allowed to create a snapshot or if the snapshot we have is already the latest.
     *
     * @param request The request to create a snapshot.
     */
    @Override
    public void checkSnapshotStatus(final SnapshotRequest request) {
        if (!securityContext.isProcessingUser()) {
            throw new PermissionException(securityContext.getUserRef(), "Only processing users can use this resource");
        }
        shardManager.checkSnapshotStatus(request);
    }

    /**
     * Actually create a snapshot and stream it to the supplied output stream.
     *
     * @param request      The request to create a snapshot.
     * @param outputStream The output stream to write the snapshot to.
     */
    @Override
    public void fetchSnapshot(final SnapshotRequest request, final OutputStream outputStream) throws IOException {
        // We will have already checked that we have a processing user but check again just in case.
        if (!securityContext.isProcessingUser()) {
            throw new PermissionException(securityContext.getUserRef(), "Only processing users can use this resource");
        }
        shardManager.createSnapshot(request, outputStream);
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
    @Override
    public void receivePart(final long createTime,
                            final long metaId,
                            final String fileHash,
                            final String fileName,
                            final InputStream inputStream) throws IOException {
        if (!securityContext.isProcessingUser()) {
            throw new PermissionException(securityContext.getUserRef(), "Only processing users can use this resource");
        }

        final FileDescriptor fileDescriptor = new FileDescriptor(createTime, metaId, fileHash);
        final String receiveFileName = StringIdUtil.idToString(receiveId.incrementAndGet()) +
                                       SequentialFile.ZIP_EXTENSION;
        final Path receiveFile = receiveDir.resolve(receiveFileName);
        StreamUtil.streamToFile(inputStream, receiveFile);
        fileStore.add(fileDescriptor, receiveFile);
    }
}
