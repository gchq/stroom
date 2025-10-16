package stroom.proxy.app.handler;

import java.nio.file.Path;
import java.util.List;

public class FileGroup {

    static final String BASE_FILENAME = "proxy";
    static final String META_EXTENSION = "meta";
    static final String ZIP_EXTENSION = "zip";
    static final String ENTRIES_EXTENSION = "entries";
    static final String META_FILE = BASE_FILENAME + "." + META_EXTENSION;
    static final String ZIP_FILE = BASE_FILENAME + "." + ZIP_EXTENSION;
    static final String ENTRIES_FILE = BASE_FILENAME + "." + ENTRIES_EXTENSION;

    private final Path parentDir;
    private final Path zip;
    private final Path meta;
    private final Path entries;

    public FileGroup(final Path parentDir) {
        this.parentDir = parentDir;
        this.zip = parentDir.resolve(ZIP_FILE);
        this.meta = parentDir.resolve(META_FILE);
        this.entries = parentDir.resolve(ENTRIES_FILE);
    }

    /**
     * @return The path to the .zip file
     */
    public Path getZip() {
        return zip;
    }

    /**
     * @return The path to the .meta file
     */
    public Path getMeta() {
        return meta;
    }

    /**
     * @return The .entries file containing the {@link ZipEntryGroup}s, serialised as
     * JSON with one per line.
     */
    public Path getEntries() {
        return entries;
    }

    /**
     * @return The parent dir that contains the meta, zip and entries files.
     */
    public Path getParentDir() {
        return parentDir;
    }

    /**
     * @return All items in the file group
     */
    public List<Path> items() {
        return List.of(zip, meta, entries);
    }

    @Override
    public String toString() {
        return parentDir.toString();
    }
}
