package stroom.docstore.fav.impl;

import stroom.docstore.fav.api.DocFavService;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;

public class DocFavModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(DocFavService.class).to(DocFavServiceImpl.class);

        RestResourcesBinder.create(binder())
                .bind(DocFavResourceImpl.class);
    }
}
