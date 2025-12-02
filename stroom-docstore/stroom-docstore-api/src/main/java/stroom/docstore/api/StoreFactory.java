package stroom.docstore.api;

import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.AbstractDoc.AbstractBuilder;

import java.util.function.Supplier;

public interface StoreFactory {

    <D extends AbstractDoc> Store<D> createStore(final DocumentSerialiser2<D> serialiser,
                                                 final String type,
                                                 final Supplier<AbstractBuilder<D, ?>> builderSupplier);
}
