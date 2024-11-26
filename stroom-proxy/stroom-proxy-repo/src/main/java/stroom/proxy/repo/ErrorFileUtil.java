package stroom.proxy.repo;

import stroom.data.zip.CharsetConstants;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ErrorFileUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ErrorFileUtil.class);

    private static final String ZIP_EXTENSION = ".zip";
    private static final String ERROR_EXTENSION = ".err";
    private static final String BAD_EXTENSION = ".bad";

    public static Path getErrorFile(final Path file) {
        final String fileName = file.getFileName().toString();
        if (fileName.endsWith(BAD_EXTENSION)) {
            return file.getParent()
                    .resolve(fileName.substring(0, fileName.length() - ZIP_EXTENSION.length() - BAD_EXTENSION.length())
                            + ERROR_EXTENSION + BAD_EXTENSION);
        } else {
            return file.getParent()
                    .resolve(fileName.substring(0, fileName.length() - ZIP_EXTENSION.length()) + ERROR_EXTENSION);
        }
    }

    @SuppressWarnings(value = "DM_DEFAULT_ENCODING")
    public static void addErrorMessage(final Path file, final String msg, final boolean bad) {
        LOGGER.debug(() -> msg + " (" + FileUtil.getCanonicalPath(file) + ")");
        try {
            Path errorFile = getErrorFile(file);
            if (!Files.isRegularFile(file)) {
                return;
            }

            if (bad) {
                final Path renamedFile = file.getParent().resolve(file.getFileName().toString() + BAD_EXTENSION);
                try {
                    Files.move(file, renamedFile);
                } catch (final Exception e) {
                    LOGGER.warn(() -> "Failed to rename zip file to " + renamedFile);
                }
                if (Files.isRegularFile(errorFile)) {
                    final Path renamedErrorFile = errorFile.getParent()
                            .resolve(errorFile.getFileName().toString() + BAD_EXTENSION);
                    Files.move(errorFile, renamedErrorFile);
                    errorFile = renamedErrorFile;
                }
            }

            try (final OutputStream os = Files.newOutputStream(errorFile)) {
                os.write(msg.getBytes(CharsetConstants.DEFAULT_CHARSET));
            }

        } catch (final IOException ex) {
            LOGGER.warn(() -> "Failed to write to file " + file + " message " + msg);
        }
    }

    public static void deleteFileAndErrors(final Path zipFile) {
        try {
            // Delete the file.
            final Path errorFile = getErrorFile(zipFile);
            LOGGER.debug(() -> "Deleting: " + FileUtil.getCanonicalPath(zipFile));
            Files.delete(zipFile);
            if (Files.isRegularFile(errorFile)) {
                LOGGER.debug(() -> "Deleting: " + FileUtil.getCanonicalPath(errorFile));
                Files.delete(errorFile);
            }
        } catch (final IOException ioEx) {
            LOGGER.error(() -> "delete() - Unable to delete zip file " + zipFile, ioEx);
        }
    }
}
