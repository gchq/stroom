package stroom.pipeline.textconverter;

import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.StoreFactory;
import stroom.pipeline.shared.TextConverterDoc;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
class TextConverterStoreImpl
        extends AbstractDocumentStore<TextConverterDoc>
        implements TextConverterStore {

    @Inject
    TextConverterStoreImpl(final StoreFactory storeFactory,
                           final TextConverterSerialiser serialiser) {
        super(storeFactory,
                serialiser,
                TextConverterDoc.TYPE,
                TextConverterDoc::builder,
                TextConverterDoc::copy);
    }
}
