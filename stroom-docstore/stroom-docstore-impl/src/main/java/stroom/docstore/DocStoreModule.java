package stroom.docstore;

import com.google.inject.AbstractModule;

public class DocStoreModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(Store.class).to(StoreImpl.class);

        bind(Serialiser2Factory.class).to(Serialiser2FactoryImpl.class);
    }
}
