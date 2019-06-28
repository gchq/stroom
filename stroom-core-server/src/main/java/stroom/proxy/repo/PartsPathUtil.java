package stroom.proxy.repo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

class PartsPathUtil {
    private final static Logger LOGGER = LoggerFactory.getLogger(PartsPathUtil.class);

    private static final String PART = "__part";
    private static final String PARTS = "__parts";

    static boolean isPartsDir(final Path dir) {
        final String fileName = dir.getFileName().toString();
        return fileName.endsWith(PARTS);
    }

    static Path createPartsDir(final Path file) {
        final String fileName = file.getFileName().toString();
        final int index = fileName.lastIndexOf(".");
        if (index != -1) {
            final String stem = fileName.substring(0, index);
            return file.getParent().resolve(stem + PartsPathUtil.PARTS);
        }
        return null;
    }

    static Path createParentPartsZipFile(final Path dir) {
        final String fileName = dir.getFileName().toString();
        final String stem = fileName.substring(0, fileName.length() - PARTS.length());
        final String originalZip = stem + StroomZipRepository.ZIP_EXTENSION;
        return dir.getParent().resolve(originalZip);
    }

    static Path createPart(final Path dir, final Path file, final String idString) {
        final String fileName = file.getFileName().toString();
        final int index = fileName.lastIndexOf(".");
        if (index != -1) {
            final String stem = fileName.substring(0, index);
            return dir.resolve(stem + PartsPathUtil.PART + idString + StroomZipRepository.ZIP_EXTENSION);
        }
        return null;
    }

    static boolean isPart(final Path file) {
        final String fileName = file.getFileName().toString();
        return fileName.endsWith(StroomZipRepository.ZIP_EXTENSION) && fileName.contains(PART);
    }

    static void checkPath(final String filename) throws IOException {
        checkPath(filename, PART);
        checkPath(filename, PARTS);
    }

    static void checkPath(final String filename, final String forbidden) throws IOException {
        if (filename.contains(forbidden)) {
            final String message = "Attempt to create a forbidden path that includes '" + forbidden + "'";
            LOGGER.error(message);
            throw new IOException(message);
        }
    }
}
