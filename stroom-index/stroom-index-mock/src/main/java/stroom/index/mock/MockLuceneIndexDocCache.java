package stroom.index.mock;

import stroom.docref.DocRef;
import stroom.index.impl.LuceneIndexDocCache;
import stroom.index.shared.LuceneIndexDoc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MockLuceneIndexDocCache implements LuceneIndexDocCache {

    private final Map<DocRef, LuceneIndexDoc> indexDocMap = new ConcurrentHashMap<>();

    public void put(final DocRef key, final LuceneIndexDoc value) {
        indexDocMap.put(key, value);
    }

    @Override
    public LuceneIndexDoc get(final DocRef key) {
        return indexDocMap.get(key);
    }

    @Override
    public void remove(final DocRef key) {
        indexDocMap.remove(key);
    }
}
