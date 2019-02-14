package stroom.streamstore;

import com.google.inject.AbstractModule;
import stroom.util.GuiceUtil;
import stroom.util.RestResource;

public class StreamAttributeMapResourceModule extends AbstractModule {

    //FAO @66 TODO This module was created as a temp solution until StreamAttributeMapResource
    // is moved to stroom-meta

    @Override
    protected void configure() {
        super.configure();

        GuiceUtil.buildMultiBinder(binder(), RestResource.class)
                .addBinding(StreamAttributeMapResource.class);
    }
}
