package stroom.ai.impl;

import stroom.ai.api.OpenAIModelStore;
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.StoreFactory;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.security.api.SecurityContext;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class OpenAIModelStoreImpl
        extends AbstractDocumentStore<OpenAIModelDoc>
        implements OpenAIModelStore {

    @Inject
    public OpenAIModelStoreImpl(
            final StoreFactory storeFactory,
            final SecurityContext securityContext,
            final OpenAIModelSerialiser serialiser) {
        super(storeFactory,
                securityContext,
                serialiser,
                OpenAIModelDoc.TYPE,
                OpenAIModelDoc::builder,
                OpenAIModelDoc::copy);
    }
}
