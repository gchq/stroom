package stroom.docstore.api;

import stroom.docstore.shared.AbstractDoc;

public interface StoreFactory {

    <D extends AbstractDoc> Store<D> createStore(final DocumentSerialiser2<D> serialiser,
                                                 final String type,
                                                 final Class<D> clazz);
}
