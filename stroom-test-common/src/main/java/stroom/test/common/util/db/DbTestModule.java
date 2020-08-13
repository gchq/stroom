package stroom.test.common.util.db;

import stroom.db.util.DataSourceFactory;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

public class DbTestModule extends AbstractModule {
    @Override
    protected void configure() {
        super.configure();
        bind(DataSourceFactory.class).to(TestDataSourceFactory.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(TestDataSourceFactory.class);
    }
}
