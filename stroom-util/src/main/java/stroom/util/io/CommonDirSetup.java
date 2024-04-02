package stroom.util.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CommonDirSetup {

    public static void setup() {
        try {
            final Path tempDir = Files.createTempDirectory("temp");
            System.setProperty(TempDirProviderImpl.PROP_STROOM_TEMP, FileUtil.getCanonicalPath(tempDir));
            final Path homeDir = Files.createTempDirectory("home");
            System.setProperty(HomeDirProviderImpl.PROP_STROOM_HOME, FileUtil.getCanonicalPath(homeDir));

        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
