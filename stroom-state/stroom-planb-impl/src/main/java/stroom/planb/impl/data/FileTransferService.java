package stroom.planb.impl.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface FileTransferService {

    void fetchSnapshot(final SnapshotRequest request, final OutputStream outputStream) throws IOException;

    void receivePart(final long createTime,
                     final long metaId,
                     final String fileHash,
                     final String fileName,
                     final InputStream inputStream) throws IOException;
}
