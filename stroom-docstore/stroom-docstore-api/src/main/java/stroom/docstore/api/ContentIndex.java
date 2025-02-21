package stroom.docstore.api;

import stroom.explorer.shared.DocContentHighlights;
import stroom.explorer.shared.DocContentMatch;
import stroom.explorer.shared.FetchHighlightsRequest;
import stroom.explorer.shared.FindInContentRequest;
import stroom.util.shared.ResultPage;

public interface ContentIndex {

    ResultPage<DocContentMatch> findInContent(FindInContentRequest request);

    DocContentHighlights fetchHighlights(FetchHighlightsRequest request);

    void reindex();
}
