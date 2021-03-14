package stroom.meta.impl.db;

import stroom.meta.api.MetaSecurityFilter;
import stroom.util.db.ForceLegacyMigration;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import java.util.Optional;

public class MetaTestModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        // Not using all the DB modules so just bind to an empty anonymous class
        bind(ForceLegacyMigration.class).toInstance(new ForceLegacyMigration() {
        });
    }

    @Provides
    MetaSecurityFilter getMetaSecurityFilter() {
        return (permission, fields) -> Optional.empty();
    }
}
