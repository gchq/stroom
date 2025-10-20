package stroom.proxy.app.handler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A provider of unique paths such that each directory never contains more than 999 items.
 */
public class NestedNumberedDirProvider {

    private final AtomicLong dirId;
    private final Path root;

    NestedNumberedDirProvider(final Path root) {
        this.root = Objects.requireNonNull(root);
        this.dirId = new AtomicLong(DirUtil.getMaxDirId(root));
    }

    public static NestedNumberedDirProvider create(final Path root) {
        return new NestedNumberedDirProvider(root);
    }

    /**
     * Each call to this creates a unique subdirectory of the root path.
     * <p>
     * e.g. {@code root_path/2/333/555/333555777}
     * </p>
     *
     * @throws UncheckedIOException If the new path cannot be created.
     */
    public Path createNumberedPath() {
        final Path path = DirUtil.createPath(root, dirId.incrementAndGet());
        try {
            Files.createDirectories(path);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return path;
    }

    @Override
    public String toString() {
        return "NumberedDirProvider{" +
               "dirId=" + dirId +
               ", root=" + root +
               '}';
    }
}
