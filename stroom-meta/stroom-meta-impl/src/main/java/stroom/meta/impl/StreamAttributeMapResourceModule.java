package stroom.meta.impl;

import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;

public class StreamAttributeMapResourceModule extends AbstractModule {
    @Override
    protected void configure() {
        super.configure();

        RestResourcesBinder.create(binder())
                .bindResource(StreamAttributeMapResource.class);
    }
}
