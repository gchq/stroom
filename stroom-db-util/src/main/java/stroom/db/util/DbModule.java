package stroom.db.util;

import com.google.inject.AbstractModule;

public class DbModule extends AbstractModule {
    @Override
    protected void configure() {
        super.configure();
        bind(HikariConfigFactory.class).to(HikariConfigFactoryImpl.class);
    }
}
