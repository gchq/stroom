package stroom.test.common.util.db;

import stroom.db.util.DataSourceFactory;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

/**
 * Will create a new database for each run, needed for migration testing
 */
public class UniqueDbTestModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();
        bind(DataSourceFactory.class).to(UniqueTestDataSourceFactory.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(UniqueTestDataSourceFactory.class);
    }
}
