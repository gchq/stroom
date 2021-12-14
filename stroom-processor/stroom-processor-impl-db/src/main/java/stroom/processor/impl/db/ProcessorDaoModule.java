package stroom.processor.impl.db;

import stroom.processor.impl.ProcessorDao;
import stroom.processor.impl.ProcessorFilterDao;
import stroom.processor.impl.ProcessorFilterTrackerDao;
import stroom.processor.impl.ProcessorTaskDao;
import stroom.processor.impl.ProcessorTaskDeleteExecutor;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

public class ProcessorDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(ProcessorDao.class).to(ProcessorDaoImpl.class);
        bind(ProcessorFilterDao.class).to(ProcessorFilterDaoImpl.class);
        bind(ProcessorTaskDao.class).to(ProcessorTaskDaoImpl.class);
        bind(ProcessorTaskDeleteExecutor.class).to(ProcessorTaskDeleteExecutorImpl.class);
        bind(ProcessorFilterTrackerDao.class).to(ProcessorFilterTrackerDaoImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(ProcessorNodeCache.class)
                .addBinding(ProcessorFeedCache.class);
    }
}
