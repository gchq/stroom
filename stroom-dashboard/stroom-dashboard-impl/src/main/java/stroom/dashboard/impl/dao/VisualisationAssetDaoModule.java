package stroom.dashboard.impl.dao;

import stroom.dashboard.impl.visualisation.VisualisationAssetDao;

import com.google.inject.AbstractModule;

/**
 * Guice injection module.
 */
public class VisualisationAssetDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(VisualisationAssetDao.class).to(VisualisationAssetDaoImpl.class);
    }
}
