package stroom.test.common.util.db;

import com.google.inject.AbstractModule;
import stroom.db.util.HikariConfigHolder;

public class TestDbModule extends AbstractModule {
    @Override
    protected void configure() {
        super.configure();
        bind(HikariConfigHolder.class).to(EmbeddedDbHikariConfigHolder.class);
    }
}
