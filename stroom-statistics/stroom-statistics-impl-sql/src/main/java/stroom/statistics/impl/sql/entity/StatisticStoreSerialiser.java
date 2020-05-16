package stroom.statistics.impl.sql.entity;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;

public class StatisticStoreSerialiser implements DocumentSerialiser2<StatisticStoreDoc> {
    private final Serialiser2<StatisticStoreDoc> delegate;

    @Inject
    public StatisticStoreSerialiser(final Serialiser2Factory serialiser2Factory) {
        delegate = serialiser2Factory.createSerialiser(StatisticStoreDoc.class);
    }

    @Override
    public StatisticStoreDoc read(final Map<String, byte[]> data) throws IOException {
        return delegate.read(data);
    }

    @Override
    public Map<String, byte[]> write(final StatisticStoreDoc document) throws IOException {
        return delegate.write(document);
    }
}