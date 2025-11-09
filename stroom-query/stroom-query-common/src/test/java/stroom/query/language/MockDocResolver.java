package stroom.query.language;

import stroom.docref.DocRef;

public class MockDocResolver extends DocResolver {

    public static final MockDocResolver INSTANCE = new MockDocResolver();

    public static MockDocResolver getInstance() {
        return INSTANCE;
    }

    private MockDocResolver() {
        super(null, null);
    }

    @Override
    public DocRef resolveDataSourceRef(final String name) {
        return new DocRef(name, name, name);
    }

    @Override
    public DocRef resolveDocRef(final String type, final String name) {
        return new DocRef(type, name, name);
    }
}
