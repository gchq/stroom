package stroom.sigmarule.impl;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.sigmarule.shared.SigmaRuleDoc;

import java.io.IOException;
import java.util.Map;
import javax.inject.Inject;

public class SigmaRuleSerialiser implements DocumentSerialiser2<SigmaRuleDoc> {

    private final Serialiser2<SigmaRuleDoc> delegate;

    @Inject
    public SigmaRuleSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(SigmaRuleDoc.class);
    }

    @Override
    public SigmaRuleDoc read(final Map<String, byte[]> data) throws IOException {
        final SigmaRuleDoc document = delegate.read(data);
        return document;
    }

    @Override
    public Map<String, byte[]> write(final SigmaRuleDoc document) throws IOException {
        final Map<String, byte[]> data = delegate.write(document);
        return data;
    }
}
