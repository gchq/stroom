package stroom.kafka.impl;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.kafka.shared.KafkaConfigDoc;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;

public class KafkaConfigSerialiser implements DocumentSerialiser2<KafkaConfigDoc> {

    private final Serialiser2<KafkaConfigDoc> delegate;

    @Inject
    public KafkaConfigSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(KafkaConfigDoc.class);
    }

    @Override
    public KafkaConfigDoc read(final Map<String, byte[]> data) throws IOException {
        return delegate.read(data);
    }

    @Override
    public Map<String, byte[]> write(final KafkaConfigDoc document) throws IOException {
        return delegate.write(document);
    }
}
