package stroom.feed.impl;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.feed.shared.FeedDoc;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;

public class FeedSerialiser implements DocumentSerialiser2<FeedDoc> {
    private final Serialiser2<FeedDoc> delegate;

    @Inject
    public FeedSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(FeedDoc.class);
    }

    @Override
    public FeedDoc read(final Map<String, byte[]> data) throws IOException {
        return delegate.read(data);
    }

    @Override
    public Map<String, byte[]> write(final FeedDoc document) throws IOException {
        return delegate.write(document);
    }
}