package stroom.query.impl;

import stroom.query.shared.FetchSuggestionsRequest;

import java.util.List;

public interface SuggestionsService {

    List<String> fetch(final FetchSuggestionsRequest request);
}
