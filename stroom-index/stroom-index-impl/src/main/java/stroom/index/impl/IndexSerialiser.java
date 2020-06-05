package stroom.index.impl;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.index.shared.IndexDoc;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;

public class IndexSerialiser implements DocumentSerialiser2<IndexDoc> {
    private final Serialiser2<IndexDoc> delegate;

    @Inject
    public IndexSerialiser(final Serialiser2Factory serialiser2Factory) {
        delegate = serialiser2Factory.createSerialiser(IndexDoc.class);
    }

    @Override
    public IndexDoc read(final Map<String, byte[]> data) throws IOException {
        return delegate.read(data);
    }

    @Override
    public Map<String, byte[]> write(final IndexDoc document) throws IOException {
        return delegate.write(document);
    }
}