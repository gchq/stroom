package stroom.proxy.repo;

import stroom.bytebuffer.ByteBufferModule;
import stroom.db.util.DbModule;
import stroom.proxy.repo.dao.db.ProxyDbModule;
import stroom.proxy.repo.dao.lmdb.ProxyLmdbModule;
import stroom.test.common.util.test.TestingHomeAndTempProvidersModule;

import com.google.inject.AbstractModule;

import java.nio.file.Path;

public class ProxyRepoTestModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new ByteBufferModule());
        install(new ProxyDbModule());
        install(new ProxyLmdbModule());
        install(new DbModule());

        final TestingHomeAndTempProvidersModule homeAndTempProvidersModule = new TestingHomeAndTempProvidersModule();
        install(homeAndTempProvidersModule);

        final Path homeDir = homeAndTempProvidersModule.getHomeDir();
        final Path repoDir = homeDir.resolve("repo");
        final Path dbDir = homeDir.resolve("db");

        bind(RepoDirProvider.class).toInstance(() -> repoDir);
        bind(RepoDbDirProvider.class).toInstance(() -> dbDir);
        bind(ErrorReceiver.class).to(ErrorReceiverImpl.class);

        bind(ForwarderDestinations.class).to(MockForwardDestinations.class);
        bind(FailureDestinations.class).to(MockFailureDestinations.class);
        bind(Sender.class).to(MockSender.class);
        bind(ProgressLog.class).to(ProgressLogImpl.class);
    }
}
