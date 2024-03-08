package stroom.search.extraction;

import stroom.query.api.v2.SearchRequest;

import java.util.List;

public interface MemoryIndex {

    boolean match(SearchRequest searchRequest, List<FieldValue> fieldValues);
}
