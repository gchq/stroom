package stroom.core.query;

import stroom.docref.DocRef;
import stroom.query.shared.FetchSuggestionsRequest;

import java.util.List;
import java.util.function.Function;

public interface SuggestionsService {

    void registerHandler(final String dataSourceType, final SuggestionsQueryHandler handler);

    List<String> fetch(final FetchSuggestionsRequest request);
}
