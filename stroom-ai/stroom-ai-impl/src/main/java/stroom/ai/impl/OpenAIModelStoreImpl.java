package stroom.ai.impl;

import stroom.ai.api.OpenAIModelStore;
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.StoreFactory;
import stroom.openai.shared.OpenAIModelDoc;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class OpenAIModelStoreImpl
        extends AbstractDocumentStore<OpenAIModelDoc>
        implements OpenAIModelStore {

    @Inject
    public OpenAIModelStoreImpl(
            final StoreFactory storeFactory,
            final OpenAIModelSerialiser serialiser) {
        super(storeFactory,
                serialiser,
                OpenAIModelDoc.TYPE,
                OpenAIModelDoc::builder,
                OpenAIModelDoc::copy);
    }
}
