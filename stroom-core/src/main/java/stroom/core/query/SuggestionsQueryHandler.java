package stroom.core.query;

import stroom.datasource.api.v2.AbstractField;
import stroom.docref.DocRef;

import java.util.List;

public interface SuggestionsQueryHandler {

    List<String> getSuggestions(final DocRef dataSource, final AbstractField field,
                                                   final String query);
}
