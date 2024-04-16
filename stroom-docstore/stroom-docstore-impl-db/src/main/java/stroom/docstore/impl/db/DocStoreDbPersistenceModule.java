package stroom.docstore.impl.db;

import stroom.docstore.api.Persistence;

import com.google.inject.AbstractModule;

public class DocStoreDbPersistenceModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(Persistence.class).to(DBPersistence.class);
    }
}
