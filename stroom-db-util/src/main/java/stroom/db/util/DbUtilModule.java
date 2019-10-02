package stroom.db.util;

import com.google.inject.AbstractModule;

public class DbUtilModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(HikariConfigHolder.class).asEagerSingleton();
    }
}
