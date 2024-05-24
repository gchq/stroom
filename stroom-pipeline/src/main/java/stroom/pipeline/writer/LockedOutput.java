package stroom.pipeline.writer;

import stroom.util.io.FileUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

public class LockedOutput extends OutputProxy {

    private static final String UNABLE_TO_RENAME_FILE = "Unable to rename file \"";
    private static final String TO = "\" to \"";
    private static final String QUOTE = "\"";

    final Path lockFile;
    final Path outFile;
    final Set<PosixFilePermission> filePermissions;

    public LockedOutput(final Output innerOutput,
                        final Path lockFile,
                        final Path outFile,
                        final Set<PosixFilePermission> filePermissions) {
        super(innerOutput);
        this.lockFile = lockFile;
        this.outFile = outFile;
        this.filePermissions = filePermissions;
    }

    @Override
    public void close() throws IOException {
        super.close();

        try {
            Files.move(lockFile, outFile);

            // If specified, set file system permissions on the finished file
            if (filePermissions != null) {
                Files.setPosixFilePermissions(outFile, filePermissions);
            }
        } catch (final IOException e) {
            final String message = UNABLE_TO_RENAME_FILE +
                    FileUtil.getCanonicalPath(lockFile) +
                    TO +
                    FileUtil.getCanonicalPath(outFile) +
                    QUOTE;
            throw new IOException(message);
        }
    }
}
