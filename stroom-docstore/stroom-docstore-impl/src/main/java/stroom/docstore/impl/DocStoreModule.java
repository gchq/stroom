package stroom.docstore.impl;

import com.google.inject.AbstractModule;
import stroom.docstore.api.Persistence;
import stroom.docstore.api.Serialiser2Factory;
import stroom.docstore.api.StoreFactory;
import stroom.security.api.SecurityContext;

public class DocStoreModule extends AbstractModule {

    @Override
    protected void configure() {
        requireBinding(SecurityContext.class);
        requireBinding(Persistence.class);

//       install(new FactoryModuleBuilder().build(Store.Factory.class));
//        bind(Store.class).to(StoreImpl.class);

        bind(StoreFactory.class).to(StoreFactoryImpl.class);
        bind(Serialiser2Factory.class).to(Serialiser2FactoryImpl.class);
    }
}
