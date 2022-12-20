package stroom.view.impl;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.view.shared.ViewDoc;

import java.io.IOException;
import java.util.Map;
import javax.inject.Inject;

public class ViewSerialiser implements DocumentSerialiser2<ViewDoc> {

    private final Serialiser2<ViewDoc> delegate;

    @Inject
    public ViewSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(ViewDoc.class);
    }

    @Override
    public ViewDoc read(final Map<String, byte[]> data) throws IOException {
        final ViewDoc document = delegate.read(data);
        return document;
    }

    @Override
    public Map<String, byte[]> write(final ViewDoc document) throws IOException {
        final Map<String, byte[]> data = delegate.write(document);
        return data;
    }
}
