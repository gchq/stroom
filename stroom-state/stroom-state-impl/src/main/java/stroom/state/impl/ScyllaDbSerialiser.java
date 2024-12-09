package stroom.state.impl;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.state.shared.ScyllaDbDoc;

import jakarta.inject.Inject;

import java.io.IOException;
import java.util.Map;

public class ScyllaDbSerialiser implements DocumentSerialiser2<ScyllaDbDoc> {

    private final Serialiser2<ScyllaDbDoc> delegate;

    @Inject
    ScyllaDbSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(ScyllaDbDoc.class);
    }

    @Override
    public ScyllaDbDoc read(final Map<String, byte[]> data) throws IOException {
        return delegate.read(data);
    }

    @Override
    public Map<String, byte[]> write(final ScyllaDbDoc document) throws IOException {
        return delegate.write(document);
    }
}
