package stroom.meta.impl.db;

import com.google.inject.AbstractModule;
import stroom.util.GuiceUtil;
import stroom.util.RestResource;

public class StreamAttributeMapResourceModule extends AbstractModule {
    @Override
    protected void configure() {
        super.configure();

        GuiceUtil.buildMultiBinder(binder(), RestResource.class)
                .addBinding(StreamAttributeMapResource.class);
    }
}
