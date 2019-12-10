package stroom.test.common.util.db;

import com.google.inject.AbstractModule;
import stroom.db.util.DataSourceFactory;

public class TestDbModule extends AbstractModule {
    @Override
    protected void configure() {
        super.configure();
        bind(DataSourceFactory.class).to(EmbeddedDbDataSourceFactory.class);
    }
}
