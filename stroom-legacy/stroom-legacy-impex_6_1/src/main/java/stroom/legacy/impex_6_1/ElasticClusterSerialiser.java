package stroom.legacy.impex_6_1;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.search.elastic.shared.ElasticClusterDoc;

import java.io.IOException;
import java.util.Map;
import javax.inject.Inject;

@Deprecated
public class ElasticClusterSerialiser implements DocumentSerialiser2<ElasticClusterDoc> {

    private final Serialiser2<ElasticClusterDoc> delegate;

    @Inject
    ElasticClusterSerialiser(
            final Serialiser2Factory serialiser2Factory
    ) {
        this.delegate = serialiser2Factory.createSerialiser(ElasticClusterDoc.class);
    }

    @Override
    public ElasticClusterDoc read(final Map<String, byte[]> data) throws IOException {
        return delegate.read(data);
    }

    @Override
    public Map<String, byte[]> write(final ElasticClusterDoc document) throws IOException {
        return delegate.write(document);
    }
}
