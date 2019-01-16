package stroom.docstore;

import com.google.inject.AbstractModule;
import stroom.security.SecurityContext;

public class DocStoreModule extends AbstractModule {

    @Override
    protected void configure() {
        requireBinding(SecurityContext.class);
        requireBinding(Persistence.class);

//       install(new FactoryModuleBuilder().build(StoreImpl.Factory.class));
        bind(Store.class).to(StoreImpl.class);
        bind(Serialiser2Factory.class).to(Serialiser2FactoryImpl.class);
    }
}
