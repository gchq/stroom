package stroom.meta.impl.db;

import stroom.cache.api.CacheManager;
import stroom.lifecycle.api.LifecycleBinder;
import stroom.meta.impl.MetaDao;
import stroom.meta.impl.MetaFeedDao;
import stroom.meta.impl.MetaKeyDao;
import stroom.meta.impl.MetaProcessorDao;
import stroom.meta.impl.MetaRetentionTrackerDao;
import stroom.meta.impl.MetaTypeDao;
import stroom.meta.impl.MetaValueDao;
import stroom.util.RunnableWrapper;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

import javax.inject.Inject;

public class MetaDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        requireBinding(CacheManager.class);

        bind(MetaFeedDao.class).to(MetaFeedDaoImpl.class);
        bind(MetaTypeDao.class).to(MetaTypeDaoImpl.class);
        bind(MetaProcessorDao.class).to(MetaProcessorDaoImpl.class);
        bind(MetaKeyDao.class).to(MetaKeyDaoImpl.class);
        bind(MetaValueDao.class).to(MetaValueDaoImpl.class);
        bind(MetaDao.class).to(MetaDaoImpl.class);
        bind(MetaRetentionTrackerDao.class).to(MetaRetentionTrackerDaoImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(MetaValueDaoImpl.class)
                .addBinding(MetaKeyDaoImpl.class)
                .addBinding(MetaDaoImpl.class)
                .addBinding(MetaProcessorDaoImpl.class)
                .addBinding(MetaTypeDaoImpl.class)
                .addBinding(MetaFeedDaoImpl.class);

        LifecycleBinder.create(binder())
                .bindShutdownTaskTo(MetaValueServiceFlush.class);


    }

    private static class MetaValueServiceFlush extends RunnableWrapper {

        @Inject
        MetaValueServiceFlush(final MetaValueDaoImpl metaValueService) {
            super(metaValueService::flush);
        }
    }
}
