package stroom.data.store.impl.fs;

import stroom.util.db.ForceLegacyMigration;

import com.google.inject.AbstractModule;

public class FsTestModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        // Not using all the DB modules so just bind to an empty anonymous class
        bind(ForceLegacyMigration.class).toInstance(new ForceLegacyMigration() {
        });
    }
}
