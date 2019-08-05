package stroom.meta.impl;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.event.logging.api.ObjectInfoProviderBinder;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaSecurityFilter;
import stroom.meta.shared.MetaService;
import stroom.search.api.Searchable;

public class MetaModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(MetaService.class).to(MetaServiceImpl.class);
        bind(MetaSecurityFilter.class).to(MetaSecurityFilterImpl.class);

        // Provide object info to the logging service.
        ObjectInfoProviderBinder.create(binder())
                .bind(Meta.class, MetaObjectInfoProvider.class);

        final Multibinder<Searchable> multibinder = Multibinder.newSetBinder(binder(), Searchable.class);
        multibinder.addBinding().to(MetaServiceImpl.class);
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
