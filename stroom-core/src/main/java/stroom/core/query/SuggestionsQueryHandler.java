package stroom.core.query;

import stroom.datasource.api.v2.AbstractField;
import stroom.docref.DocRef;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface SuggestionsQueryHandler {

    CompletableFuture<List<String>> getSuggestions(final DocRef dataSource, final AbstractField field,
                                                   final String query);
}
