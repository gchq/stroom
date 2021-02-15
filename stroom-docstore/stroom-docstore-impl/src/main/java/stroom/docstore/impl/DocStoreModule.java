package stroom.docstore.impl;

import stroom.docstore.api.DocumentResourceHelper;
import stroom.docstore.api.Serialiser2Factory;
import stroom.docstore.api.StoreFactory;
import stroom.event.logging.api.DocumentEventLog;
import stroom.security.api.SecurityContext;

import com.google.inject.AbstractModule;

public class DocStoreModule extends AbstractModule {

    @Override
    protected void configure() {
        requireBinding(SecurityContext.class);
        requireBinding(Persistence.class);
        requireBinding(DocumentEventLog.class);

        bind(DocumentResourceHelper.class).to(LoggingDocumentResourceHelperImpl.class);
        bind(StoreFactory.class).to(StoreFactoryImpl.class);
        bind(Serialiser2Factory.class).to(Serialiser2FactoryImpl.class);
    }
}
