package stroom.planb.impl.data;

import stroom.security.api.SecurityContext;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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
        shardManager.fetchSnapshot(request, outputStream);
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
