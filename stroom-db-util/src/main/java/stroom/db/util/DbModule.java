package stroom.db.util;

import com.google.inject.AbstractModule;

public class DbModule extends AbstractModule {
    @Override
    protected void configure() {
        super.configure();
        bind(DataSourceFactory.class).to(DataSourceFactoryImpl.class);
    }
}
