package stroom.storedquery.impl;

import com.google.inject.AbstractModule;
import stroom.storedquery.api.StoredQueryService;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.RestResource;

public class StoredQueryModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(StoredQueryService.class).to(StoredQueryServiceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), RestResource.class)
                .addBinding(StoredQueryResourceImpl.class);
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
