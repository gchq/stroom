package stroom.search.solr;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.search.solr.shared.SolrIndexDoc;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;

class SolrIndexSerialiser implements DocumentSerialiser2<SolrIndexDoc> {
    private final Serialiser2<SolrIndexDoc> delegate;

    @Inject
    SolrIndexSerialiser(final Serialiser2Factory serialiser2Factory) {
        delegate = serialiser2Factory.createSerialiser(SolrIndexDoc.class);
    }

    @Override
    public SolrIndexDoc read(final Map<String, byte[]> data) throws IOException {
        return delegate.read(data);
    }

    @Override
    public Map<String, byte[]> write(final SolrIndexDoc document) throws IOException {
        return delegate.write(document);
    }
}