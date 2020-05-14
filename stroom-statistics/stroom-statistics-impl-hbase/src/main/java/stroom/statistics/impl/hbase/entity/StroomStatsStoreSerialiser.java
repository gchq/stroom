package stroom.statistics.impl.hbase.entity;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.statistics.impl.hbase.shared.StroomStatsStoreDoc;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;

public class StroomStatsStoreSerialiser implements DocumentSerialiser2<StroomStatsStoreDoc> {
    private final Serialiser2<StroomStatsStoreDoc> delegate;

    @Inject
    public StroomStatsStoreSerialiser(final Serialiser2Factory serialiser2Factory) {
        delegate = serialiser2Factory.createSerialiser(StroomStatsStoreDoc.class);
    }

    @Override
    public StroomStatsStoreDoc read(final Map<String, byte[]> data) throws IOException {
        return delegate.read(data);
    }

    @Override
    public Map<String, byte[]> write(final StroomStatsStoreDoc document) throws IOException {
        return delegate.write(document);
    }
}