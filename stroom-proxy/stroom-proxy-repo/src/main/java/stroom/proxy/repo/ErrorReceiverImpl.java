package stroom.proxy.repo;

import stroom.proxy.repo.store.FileSet;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

public class ErrorReceiverImpl implements ErrorReceiver {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ErrorReceiverImpl.class);

    @Override
    public void error(final FileSet fileSet, final String message) {
        LOGGER.error(() -> fileSet.getZipFileName() + ": " + message);

//        try {
//            if (!Files.isRegularFile(fileSet.getZip())) {
//                return;
//            }
//            final Path errorFile = fileSet.getError();
//            Files.writeString(
//                    errorFile,
//                    message,
//                    StandardCharsets.UTF_8,
//                    StandardOpenOption.CREATE,
//                    StandardOpenOption.APPEND);
//
//        } catch (final IOException ex) {
//            LOGGER.warn("Failed to write to file " + fileSet.getZip() + " message " + message);
//        }
    }

    @Override
    public void fatal(final FileSet fileSet, final String message) {
        // Write to error file first.
        error(fileSet, message);

//        // Now move the source zip so we know it was bad.
//        try {
//            Files.move(fileSet.getZip(), fileSet.getBadZip(), StandardCopyOption.ATOMIC_MOVE);
//        } catch (final Exception e) {
//            LOGGER.warn("Failed to rename zip file to " + fileSet.getBadZip());
//        }
    }

//    public static void deleteFileAndErrors(final Path zipFile) {
//        try {
//            // Delete the file.
//            final Path dir = zipFile.getParent();
//            final String zipFileName = zipFile.getFileName().toString();
//            final String metaFileName = ProxyRepoFileNames.getMeta(zipFileName);
//            final String errorFileName = ProxyRepoFileNames.getError(zipFileName);
//            final String badFileName = ProxyRepoFileNames.getBad(zipFileName);
//            Files.deleteIfExists(dir.resolve(zipFileName));
//            Files.deleteIfExists(dir.resolve(metaFileName));
//            Files.deleteIfExists(dir.resolve(errorFileName));
//            Files.deleteIfExists(dir.resolve(badFileName));
//        } catch (final IOException ioEx) {
//            LOGGER.error("delete() - Unable to delete zip file " + zipFile, ioEx);
//        }
//    }
}
