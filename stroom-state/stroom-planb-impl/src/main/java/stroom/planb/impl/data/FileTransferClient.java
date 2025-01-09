package stroom.planb.impl.data;

import java.io.IOException;
import java.nio.file.Path;

public interface FileTransferClient {

    void storePart(FileDescriptor fileDescriptor,
                   Path path) throws IOException;

    void fetchSnapshot(String nodeName,
                       SnapshotRequest request,
                       Path snapshotDir) throws IOException;
}
