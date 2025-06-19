package stroom.test.common.util.test;

import stroom.util.io.HomeDirProvider;
import stroom.util.io.TempDirProvider;

import com.google.inject.AbstractModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestingHomeAndTempProvidersModule extends AbstractModule {

    private final Path tempDir;
    private final Path homeDir;

    public TestingHomeAndTempProvidersModule() {
        try {
            this.tempDir = Files.createTempDirectory("stroom-temp");
            this.homeDir = tempDir.resolve("home");
        } catch (final IOException e) {
            throw new RuntimeException("Error creating temp dir", e);
        }
    }

    public TestingHomeAndTempProvidersModule(final Path tempDir) {
        this.tempDir = tempDir;
        this.homeDir = tempDir.resolve("home");
    }

    @Override
    protected void configure() {
        super.configure();

        bind(HomeDirProvider.class).toInstance(this::getHomeDir);
        bind(TempDirProvider.class).toInstance(this::getTempDir);
    }

    public Path getHomeDir() {
        return homeDir;
    }

    public Path getTempDir() {
        return tempDir;
    }
}
