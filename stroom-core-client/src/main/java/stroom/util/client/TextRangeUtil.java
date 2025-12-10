/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
