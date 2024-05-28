package stroom.docstore.api;

import stroom.docref.DocContentHighlights;
import stroom.docref.DocContentMatch;
import stroom.explorer.shared.FetchHighlightsRequest;
import stroom.explorer.shared.FindInContentRequest;
import stroom.util.shared.ResultPage;

public interface ContentIndex {

    ResultPage<DocContentMatch> findInContent(FindInContentRequest request);

    DocContentHighlights fetchHighlights(FetchHighlightsRequest request);
}
