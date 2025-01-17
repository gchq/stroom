package stroom.planb.impl.data;

import java.nio.file.Path;
import java.time.Instant;

public interface FileTransferClient {

    void storePart(FileDescriptor fileDescriptor,
                   Path path);

    Instant fetchSnapshot(String nodeName,
                          SnapshotRequest request,
                          Path snapshotDir);
}
