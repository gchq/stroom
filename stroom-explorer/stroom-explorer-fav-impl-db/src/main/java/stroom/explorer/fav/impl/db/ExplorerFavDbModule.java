package stroom.explorer.fav.impl.db;

import stroom.explorer.fav.impl.ExplorerFavDao;

import com.google.inject.AbstractModule;

public class ExplorerFavDbModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(ExplorerFavDao.class).to(ExplorerFavDaoImpl.class);
    }
}
