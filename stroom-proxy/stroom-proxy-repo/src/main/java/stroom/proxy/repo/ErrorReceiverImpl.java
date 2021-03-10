package stroom.proxy.repo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ErrorReceiverImpl implements ErrorReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorReceiverImpl.class);

    private static final String ZIP_EXTENSION = ".zip";
    private static final String ERROR_EXTENSION = ".err";
    private static final String BAD_EXTENSION = ".bad";

    @Override
    public void error(final Path zipFile, final String message) {
        try {
            if (!Files.isRegularFile(zipFile)) {
                return;
            }
            Path errorFile = getErrorFile(zipFile);

            Files.writeString(
                    errorFile,
                    message,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);

        } catch (final IOException ex) {
            LOGGER.warn("Failed to write to file " + zipFile + " message " + message);
        }
    }

    @Override
    public void fatal(final Path zipFile, final String message) {
        // Write to error file first.
        error(zipFile, message);

        // Now move the source zip so we know it was bad.
        final Path renamedFile = zipFile.getParent().resolve(zipFile.getFileName().toString() + BAD_EXTENSION);
        try {
            Files.move(zipFile, renamedFile);
        } catch (final Exception e) {
            LOGGER.warn("Failed to rename zip file to " + renamedFile);
        }
    }

    public static Path getErrorFile(final Path zipFile) {
        final String fileName = zipFile.getFileName().toString();
        return zipFile.getParent()
                .resolve(fileName.substring(0, fileName.length() - ZIP_EXTENSION.length()) + ERROR_EXTENSION);
    }

    public static void deleteFileAndErrors(final Path zipFile) {
        try {
            // Delete the file.
            final Path errorFile = getErrorFile(zipFile);
            Files.delete(zipFile);
            if (Files.isRegularFile(errorFile)) {
                Files.delete(errorFile);
            }
        } catch (final IOException ioEx) {
            LOGGER.error("delete() - Unable to delete zip file " + zipFile, ioEx);
        }
    }
}
