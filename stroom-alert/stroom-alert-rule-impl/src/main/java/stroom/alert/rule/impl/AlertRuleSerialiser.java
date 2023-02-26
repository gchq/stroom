package stroom.alert.rule.impl;

import stroom.alert.rule.shared.AlertRuleDoc;
import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;

import java.io.IOException;
import java.util.Map;
import javax.inject.Inject;

public class AlertRuleSerialiser implements DocumentSerialiser2<AlertRuleDoc> {

    private final Serialiser2<AlertRuleDoc> delegate;

    @Inject
    public AlertRuleSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(AlertRuleDoc.class);
    }

    @Override
    public AlertRuleDoc read(final Map<String, byte[]> data) throws IOException {
        final AlertRuleDoc document = delegate.read(data);
        return document;
    }

    @Override
    public Map<String, byte[]> write(final AlertRuleDoc document) throws IOException {
        final Map<String, byte[]> data = delegate.write(document);
        return data;
    }
}
