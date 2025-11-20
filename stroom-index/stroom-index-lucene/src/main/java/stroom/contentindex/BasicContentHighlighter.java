package stroom.contentindex;

import stroom.explorer.shared.StringMatch;
import stroom.explorer.shared.StringMatchLocation;

import java.util.List;

public class BasicContentHighlighter implements ContentHighlighter {

    private final StringMatcher stringMatcher;

    public BasicContentHighlighter(final StringMatch stringMatch) {
        stringMatcher = new StringMatcher(stringMatch);
    }

    @Override
    public List<StringMatchLocation> getHighlights(final String text, final int maxMatches) {
        return stringMatcher.match(text, maxMatches);
    }
}
