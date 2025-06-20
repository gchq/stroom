package stroom.planb.impl.data;

public record FileInfo(long createTimeMs, long metaId, String fileHash, String fileName) {

    @Override
    public String toString() {
        return "createTimeMs=" + createTimeMs +
               ", metaId=" + metaId +
               ", fileHash=" + fileHash +
               ", fileName=" + fileName;
    }
}
