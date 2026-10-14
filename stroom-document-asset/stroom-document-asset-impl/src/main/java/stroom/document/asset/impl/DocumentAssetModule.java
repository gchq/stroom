package stroom.document.asset.impl;

import stroom.util.guice.RestResourcesBinder;
import stroom.util.guice.ServletBinder;

import com.google.inject.AbstractModule;

public class DocumentAssetModule extends AbstractModule {

    @Override
    protected void configure() {
        RestResourcesBinder.create(binder())
                .bind(DocumentAssetResourceImpl.class);

        ServletBinder.create(binder())
                .bind(DocumentAssetServlet.class);
    }
}
