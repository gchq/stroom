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
        final Path renamedFile = zipFile
                .getParent()
                .resolve(ProxyRepoFileNames.getBad(zipFile.getFileName().toString()));
        try {
            Files.move(zipFile, renamedFile);
        } catch (final Exception e) {
            LOGGER.warn("Failed to rename zip file to " + renamedFile);
        }
    }

    public static Path getErrorFile(final Path zipFile) {
        String fileName = zipFile.getFileName().toString();
        fileName = ProxyRepoFileNames.getError(fileName);
        return zipFile.getParent().resolve(fileName);
    }

    public static void deleteFileAndErrors(final Path zipFile) {
        try {
            // Delete the file.
            final Path dir = zipFile.getParent();
            final String zipFileName = zipFile.getFileName().toString();
            final String metaFileName = ProxyRepoFileNames.getMeta(zipFileName);
            final String errorFileName = ProxyRepoFileNames.getError(zipFileName);
            final String badFileName = ProxyRepoFileNames.getBad(zipFileName);
            Files.deleteIfExists(dir.resolve(zipFileName));
            Files.deleteIfExists(dir.resolve(metaFileName));
            Files.deleteIfExists(dir.resolve(errorFileName));
            Files.deleteIfExists(dir.resolve(badFileName));
        } catch (final IOException ioEx) {
            LOGGER.error("delete() - Unable to delete zip file " + zipFile, ioEx);
        }
    }
}
