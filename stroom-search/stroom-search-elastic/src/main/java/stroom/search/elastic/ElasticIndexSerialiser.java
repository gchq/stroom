package stroom.search.elastic;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.search.elastic.shared.ElasticIndexDoc;

import java.io.IOException;
import java.util.Map;
import javax.inject.Inject;

class ElasticIndexSerialiser implements DocumentSerialiser2<ElasticIndexDoc> {

    private final Serialiser2<ElasticIndexDoc> delegate;

    @Inject
    ElasticIndexSerialiser(final Serialiser2Factory serialiser2Factory) {
        delegate = serialiser2Factory.createSerialiser(ElasticIndexDoc.class);
    }

    @Override
    public ElasticIndexDoc read(final Map<String, byte[]> data) throws IOException {
        return delegate.read(data);
    }

    @Override
    public Map<String, byte[]> write(final ElasticIndexDoc document) throws IOException {
        return delegate.write(document);
    }
}
