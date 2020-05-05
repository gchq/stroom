package stroom.meta.impl;

import stroom.event.logging.api.ObjectInfoProviderBinder;
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
                .bindResource(MetaResourceImpl.class);
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
}
