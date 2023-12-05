package stroom.proxy.repo.dao.lmdb;

import stroom.proxy.repo.dao.FeedDao;

import com.google.inject.AbstractModule;

public class ProxyLmdbModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();
        bind(FeedDao.class).to(FeedDaoLmdb.class);
    }
}
