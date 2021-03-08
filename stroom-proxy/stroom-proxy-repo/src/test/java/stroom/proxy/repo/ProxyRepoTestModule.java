package stroom.proxy.repo;

import stroom.db.util.DbModule;
import stroom.util.db.ForceCoreMigration;

import com.google.inject.AbstractModule;

public class ProxyRepoTestModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new ProxyRepoDbModule());
        install(new DbModule());

        bind(RepoConfig.class).to(MockProxyRepoConfig.class);
        bind(ProxyRepoConfig.class).to(MockProxyRepoConfig.class);

        // Not using all the DB modules so just bind to an empty anonymous class
        bind(ForceCoreMigration.class).toInstance(new ForceCoreMigration() {
        });

        bind(ForwarderDestinations.class).to(MockForwardDestinations.class);
    }
}
