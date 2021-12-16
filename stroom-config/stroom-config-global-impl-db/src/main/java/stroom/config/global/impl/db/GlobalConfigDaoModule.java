package stroom.config.global.impl.db;

import stroom.config.global.impl.ConfigPropertyDao;

import com.google.inject.AbstractModule;

public class GlobalConfigDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(ConfigPropertyDao.class).to(ConfigPropertyDaoImpl.class);
    }
}
