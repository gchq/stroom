package stroom.proxy.repo;

import stroom.db.util.DbModule;
import stroom.util.db.ForceCoreMigration;
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
        bind(ErrorReceiver.class).toInstance((path, message) -> {
            throw new RuntimeException(path + " " + message);
        });

        // Not using all the DB modules so just bind to an empty anonymous class
        bind(ForceCoreMigration.class).toInstance(new ForceCoreMigration() {
        });

        bind(ForwarderDestinations.class).to(MockForwardDestinations.class);
    }
}
