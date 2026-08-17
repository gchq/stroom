package stroom.pipeline.textconverter;

import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.StoreFactory;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.security.api.SecurityContext;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
class TextConverterStoreImpl
        extends AbstractDocumentStore<TextConverterDoc>
        implements TextConverterStore {

    @Inject
    TextConverterStoreImpl(final StoreFactory storeFactory,
                           final SecurityContext securityContext,
                           final TextConverterSerialiser serialiser) {
        super(storeFactory,
                securityContext,
                serialiser,
                TextConverterDoc.TYPE,
                TextConverterDoc::builder,
                TextConverterDoc::copy);
    }
}
