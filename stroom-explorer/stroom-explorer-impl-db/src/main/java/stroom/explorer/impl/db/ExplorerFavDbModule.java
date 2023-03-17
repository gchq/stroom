package stroom.explorer.impl.db;

import stroom.explorer.impl.ExplorerFavDao;

import com.google.inject.AbstractModule;

public class ExplorerFavDbModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(ExplorerFavDao.class).to(ExplorerFavDaoImpl.class);
    }
}
