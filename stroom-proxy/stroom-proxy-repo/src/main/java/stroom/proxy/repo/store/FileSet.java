package stroom.proxy.repo.store;

import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.string.StringIdUtil;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FileSet {

    public static final String META_EXTENSION = ".meta";
    public static final String ZIP_EXTENSION = ".zip";
    public static final String ENTRIES_EXTENSION = ".entries";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FileSet.class);

    private final long id;
    private final Path root;
    private final List<Path> subDirs;
    private final Path dir;
    private final Path zip;
    private final Path meta;
    private final Path entries;

    private FileSet(final long id,
                    final Path root,
                    final List<Path> subDirs,
                    final Path dir,
                    final Path zip,
                    final Path meta,
                    final Path entries) {
        this.id = id;
        this.root = root;
        this.subDirs = subDirs;
        this.dir = dir;
        this.zip = zip;
        this.meta = meta;
        this.entries = entries;
    }

    public static FileSet get(final Path root,
                              final long id,
                              final boolean nested) {
        // Convert the id to a padded string.
        final String idString = StringIdUtil.idToString(id);
        Path dir = root;
        final List<Path> subDirs = new ArrayList<>();

        // Create sub dirs if nested.
        if (nested) {
            // Add depth.
            final int depth = (idString.length() / 3) - 1;
            dir = dir.resolve(Integer.toString(depth));
            subDirs.add(dir);

            // Add dirs from parts of id string.
            for (int i = 0; i < idString.length() - 3; i += 3) {
                dir = dir.resolve(idString.substring(i, i + 3));
                subDirs.add(dir);
            }
        }

        final Path zip = dir.resolve(idString +
                ZIP_EXTENSION);
        final Path meta = dir.resolve(idString +
                META_EXTENSION);
        final Path entries = dir.resolve(idString +
                ENTRIES_EXTENSION);
        return new FileSet(id, root, subDirs, dir, zip, meta, entries);
    }

    public long getId() {
        return id;
    }

    public Path getRoot() {
        return root;
    }

    public List<Path> getSubDirs() {
        return subDirs;
    }

    public Path getDir() {
        return dir;
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

    public String getZipFileName() {
        return root.relativize(zip).toString();
    }

    public void delete() throws IOException {
        LOGGER.debug(() -> "Deleting: " + FileUtil.getCanonicalPath(zip));
        delete(zip);
        LOGGER.debug(() -> "Deleting: " + FileUtil.getCanonicalPath(meta));
        delete(meta);
        LOGGER.debug(() -> "Deleting: " + FileUtil.getCanonicalPath(entries));
        delete(entries);

        // Try to delete directories.
        try {
            boolean success = true;
            for (int i = subDirs.size() - 1; i >= 0 && success; i--) {
                final Path path = subDirs.get(i);
                success = Files.deleteIfExists(path);
            }
        } catch (final DirectoryNotEmptyException e) {
            // Expected error.
            LOGGER.trace(e::getMessage, e);
        }
    }

    private void delete(final Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    @Override
    public String toString() {
        return zip.toString();
    }
}
