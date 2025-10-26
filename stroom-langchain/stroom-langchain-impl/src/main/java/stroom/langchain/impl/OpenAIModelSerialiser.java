package stroom.langchain.impl;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.openai.shared.OpenAIModelDoc;

import jakarta.inject.Inject;

import java.io.IOException;
import java.util.Map;

public class OpenAIModelSerialiser implements DocumentSerialiser2<OpenAIModelDoc> {

    private final Serialiser2<OpenAIModelDoc> delegate;

    @Inject
    OpenAIModelSerialiser(
            final Serialiser2Factory serialiser2Factory
    ) {
        this.delegate = serialiser2Factory.createSerialiser(OpenAIModelDoc.class);
    }

    @Override
    public OpenAIModelDoc read(final Map<String, byte[]> data) throws IOException {
        return delegate.read(data);
    }

    @Override
    public Map<String, byte[]> write(final OpenAIModelDoc document) throws IOException {
        return delegate.write(document);
    }
}
