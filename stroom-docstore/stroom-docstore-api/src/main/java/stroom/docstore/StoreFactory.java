package stroom.docstore;

import stroom.docstore.shared.Doc;

public interface StoreFactory {

    <D extends Doc> Store<D> createStore(final DocumentSerialiser2<D> serialiser,
                                         final String type,
                                         final Class<D> clazz);
}
