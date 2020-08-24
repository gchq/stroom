package stroom.core.query;

import stroom.query.shared.FetchSuggestionsRequest;

import java.util.List;

public interface SuggestionsService {
    List<String> fetch(final FetchSuggestionsRequest request);
}
