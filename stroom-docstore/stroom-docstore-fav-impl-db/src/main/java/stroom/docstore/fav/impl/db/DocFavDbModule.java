package stroom.docstore.fav.impl.db;

import stroom.docstore.fav.impl.DocFavDao;

import com.google.inject.AbstractModule;

public class DocFavDbModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(DocFavDao.class).to(DocFavDaoImpl.class);
    }
}
