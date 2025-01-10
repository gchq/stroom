package stroom.planb.impl.data;

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

public class SequentialFile {

    public static final String ZIP_EXTENSION = ".zip";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SequentialFile.class);

    private final long id;
    private final Path root;
    private final List<Path> subDirs;
    private final Path dir;
    private final Path zip;

    private SequentialFile(final long id,
                           final Path root,
                           final List<Path> subDirs,
                           final Path dir,
                           final Path zip) {
        this.id = id;
        this.root = root;
        this.subDirs = subDirs;
        this.dir = dir;
        this.zip = zip;
    }

    public static SequentialFile get(final Path root,
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
        return new SequentialFile(id, root, subDirs, dir, zip);
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

    public String getZipFileName() {
        return root.relativize(zip).toString();
    }

    public void delete() throws IOException {
        LOGGER.debug(() -> "Deleting: " + FileUtil.getCanonicalPath(zip));
        Files.deleteIfExists(zip);

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

    @Override
    public String toString() {
        return zip.toString();
    }
}
