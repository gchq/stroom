package stroom.pathways.impl;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.pathways.shared.PathwaysDoc;

import jakarta.inject.Inject;

import java.io.IOException;
import java.util.Map;

public class PathwaysSerialiser implements DocumentSerialiser2<PathwaysDoc> {

    private final Serialiser2<PathwaysDoc> delegate;

    @Inject
    public PathwaysSerialiser(final Serialiser2Factory serialiser2Factory) {
        delegate = serialiser2Factory.createSerialiser(PathwaysDoc.class);
    }

    @Override
    public PathwaysDoc read(final Map<String, byte[]> data) throws IOException {
        return delegate.read(data);
    }

    @Override
    public Map<String, byte[]> write(final PathwaysDoc document) throws IOException {
        return delegate.write(document);
    }
}
