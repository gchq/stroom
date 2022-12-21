package stroom.core.query;

import stroom.query.shared.FetchSuggestionsRequest;

import java.util.List;

public interface SuggestionsQueryHandler {

    List<String> getSuggestions(final FetchSuggestionsRequest request);
}
