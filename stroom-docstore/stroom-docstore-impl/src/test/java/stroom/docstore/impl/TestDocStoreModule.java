package stroom.docstore.impl;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Persistence;
import stroom.docstore.api.Serialiser2Factory;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.shared.Doc;
import stroom.security.api.SecurityContext;

@ExtendWith(MockitoExtension.class)
class TestDocStoreModule {

    @Mock
    private Persistence persistenceMock;
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
                bind(SecurityContext.class).toInstance(securityContextMock);
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