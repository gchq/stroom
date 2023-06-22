package stroom.suggestions.api;

import stroom.query.shared.FetchSuggestionsRequest;
import stroom.query.shared.Suggestions;

import java.util.List;

public interface SuggestionsQueryHandler {

    Suggestions getSuggestions(final FetchSuggestionsRequest request);
}
