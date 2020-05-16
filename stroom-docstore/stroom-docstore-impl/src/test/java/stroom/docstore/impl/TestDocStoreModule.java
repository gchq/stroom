package stroom.docstore.impl;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.shared.Doc;
import stroom.event.logging.api.DocumentEventLog;
import stroom.importexport.api.ImportConverter;
import stroom.security.api.SecurityContext;
import stroom.util.entityevent.EntityEventBus;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.util.Providers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestDocStoreModule {
    @Mock
    private Persistence persistenceMock;
    @Mock
    private EntityEventBus entityEventBus;
    @Mock
    private SecurityContext securityContextMock;
    @Mock
    private Doc docMock;

    @Inject
    StoreFactory storeFactory;

    @Test
    void testInjection() {
        final Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(Persistence.class).toInstance(persistenceMock);
                bind(EntityEventBus.class).toInstance(entityEventBus);
                bind(SecurityContext.class).toInstance(securityContextMock);
                bind(DocumentEventLog.class).toProvider(Providers.of(null));
                bind(ImportConverter.class).toProvider(Providers.of((docRef, dataMap, importState, importMode, userId) -> dataMap));
                install(new DocStoreModule());
            }
        });

        final Serialiser2Factory serialiser2Factory = injector.getInstance(Serialiser2Factory.class);
        injector.injectMembers(this);
        final DocumentSerialiser2<MyDoc> serialiser = serialiser2Factory.createSerialiser(MyDoc.class);

        Store<MyDoc> store2 = storeFactory.createStore(serialiser, "MyDocType", MyDoc.class);
    }

    private static class MyDoc extends Doc {
    }
}