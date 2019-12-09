package stroom.db.util;

import com.google.inject.AbstractModule;

public class DbModule extends AbstractModule {
    @Override
    protected void configure() {
        super.configure();
        bind(HikariConfigHolder.class).to(HikariConfigHolderImpl.class);
    }
}
