package stroom.proxy.repo;

import stroom.util.io.FileUtil;

import com.google.inject.AbstractModule;

import java.nio.file.Path;

public class ProxyRepoTestModule extends AbstractModule {

    @Override
    protected void configure() {
        final Path repoDir = FileUtil.createTempDirectory("stroom-proxy-repo");
        bind(RepoDirProvider.class).toInstance(() -> repoDir);
        bind(ProgressLog.class).to(ProgressLogImpl.class);
    }
}
