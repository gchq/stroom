package stroom.meta.impl;

import stroom.event.logging.api.ObjectInfoProviderBinder;
import stroom.util.RunnableWrapper;
import stroom.job.api.ScheduledJobsBinder;
import stroom.meta.api.AttributeMapFactory;
import stroom.meta.api.MetaSecurityFilter;
import stroom.meta.api.MetaService;
import stroom.meta.api.PhysicalDelete;
import stroom.meta.shared.Meta;
import stroom.searchable.api.Searchable;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.OptionalBinder;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

public class MetaModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(MetaService.class).to(MetaServiceImpl.class);
        bind(PhysicalDelete.class).to(PhysicalDeleteImpl.class);

        OptionalBinder.newOptionalBinder(binder(), MetaSecurityFilter.class);
        OptionalBinder.newOptionalBinder(binder(), AttributeMapFactory.class);

        // Provide object info to the logging service.
        ObjectInfoProviderBinder.create(binder())
                .bind(Meta.class, MetaObjectInfoProvider.class);

        GuiceUtil.buildMultiBinder(binder(), Searchable.class)
                .addBinding(MetaServiceImpl.class);

        RestResourcesBinder.create(binder())
                .bind(MetaResourceImpl.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(FlushDataMetaDb.class, builder -> builder
                        .withName("Flush DataMetaDb")
                        .withManagedState(false)
                        .withSchedule(PERIODIC, "10s"))
                .bindJobTo(DataAttributesRetention.class, builder -> builder
                        .withName("Data Attributes Retention")
                        .withDescription("Delete attributes older than system property stroom.meta.deleteAge")
                        .withSchedule(PERIODIC, "1d"));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return true;
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
