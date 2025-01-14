package stroom.planb.impl.data;

import java.nio.file.Path;

public interface FileTransferClient {

    void storePart(FileDescriptor fileDescriptor,
                   Path path);

    void fetchSnapshot(String nodeName,
                       SnapshotRequest request,
                       Path snapshotDir);
}
