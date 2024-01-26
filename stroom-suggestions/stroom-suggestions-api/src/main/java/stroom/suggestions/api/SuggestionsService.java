package stroom.suggestions.api;

import stroom.query.shared.FetchSuggestionsRequest;
import stroom.query.shared.Suggestions;

public interface SuggestionsService {

    Suggestions fetch(final FetchSuggestionsRequest request);
}
