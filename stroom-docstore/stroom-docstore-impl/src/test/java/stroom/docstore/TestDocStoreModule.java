package stroom.docstore;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import stroom.docstore.shared.Doc;
import stroom.security.SecurityContext;

@ExtendWith(MockitoExtension.class)
class TestDocStoreModule {

    @Mock
    private Persistence persistenceMock;
    @Mock
    private SecurityContext securityContextMock;
    @Mock
    private Doc docMock;

    @Inject
    Store store;

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
//        final Store<MyDoc> store = injector.getInstance(Store<MyDoc>.class);
        final DocumentSerialiser2<MyDoc> serialiser = serialiser2Factory.createSerialiser(MyDoc.class);

        store.setSerialiser(serialiser);

        Store<MyDoc> store2 = store;
    }

    private static class MyDoc extends Doc {
    }

}