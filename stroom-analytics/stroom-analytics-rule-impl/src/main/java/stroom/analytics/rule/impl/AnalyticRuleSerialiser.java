package stroom.analytics.rule.impl;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;

import java.io.IOException;
import java.util.Map;
import javax.inject.Inject;

public class AnalyticRuleSerialiser implements DocumentSerialiser2<AnalyticRuleDoc> {

    private final Serialiser2<AnalyticRuleDoc> delegate;

    @Inject
    public AnalyticRuleSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(AnalyticRuleDoc.class);
    }

    @Override
    public AnalyticRuleDoc read(final Map<String, byte[]> data) throws IOException {
        final AnalyticRuleDoc document = delegate.read(data);
        return document;
    }

    @Override
    public Map<String, byte[]> write(final AnalyticRuleDoc document) throws IOException {
        final Map<String, byte[]> data = delegate.write(document);
        return data;
    }
}
