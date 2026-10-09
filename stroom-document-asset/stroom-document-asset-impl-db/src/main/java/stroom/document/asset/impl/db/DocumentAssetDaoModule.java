package stroom.document.asset.impl.db;

import stroom.document.asset.impl.DocumentAssetDao;

import com.google.inject.AbstractModule;

/**
 * Guice injection module.
 */
public class DocumentAssetDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(DocumentAssetDao.class).to(DocumentAssetDaoImpl.class);
    }
}
