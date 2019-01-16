package stroom.docstore;

import stroom.docstore.shared.Doc;
import stroom.security.SecurityContext;

import javax.inject.Inject;

public class StoreFactoryImpl implements StoreFactory {

    private final SecurityContext securityContext;
    private final Persistence persistence;

    @Inject
    public StoreFactoryImpl(final SecurityContext securityContext, final Persistence persistence) {
        this.securityContext = securityContext;
        this.persistence = persistence;
    }

    @Override
    public <D extends Doc> Store<D> createStore(final DocumentSerialiser2<D> serialiser,
                                                final String type,
                                                final Class<D> clazz) {
        Store<D> store = new StoreImpl<>(persistence, securityContext);

        store.setSerialiser(serialiser);
        store.setType(type, clazz);
        return store;
    }
}
