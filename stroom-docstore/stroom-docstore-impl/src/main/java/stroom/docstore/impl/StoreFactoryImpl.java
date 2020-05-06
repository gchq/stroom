package stroom.docstore.impl;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.shared.Doc;
import stroom.security.api.SecurityContext;
import stroom.util.entityevent.EntityEventBus;

import javax.inject.Inject;

public class StoreFactoryImpl implements StoreFactory {
    private final Persistence persistence;
    private final EntityEventBus entityEventBus;
    private final SecurityContext securityContext;

    @Inject
    public StoreFactoryImpl(final Persistence persistence,
                            final EntityEventBus entityEventBus,
                            final SecurityContext securityContext) {
        this.persistence = persistence;
        this.entityEventBus = entityEventBus;
        this.securityContext = securityContext;
    }

    @Override
    public <D extends Doc> Store<D> createStore(final DocumentSerialiser2<D> serialiser,
                                                final String type,
                                                final Class<D> clazz) {
        return new StoreImpl<>(persistence, entityEventBus, securityContext, serialiser, type, clazz);
    }
}
