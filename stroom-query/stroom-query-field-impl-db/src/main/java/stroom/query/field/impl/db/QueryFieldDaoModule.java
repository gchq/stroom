package stroom.query.field.impl.db;

import stroom.query.field.impl.QueryFieldDao;

import com.google.inject.AbstractModule;

public class QueryFieldDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(QueryFieldDao.class).to(QueryFieldDaoImpl.class);
    }
}
