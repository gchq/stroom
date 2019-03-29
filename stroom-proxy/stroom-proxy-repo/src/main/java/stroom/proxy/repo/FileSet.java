package stroom.proxy.repo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FileSet {
    private final String feed;
    private long totalUncompressedFileSize;
    private long totalZipEntryCount;
    private final List<Path> files;

    FileSet(final String feed) {
        this.feed = feed;
        this.files = new ArrayList<>();
    }

    void add(ZipInfo zipInfo) {
        files.add(zipInfo.getPath());
        totalUncompressedFileSize += zipInfo.getUncompressedSize();
        totalZipEntryCount += zipInfo.getZipEntryCount();
    }

    public String getFeed() {
        return feed;
    }

    public long getTotalUncompressedFileSize() {
        return totalUncompressedFileSize;
    }

    public long getTotalZipEntryCount() {
        return totalZipEntryCount;
    }

    public List<Path> getFiles() {
        return files;
    }

    @Override
    public String toString() {
        if (files.size() == 1) {
            return feed + " (1 file)";
        }
        return feed + " (" + files.size() + " files)";
    }
}