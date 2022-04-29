package stroom.proxy.repo.store;

import stroom.proxy.repo.ProxyRepoFileNames;
import stroom.util.io.FileUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FileSet {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileSet.class);

    private final long id;
    private final String idString;
    private final Path root;
    private final List<Path> subDirs;
    private final Path dir;
    private final Path zip;
    private final Path meta;

    private FileSet(final long id,
                    final String idString,
                    final Path root,
                    final List<Path> subDirs,
                    final Path dir,
                    final Path zip,
                    final Path meta) {
        this.id = id;
        this.idString = idString;
        this.root = root;
        this.subDirs = subDirs;
        this.dir = dir;
        this.zip = zip;
        this.meta = meta;
    }

    public static FileSet get(final Path root,
                              final long id) {
        // Convert the id to a padded string.
        final String idString = idToString(id);

        // Create sub dirs.
        final List<Path> subDirs = new ArrayList<>();
        Path dir = root;

        // Add depth.
        final int depth = (idString.length() / 3) - 1;
        dir = dir.resolve(Integer.toString(depth));
        subDirs.add(dir);

        // Add dirs from parts of id string.
        for (int i = 0; i < idString.length() - 3; i += 3) {
            dir = dir.resolve(idString.substring(i, i + 3));
            subDirs.add(dir);
        }

        final Path zip = dir.resolve(idString +
                ProxyRepoFileNames.ZIP_EXTENSION);
        final Path meta = dir.resolve(idString +
                ProxyRepoFileNames.META_EXTENSION);
        return new FileSet(id, idString, root, subDirs, dir, zip, meta);
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

    public Path getError() {
        return dir.resolve(idString +
                ProxyRepoFileNames.ERROR_EXTENSION);
    }

    public Path getBadZip() {
        return dir.resolve(idString +
                ProxyRepoFileNames.ZIP_EXTENSION +
                ProxyRepoFileNames.BAD_EXTENSION);
    }

    public String getZipFileName() {
        return root.relativize(zip).toString();
    }

    public void delete() throws IOException {
        LOGGER.debug("Deleting: " + FileUtil.getCanonicalPath(zip));
        Files.deleteIfExists(zip);
        LOGGER.debug("Deleting: " + FileUtil.getCanonicalPath(meta));
        Files.deleteIfExists(meta);
        Files.deleteIfExists(getError());
        Files.deleteIfExists(getBadZip());
    }

    private static String idToString(long id) {
        final StringBuilder sb = new StringBuilder();
        sb.append(id);
        // Pad out e.g. 10100 -> 010100
        while ((sb.length() % 3) != 0) {
            sb.insert(0, '0');
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return zip.toString();
    }
}
