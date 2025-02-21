package stroom.proxy.app.handler;

import java.nio.file.Path;
import java.util.List;

public class FileGroup {

    private static final String META_FILE = "proxy.meta";
    private static final String ZIP_FILE = "proxy.zip";
    private static final String ENTRIES_FILE = "proxy.entries";

    private final Path zip;
    private final Path meta;
    private final Path entries;

    public FileGroup(final Path parentDir) {
        this.zip = parentDir.resolve(ZIP_FILE);
        this.meta = parentDir.resolve(META_FILE);
        this.entries = parentDir.resolve(ENTRIES_FILE);
    }

    public Path getZip() {
        return zip;
    }

    public Path getMeta() {
        return meta;
    }

    public Path getEntries() {
        return entries;
    }

    /**
     * @return All items in the file group
     */
    public List<Path> items() {
        return List.of(zip, meta, entries);
    }

    @Override
    public String toString() {
        return zip.getParent().toString();
    }
}
