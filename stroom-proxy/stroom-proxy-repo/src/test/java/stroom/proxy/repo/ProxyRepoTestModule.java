package stroom.proxy.repo;

import stroom.db.util.DbModule;
import stroom.util.io.FileUtil;

import com.google.inject.AbstractModule;

import java.nio.file.Path;

public class ProxyRepoTestModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new ProxyRepoDbModule());
        install(new DbModule());

        final Path repoDir = FileUtil.createTempDirectory("stroom-proxy-repo");
        final Path dbDir = FileUtil.createTempDirectory("stroom-proxy-db");
        bind(RepoDirProvider.class).toInstance(() -> repoDir);
        bind(RepoDbDirProvider.class).toInstance(() -> dbDir);
        bind(ErrorReceiver.class).to(ErrorReceiverImpl.class);

        bind(ForwarderDestinations.class).to(MockForwardDestinations.class);
        bind(Sender.class).to(MockSender.class);
        bind(ProgressLog.class).to(ProgressLogImpl.class);
        bind(RepoDbConfig.class).to(ProxyRepoDbConfig.class);
    }
}
