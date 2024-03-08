package stroom.explorer.impl;

import stroom.docrefinfo.api.DocRefDecorator;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.util.entityevent.EntityEvent;
import stroom.util.guice.GuiceUtil;

import com.google.inject.AbstractModule;

public class DocRefInfoModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(DocRefInfoService.class).to(DocRefInfoServiceImpl.class);
        bind(DocRefDecorator.class).to(DocRefInfoServiceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), EntityEvent.Handler.class)
                .addBinding(DocRefInfoCache.class);
    }
}
