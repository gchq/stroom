package stroom.meta.impl;

import com.google.inject.AbstractModule;
import stroom.event.logging.api.ObjectInfoProviderBinder;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaSecurityFilter;
import stroom.meta.shared.MetaService;
import stroom.searchable.api.Searchable;
import stroom.util.guice.GuiceUtil;

public class MetaModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(MetaService.class).to(MetaServiceImpl.class);
        bind(MetaSecurityFilter.class).to(MetaSecurityFilterImpl.class);

        // Provide object info to the logging service.
        ObjectInfoProviderBinder.create(binder())
                .bind(Meta.class, MetaObjectInfoProvider.class);

        GuiceUtil.buildMultiBinder(binder(), Searchable.class)
                .addBinding(MetaServiceImpl.class);
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
