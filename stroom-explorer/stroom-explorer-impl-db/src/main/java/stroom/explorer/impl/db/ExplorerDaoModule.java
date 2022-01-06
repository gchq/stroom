package stroom.explorer.impl.db;

import stroom.explorer.impl.ExplorerTreeDao;

import com.google.inject.AbstractModule;

public class ExplorerDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(ExplorerTreeDao.class).to(ExplorerTreeDaoImpl.class);
    }
}
