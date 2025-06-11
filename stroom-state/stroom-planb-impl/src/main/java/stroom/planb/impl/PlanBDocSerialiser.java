package stroom.planb.impl;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.planb.shared.PlanBDoc;

import jakarta.inject.Inject;

import java.io.IOException;
import java.util.Map;

public class PlanBDocSerialiser implements DocumentSerialiser2<PlanBDoc> {

    private final Serialiser2<PlanBDoc> delegate;

    @Inject
    PlanBDocSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(PlanBDoc.class);
    }

    @Override
    public PlanBDoc read(final Map<String, byte[]> data) throws IOException {
        return delegate.read(data);
    }

    @Override
    public Map<String, byte[]> write(final PlanBDoc document) throws IOException {
        return delegate.write(document);
    }
}
