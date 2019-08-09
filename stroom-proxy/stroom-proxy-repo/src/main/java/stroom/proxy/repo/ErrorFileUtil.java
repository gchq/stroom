package stroom.proxy.repo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.zip.CharsetConstants;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ErrorFileUtil {
    private final static Logger LOGGER = LoggerFactory.getLogger(ErrorFileUtil.class);

    private final static String ZIP_EXTENSION = ".zip";
    private final static String ERROR_EXTENSION = ".err";
    private final static String BAD_EXTENSION = ".bad";

    public static Path getErrorFile(final Path file) {
        final String fileName = file.getFileName().toString();
        if (fileName.endsWith(BAD_EXTENSION)) {
            return file.getParent().resolve(fileName.substring(0, fileName.length() - ZIP_EXTENSION.length() - BAD_EXTENSION.length())
                    + ERROR_EXTENSION + BAD_EXTENSION);
        } else {
            return file.getParent().resolve(fileName.substring(0, fileName.length() - ZIP_EXTENSION.length()) + ERROR_EXTENSION);
        }
    }

    @SuppressWarnings(value = "DM_DEFAULT_ENCODING")
    public static void addErrorMessage(final Path file, final String msg, final boolean bad) {
        try {
            Path errorFile = getErrorFile(file);
            if (!Files.isRegularFile(file)) {
                return;
            }

            if (bad) {
                final Path renamedFile = file.getParent().resolve(file.getFileName().toString() + BAD_EXTENSION);
                try {
                    Files.move(file, renamedFile);
//                    zipFile.renameTo(renamedFile);
                } catch (final Exception e) {
                    LOGGER.warn("Failed to rename zip file to " + renamedFile);
                }
                if (Files.isRegularFile(errorFile)) {
                    final Path renamedErrorFile = errorFile.getParent().resolve(errorFile.getFileName().toString() + BAD_EXTENSION);
                    Files.move(errorFile, renamedErrorFile);
                    errorFile = renamedErrorFile;
                }
            }

            try (final OutputStream os = Files.newOutputStream(errorFile)) {
                os.write(msg.getBytes(CharsetConstants.DEFAULT_CHARSET));
            }

        } catch (final IOException ex) {
            LOGGER.warn("Failed to write to file " + file + " message " + msg);
        }
    }

    public static void deleteFileAndErrors(final Path zipFile) {
        try {
            // Delete the file.
            final Path errorfile = getErrorFile(zipFile);
            Files.delete(zipFile);
            if (Files.isRegularFile(errorfile)) {
                Files.delete(errorfile);
            }
        } catch (final IOException ioEx) {
            LOGGER.error("delete() - Unable to delete zip file " + zipFile, ioEx);
        }
    }
}
