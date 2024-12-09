package stroom.state.impl;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.state.shared.StateDoc;

import jakarta.inject.Inject;

import java.io.IOException;
import java.util.Map;

public class StateDocSerialiser implements DocumentSerialiser2<StateDoc> {

    private final Serialiser2<StateDoc> delegate;

    @Inject
    StateDocSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(StateDoc.class);
    }

    @Override
    public StateDoc read(final Map<String, byte[]> data) throws IOException {
        return delegate.read(data);
    }

    @Override
    public Map<String, byte[]> write(final StateDoc document) throws IOException {
        return delegate.write(document);
    }
}
