package stroom.pipeline.xslt;

import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.StoreFactory;
import stroom.pipeline.shared.XsltDoc;
import stroom.security.api.SecurityContext;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
class XsltStoreImpl
        extends AbstractDocumentStore<XsltDoc>
        implements XsltStore {

    @Inject
    XsltStoreImpl(final StoreFactory storeFactory,
                  final SecurityContext securityContext,
                  final XsltSerialiser serialiser) {
        super(storeFactory,
                securityContext,
                serialiser,
                XsltDoc.TYPE,
                XsltDoc::builder,
                XsltDoc::copy);
    }
}
