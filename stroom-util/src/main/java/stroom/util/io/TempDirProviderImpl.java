package stroom.util.io;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.file.Path;
import java.nio.file.Paths;

@Singleton
public class TempDirProviderImpl implements TempDirProvider {
    private final PathConfig pathConfig;

    private Path tempDir;

    @Inject
    public TempDirProviderImpl(final PathConfig pathConfig) {
        this.pathConfig = pathConfig;
    }

    @Override
    public Path get() {
        if (tempDir == null) {
            if (pathConfig.getTemp() != null) {
                tempDir = Paths.get(pathConfig.getTemp());
            }

            if (tempDir == null) {
                throw new NullPointerException("Temp dir is null");
            }
        }
        return tempDir;
    }

    public void setTempDir(final Path tempDir) {
        this.tempDir = tempDir;
    }
}
