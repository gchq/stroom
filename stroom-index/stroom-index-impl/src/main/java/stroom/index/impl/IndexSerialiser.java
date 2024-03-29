package stroom.index.impl;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.index.shared.LuceneIndexDoc;

import jakarta.inject.Inject;

import java.io.IOException;
import java.util.Map;

public class IndexSerialiser implements DocumentSerialiser2<LuceneIndexDoc> {

    private final Serialiser2<LuceneIndexDoc> delegate;

    @Inject
    public IndexSerialiser(final Serialiser2Factory serialiser2Factory) {
        delegate = serialiser2Factory.createSerialiser(LuceneIndexDoc.class);
    }

    @Override
    public LuceneIndexDoc read(final Map<String, byte[]> data) throws IOException {
        return delegate.read(data);
    }

    @Override
    public Map<String, byte[]> write(final LuceneIndexDoc document) throws IOException {
        return delegate.write(document);
    }
}
