package stroom.storedquery.impl.db;

import stroom.storedquery.impl.StoredQueryDao;

import com.google.inject.AbstractModule;

public class StoredQueryDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(StoredQueryDao.class).to(StoredQueryDaoImpl.class);
    }
}
