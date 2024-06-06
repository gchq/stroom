package stroom.contentindex;

import stroom.docref.StringMatch;
import stroom.docref.StringMatchLocation;
import stroom.util.string.StringMatcher;

import java.util.List;

public class BasicContentHighlighter implements ContentHighlighter {

    private final StringMatcher stringMatcher;

    public BasicContentHighlighter(final StringMatch stringMatch) {
        stringMatcher = new StringMatcher(stringMatch);
    }

    @Override
    public List<StringMatchLocation> getHighlights(final String field, final String text, final int maxMatches) {
        return stringMatcher.match(text, maxMatches);
    }
}
