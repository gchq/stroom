package stroom.dashboard.impl.db;

import stroom.test.common.util.db.DbTestModule;

import com.google.inject.AbstractModule;

public class TestModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();
        install(new VisualisationAssetDaoModule());
        install(new VisualisationAssetDbModule());
        install(new DbTestModule());
    }

}
