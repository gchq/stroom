package stroom.streamtask.server;

import stroom.feed.MetaMap;
import stroom.util.date.DateUtil;
import stroom.util.io.FileUtil;
import stroom.util.shared.ModelStringUtil;

import java.nio.file.Path;

public class ZipInfo {
    private final Path path;
    private final MetaMap metaMap;
    private final Long uncompressedSize;
    private final Long compressedSize;
    private final Long lastModified;

    ZipInfo(final Path path, final MetaMap metaMap, final Long uncompressedSize, final Long compressedSize, final Long lastModified) {
        this.path = path;
        this.metaMap = metaMap;
        this.uncompressedSize = uncompressedSize;
        this.compressedSize = compressedSize;
        this.lastModified = lastModified;
    }

    public Path getPath() {
        return path;
    }

    public MetaMap getMetaMap() {
        return metaMap;
    }

    public Long getUncompressedSize() {
        return uncompressedSize;
    }

    public Long getCompressedSize() {
        return compressedSize;
    }

    public Long getLastModified() {
        return lastModified;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(FileUtil.getCanonicalPath(path));
        if (metaMap != null) {
            sb.append("\n\tmetaMap=");
            sb.append(metaMap);
        }
        if (uncompressedSize != null) {
            sb.append("\n\tuncompressedSize=");
            sb.append(ModelStringUtil.formatIECByteSizeString(uncompressedSize));
        }
        if (compressedSize != null) {
            sb.append("\n\tcompressedSize=");
            sb.append(ModelStringUtil.formatIECByteSizeString(compressedSize));
        }
        if (lastModified != null) {
            sb.append("\n\tlastModified=");
            sb.append(DateUtil.createNormalDateTimeString(lastModified));
        }
        return sb.toString();
    }
}