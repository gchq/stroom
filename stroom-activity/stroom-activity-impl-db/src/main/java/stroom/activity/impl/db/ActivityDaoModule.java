package stroom.activity.impl.db;

import stroom.activity.impl.ActivityDao;

import com.google.inject.AbstractModule;

public class ActivityDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(ActivityDao.class).to(ActivityDaoImpl.class);
    }
}
