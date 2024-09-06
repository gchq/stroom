package stroom.contentindex;

import stroom.docref.StringMatchLocation;

import java.util.List;

public interface ContentHighlighter {

    List<StringMatchLocation> getHighlights(String field, String text, int maxMatches);
}
