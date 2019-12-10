package stroom.test.common.util.db;

import com.google.inject.AbstractModule;
import stroom.db.util.HikariConfigFactory;

public class TestDbModule extends AbstractModule {
    @Override
    protected void configure() {
        super.configure();
        bind(HikariConfigFactory.class).to(EmbeddedDbHikariConfigFactory.class);
    }
}
