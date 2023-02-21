package stroom.explorer.impl;

import stroom.explorer.api.ExplorerFavService;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;

public class ExplorerFavModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(ExplorerFavService.class).to(ExplorerFavServiceImpl.class);

        RestResourcesBinder.create(binder())
                .bind(ExplorerFavouriteResourceImpl.class);
    }
}
