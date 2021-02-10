package stroom.job.impl.db;

import stroom.util.db.ForceCoreMigration;

import com.google.inject.AbstractModule;

public class TestModule extends AbstractModule {
    @Override
    protected void configure() {
        super.configure();

        // Not using all the DB modules so just bind to an empty anonymous class
        bind(ForceCoreMigration.class).toInstance(new ForceCoreMigration() {});
    }


}
