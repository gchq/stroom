package stroom.meta.impl;

import com.google.inject.AbstractModule;
import stroom.util.guice.GuiceUtil;
import stroom.util.RestResource;

public class StreamAttributeMapResourceModule extends AbstractModule {
    @Override
    protected void configure() {
        super.configure();

        GuiceUtil.buildMultiBinder(binder(), RestResource.class)
                .addBinding(StreamAttributeMapResource.class);
    }
}
