package stroom.suggestions.api;

import stroom.query.shared.FetchSuggestionsRequest;
import stroom.query.shared.Suggestions;

public interface SuggestionsQueryHandler {

    Suggestions getSuggestions(final FetchSuggestionsRequest request);
}
