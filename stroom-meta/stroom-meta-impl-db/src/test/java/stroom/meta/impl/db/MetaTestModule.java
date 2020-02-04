package stroom.meta.impl.db;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import stroom.meta.shared.MetaSecurityFilter;
import stroom.util.db.ForceCoreMigration;

import java.util.Optional;

public class MetaTestModule extends AbstractModule {
    @Override
    protected void configure() {
        super.configure();

        // Not using all the DB modules so just bind to an empty anonymous class
        bind(ForceCoreMigration.class).toInstance(new ForceCoreMigration() {});
    }

    @Provides
    MetaSecurityFilter getMetaSecurityFilter() {
        return permission -> Optional.empty();
    }
}
