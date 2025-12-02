package stroom.meta.impl;

import stroom.event.logging.api.ObjectInfoProviderBinder;
import stroom.job.api.ScheduledJobsBinder;
import stroom.meta.api.MetaSecurityFilter;
import stroom.meta.api.MetaService;
import stroom.meta.api.PhysicalDelete;
import stroom.meta.api.StreamFeedProvider;
import stroom.meta.shared.Meta;
import stroom.query.api.datasource.DataSourceProvider;
import stroom.searchable.api.Searchable;
import stroom.util.RunnableWrapper;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.OptionalBinder;
import jakarta.inject.Inject;

public class MetaModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(MetaService.class).to(MetaServiceImpl.class);
        bind(StreamFeedProvider.class).to(MetaServiceImpl.class);
        bind(PhysicalDelete.class).to(PhysicalDeleteImpl.class);

        OptionalBinder.newOptionalBinder(binder(), MetaSecurityFilter.class);

        // Provide object info to the logging service.
        ObjectInfoProviderBinder.create(binder())
                .bind(Meta.class, MetaObjectInfoProvider.class);

        GuiceUtil.buildMultiBinder(binder(), DataSourceProvider.class)
                .addBinding(MetaServiceImpl.class);
//        GuiceUtil.buildMultiBinder(binder(), Searchable.class)
//                .addBinding(MetaServiceImpl.class);
        GuiceUtil.buildMapBinder(binder(), Searchable.class)
                .addBinding(MetaServiceImpl.class);

        RestResourcesBinder.create(binder())
                .bind(MetaResourceImpl.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(FlushDataMetaDb.class, builder -> builder
                        .name("Flush Data Attributes To DB")
                        .description("Flush meta data attribute values to the database")
                        .managed(false)
                        .frequencySchedule("10s"))
                .bindJobTo(DataAttributesRetention.class, builder -> builder
                        .name("Attribute Value Data Retention")
                        .description("Delete data attribute values older than system property " +
                                     "stroom.data.meta.metaValue.deleteAge")
                        .frequencySchedule("1d"));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return 0;
    }

    private static class FlushDataMetaDb extends RunnableWrapper {

        @Inject
        FlushDataMetaDb(final MetaValueDao metaValueService) {
            super(metaValueService::flush);
        }
    }

    private static class DataAttributesRetention extends RunnableWrapper {

        @Inject
        DataAttributesRetention(final MetaValueDao metaValueService) {
            super(metaValueService::deleteOldValues);
        }
    }
}
