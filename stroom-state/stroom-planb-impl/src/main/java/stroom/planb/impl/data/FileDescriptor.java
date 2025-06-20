package stroom.planb.impl.data;

import java.nio.file.Path;

public record FileDescriptor(long createTimeMs, long metaId, String fileHash) {

    public FileInfo getInfo(final Path path) {
        return new FileInfo(
                createTimeMs,
                metaId,
                fileHash,
                path.getFileName().toString());
    }
}
