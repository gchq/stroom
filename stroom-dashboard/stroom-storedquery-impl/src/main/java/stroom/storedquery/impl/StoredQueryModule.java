package stroom.storedquery.impl;

import stroom.storedquery.api.StoredQueryService;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;

public class StoredQueryModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(StoredQueryService.class).to(StoredQueryServiceImpl.class);

        RestResourcesBinder.create(binder())
                .bindResource(StoredQueryResourceImpl.class);
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
