package stroom.pipeline.xslt;

import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.StoreFactory;
import stroom.pipeline.shared.XsltDoc;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
class XsltStoreImpl
        extends AbstractDocumentStore<XsltDoc>
        implements XsltStore {

    @Inject
    XsltStoreImpl(final StoreFactory storeFactory,
                  final XsltSerialiser serialiser) {
        super(storeFactory,
                serialiser,
                XsltDoc.TYPE,
                XsltDoc::builder,
                XsltDoc::copy);
    }
}
