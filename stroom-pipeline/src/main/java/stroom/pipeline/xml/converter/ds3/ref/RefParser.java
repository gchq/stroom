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

package stroom.pipeline.xml.converter.ds3.ref;


import java.util.ArrayList;
import java.util.List;

public class RefParser {
    public static final char REF_CHAR = '$';
    public static final char QUOTE = '\'';
    private static final char COMPOSITE_DELIMITER = '+';
    private static final char ARRAY_START = '[';
    private static final char ARRAY_END = ']';

    public List<RefFactory> parse(final String reference) {
        final List<RefFactory> sections = new ArrayList<>();

        if (reference != null && !reference.isEmpty()) {
            final char[] chars = reference.toCharArray();
            boolean inQuote = false;
            boolean inArray = false;
            int pos = 0;

            // If the first character is $ or ' then it needs to be parsed
            // properly. If not then we will assume it is just a fixed string.
            if (chars[0] == REF_CHAR || chars[0] == QUOTE) {
                // Split the outer string.
                for (int i = 0; i < chars.length; i++) {
                    final char c = chars[i];

                    if (c == QUOTE) {
                        // Toggle quoted flag.
                        inQuote = !inQuote;
                    } else if (!inQuote) {
                        if (c == ARRAY_START) {
                            inArray = true;
                        } else if (inArray) {
                            if (c == ARRAY_END) {
                                inArray = false;
                            }
                        } else if (c == COMPOSITE_DELIMITER) {
                            // Entering a new section.
                            final String string = reference.substring(pos, i);
                            final RefFactory section = parseSection(reference, string);
                            sections.add(section);

                            pos = i + 1;
                        }
                    }
                }

                if (pos < chars.length) {
                    final String string = reference.substring(pos, chars.length);
                    final RefFactory section = parseSection(reference, string);
                    sections.add(section);
                }

            } else {
                // We are going to treat the reference as a fixed string.
                sections.add(new TextRefFactory(reference, reference));
            }
        }

        return sections;
    }

    private RefFactory parseSection(final String reference, final String string) {
        if (string.charAt(0) == REF_CHAR) {
            return parseRef(reference, string);
        } else {
            final String stripped = stripQuotes(string);
            return new TextRefFactory(reference, stripped);
        }
    }

    private StoreRefFactory parseRef(final String reference, final String string) {
        boolean inArray = false;
        int pos = 1;
        String refId = null;
        int refGroup = -1;
        MatchIndex matchIndex = null;

        final char[] chars = string.toCharArray();

        for (int i = 1; i < chars.length; i++) {
            final char c = chars[i];

            if (c == ARRAY_START) {
                if (pos < i) {
                    final String num = string.substring(pos, i);
                    try {
                        refGroup = Integer.parseInt(num);
                    } catch (final RuntimeException e) {
                        throw new RuntimeException("Malformed lookup group '" + num + "' specified at position " + pos
                                + " in reference '" + reference + "'");
                    }
                }
                inArray = true;
                pos = i + 1;
            } else if (inArray) {
                if (c == ARRAY_END) {
                    inArray = false;
                    final String sec = string.substring(pos, i);
                    matchIndex = parseMatchIndex(reference, sec);
                    pos = i + 1;
                }
            } else if (c == REF_CHAR) {
                if (pos == 1) {
                    // This is a ref id.
                    refId = string.substring(pos, i);
                }
                pos = i + 1;
            }
        }

        if (refGroup == -1) {
            if (pos < chars.length) {
                final String num = string.substring(pos, chars.length);
                try {
                    refGroup = Integer.parseInt(num);
                } catch (final RuntimeException e) {
                    throw new RuntimeException("Malformed lookup group '" + num + "' specified at position " + pos
                            + " in reference '" + reference + "'");
                }
            } else {
                refGroup = 0;
            }
        }

        return new StoreRefFactory(reference, string, refId, refGroup, matchIndex);
    }

    private MatchIndex parseMatchIndex(final String reference, final String string) {
        if (string.length() == 0) {
            throw new RuntimeException("Empty match index specified in reference '" + reference + "'");
        }

        String num = null;

        try {
            final boolean negative = string.charAt(0) == '-';
            final boolean positive = string.charAt(0) == '+';

            if (positive) {
                num = string.substring(1);
            } else {
                num = string;
            }
            final int index = Integer.parseInt(num);

            return new MatchIndex(index, negative || positive);
        } catch (final RuntimeException e) {
            throw new RuntimeException(
                    "Malformed lookup index '" + num + "' specified in reference '" + reference + "'");
        }
    }

    private String stripQuotes(final String string) {
        if (string.charAt(0) != QUOTE) {
            return string;
        }

        final char[] charsIn = string.toCharArray();
        final char[] charsOut = new char[charsIn.length];

        int i = 0;
        int j = 0;
        for (; i < charsIn.length; i++) {
            final char c = charsIn[i];
            if (c == QUOTE) {
                if (i < charsIn.length - 1 && charsIn[i + 1] == QUOTE) {
                    charsOut[j++] = c;
                    i++;
                }
            } else {
                charsOut[j++] = c;
            }
        }

        return new String(charsOut, 0, j);
    }
}
