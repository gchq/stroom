package stroom.planb.impl.data;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.planb.impl.io.StatePaths;
import stroom.security.api.SecurityContext;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PermissionException;
import stroom.util.zip.ZipUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Singleton
public class FileTransferServiceImpl implements FileTransferService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FileTransferServiceImpl.class);

    private final SequentialFileStore fileStore;
    private final SecurityContext securityContext;
    private final Path shardDir;

    @Inject
    public FileTransferServiceImpl(final SequentialFileStore fileStore,
                                   final SecurityContext securityContext,
                                   final StatePaths statePaths,
                                   final ByteBufferFactory byteBufferFactory) {
        this.fileStore = fileStore;
        this.securityContext = securityContext;
        shardDir = statePaths.getShardDir();
    }

    @Override
    public void fetchSnapshot(final SnapshotRequest request, final OutputStream outputStream) throws IOException {
        if (!securityContext.isProcessingUser()) {
            throw new PermissionException(securityContext.getUserRef(), "Only processing users can use this resource");
        }

        final Path shard = shardDir.resolve(request.getMapName());
        if (!Files.exists(shard)) {
            throw new RuntimeException("Shard not found");
        }

        // TODO : Possibly create windowed snapshots.

        // For now we'll just stream the whole map to the requestor. In future we could easily just create a time
        // window snapshot.

        try (final ZipArchiveOutputStream zipOutputStream =
                ZipUtil.createOutputStream(new BufferedOutputStream(outputStream))) {
            ZipUtil.zip(shard, zipOutputStream);
        }
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
