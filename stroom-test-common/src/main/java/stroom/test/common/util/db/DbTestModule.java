package stroom.test.common.util.db;

import com.google.inject.AbstractModule;
import stroom.db.util.DataSourceFactory;

public class DbTestModule extends AbstractModule {
    @Override
    protected void configure() {
        super.configure();
        bind(DataSourceFactory.class).to(EmbeddedDbDataSourceFactory.class);
    }
}
