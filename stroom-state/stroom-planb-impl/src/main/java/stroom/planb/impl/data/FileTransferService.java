package stroom.planb.impl.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface FileTransferService {

    void checkSnapshotStatus(SnapshotRequest request);

    void fetchSnapshot(SnapshotRequest request, OutputStream outputStream) throws IOException;

    void receivePart(long createTime,
                     long metaId,
                     String fileHash,
                     String fileName,
                     boolean synchroniseMerge,
                     InputStream inputStream) throws IOException;
}
