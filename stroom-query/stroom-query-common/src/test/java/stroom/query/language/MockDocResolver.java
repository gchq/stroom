package stroom.query.language;

import stroom.docref.DocRef;

public class MockDocResolver extends DocResolver {

    public MockDocResolver() {
        super(null, null);
    }

    @Override
    public DocRef resolveDataSourceRef(final String name) {
        return new DocRef(null, null, name);
    }

    @Override
    public DocRef resolveDocRef(final String type, final String name) {
        return new DocRef(null, null, name);
    }
}
