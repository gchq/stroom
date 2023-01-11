package stroom.query.impl;

import stroom.query.shared.FetchSuggestionsRequest;

import java.util.List;

public interface SuggestionsQueryHandler {

    List<String> getSuggestions(final FetchSuggestionsRequest request);
}
