package stroom.proxy.repo;

import stroom.db.util.DbModule;
import stroom.util.db.ForceCoreMigration;

import com.google.inject.AbstractModule;

public class ProxyRepoTestModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new ProxyRepoDbModule());
        install(new DbModule());

        // Not using all the DB modules so just bind to an empty anonymous class
        bind(ForceCoreMigration.class).toInstance(new ForceCoreMigration() {
        });
    }
}
