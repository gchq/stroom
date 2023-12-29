package stroom.proxy.app.handler;

import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.string.StringIdUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DirUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DirUtil.class);

    public static Path createPath(final Path root,
                                  final long id) {
        // Convert the id to a padded string.
        final String idString = StringIdUtil.idToString(id);
        Path dir = root;

        // Create sub dirs.
        // Add depth.
        final int depth = (idString.length() / 3) - 1;
        dir = dir.resolve(Integer.toString(depth));

        // Add dirs from parts of id string.
        for (int i = 0; i < idString.length() - 3; i += 3) {
            dir = dir.resolve(idString.substring(i, i + 3));
        }

        dir = dir.resolve(idString);

        return dir;
    }

    public static void ensureDirExists(final Path path) {
        try {
            Files.createDirectories(path);
        } catch (final IOException e) {
            LOGGER.error(() -> "Error creating directories for: " + FileUtil.getCanonicalPath(path), e);
            throw new UncheckedIOException(e);
        }
    }
}
