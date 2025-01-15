package stroom.planb.impl.data;

import stroom.security.api.SecurityContext;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Singleton
public class FileTransferServiceImpl implements FileTransferService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FileTransferServiceImpl.class);

    private final SequentialFileStore fileStore;
    private final SecurityContext securityContext;
    private final ShardManager shardManager;

    @Inject
    public FileTransferServiceImpl(final SequentialFileStore fileStore,
                                   final SecurityContext securityContext,
                                   final ShardManager shardManager) {
        this.fileStore = fileStore;
        this.securityContext = securityContext;
        this.shardManager = shardManager;
    }

    @Override
    public void fetchSnapshot(final SnapshotRequest request, final OutputStream outputStream) throws IOException {
        if (!securityContext.isProcessingUser()) {
            throw new PermissionException(securityContext.getUserRef(), "Only processing users can use this resource");
        }

        // TODO : Possibly create windowed snapshots.

        final String mapName = request.getMapName();
        shardManager.zip(mapName, outputStream);
    }

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
        SequentialFile tempFile = null;
        try {
            tempFile = fileStore.createTemp();
            StreamUtil.streamToFile(inputStream, tempFile.getZip());
            fileStore.add(fileDescriptor, tempFile.getZip());

        } finally {
            // Cleanup.
            if (tempFile != null) {
                try {
                    tempFile.delete();
                } catch (final IOException e) {
                    LOGGER.error(e::getMessage, e);
                }
            }
        }
    }
}
