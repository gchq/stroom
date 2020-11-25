package stroom.util.io;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

@Singleton
public class TempDirProviderImpl implements TempDirProvider {
    private final PathConfig pathConfig;
    private final HomeDirProvider homeDirProvider;

    private Path tempDir;

    @Inject
    public TempDirProviderImpl(final PathConfig pathConfig,
                               final HomeDirProvider homeDirProvider) {
        this.pathConfig = pathConfig;
        this.homeDirProvider = homeDirProvider;
    }

    @Override
    public Path get() {
        if (tempDir == null) {
            Path path = null;

            String dir = pathConfig.getTemp();
            if (dir != null) {
                dir = FileUtil.replaceHome(dir);
                path = Paths.get(dir);
            }

            if (path == null) {
                throw new NullPointerException("Temp dir is null");
            }

            // If this isn't an absolute path then make it so relative to the home path.
            if (!path.startsWith("/") && !path.startsWith("\\")) {
                path = homeDirProvider.get().resolve(path);
            }

            path = path.toAbsolutePath();

            tempDir = path;
        }
        return tempDir;
    }

    public void setTempDir(final Path tempDir) {
        this.tempDir = tempDir;
    }
}
