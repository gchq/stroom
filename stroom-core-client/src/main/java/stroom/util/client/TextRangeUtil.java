package stroom.util.client;

import stroom.explorer.shared.StringMatchLocation;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.Location;
import stroom.util.shared.TextRange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TextRangeUtil {

    public static List<TextRange> convertMatchesToRanges(final String string,
                                                         final List<StringMatchLocation> matchList) {
        if (!matchList.isEmpty()) {
            final char[] chars = string.toCharArray();
            int lineNo = 1;
            int colNo = 0;
            int matchNo = 0;
            StringMatchLocation match = matchList.get(matchNo);
            int start = match.getOffset();
            int end = Math.max(start, start + match.getLength() - 1);
            Location from = null;
            final List<TextRange> ranges = new ArrayList<>(matchList.size());

            for (int i = 0; i < chars.length && match != null; i++) {
                final char c = chars[i];
                if (c == '\n') {
                    lineNo++;
                    colNo = 0;
                } else {
                    colNo++;
                }

                if (i == start) {
                    from = new DefaultLocation(lineNo, colNo);
                }
                if (i == end) {
                    final Location to = new DefaultLocation(lineNo, colNo);
                    ranges.add(new TextRange(from, to));

                    from = null;
                    matchNo++;

                    if (matchList.size() > matchNo) {
                        match = matchList.get(matchNo);
                        start = match.getOffset();
                        end = Math.max(start, start + match.getLength() - 1);
                    } else {
                        match = null;
                    }
                }
            }

            return ranges;
        }
        return Collections.emptyList();
    }
}
