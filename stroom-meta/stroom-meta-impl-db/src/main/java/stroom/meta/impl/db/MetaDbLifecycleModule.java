package stroom.meta.impl.db;

import stroom.lifecycle.api.LifecycleBinder;
import stroom.util.RunnableWrapper;

import com.google.inject.AbstractModule;

import javax.inject.Inject;

public class MetaDbLifecycleModule extends AbstractModule {
    @Override
    protected void configure() {

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
