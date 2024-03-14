package stroom.index.shared;

import stroom.datasource.api.v2.IndexField;
import stroom.docref.DocRef;

import java.util.HashMap;
import java.util.Map;

public class MockIndexFieldCache implements IndexFieldCache {

    private final Map<String, IndexField> indexFieldMap = new HashMap<>();

    @Override
    public IndexField get(final DocRef key, final String fieldName) {
        return indexFieldMap.get(fieldName);
    }

    public void put(final String fieldName, final IndexField indexField) {
        indexFieldMap.put(fieldName, indexField);
    }
}
