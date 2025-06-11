package stroom.config.global.impl.db;

import stroom.test.common.util.db.DbTestModule;

import com.google.inject.AbstractModule;

public class GlobalConfigTestModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new GlobalConfigDaoModule());
        install(new GlobalConfigDbModule());
        install(new DbTestModule());
    }
}
