package stroom.query.common.v2;

import stroom.docref.DocRef;
import stroom.query.api.datasource.IndexField;

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
