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

package stroom.pipeline.xml.converter.ds3;

import stroom.pipeline.xml.converter.ds3.NodeFactory.NodeType;
import stroom.pipeline.xml.converter.ds3.ref.VarMap;

public class Split extends Expression implements Match {

    private static final char SPACE = ' ';

    private final SplitFactory factory;
    private CharSequence cs;
    private int[] start = new int[2];
    private int[] end = new int[2];
    private CharBuffer escapeFiltered;
    private int filteredGroup = -1;

    public Split(final VarMap varMap, final SplitFactory factory) {
        super(varMap, factory);
        this.factory = factory;
    }

    @Override
    public void setInput(final CharSequence cs) {
        this.cs = cs;
        start[0] = -1;
        start[1] = -1;
        end[0] = -1;
        end[1] = -1;
        escapeFiltered = null;
    }

    @Override
    public Match match() {
        if (find()) {
            return this;
        }

        return null;
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.SPLIT;
    }

    private boolean find() {
        boolean inContainer = false;
        boolean escape = false;

        final int outerStart = 0;
        int outerEnd = 0;
        int innerStart = 0;
        int innerEnd = -1;

        for (; outerEnd < cs.length(); ) {
            // See if we have a delimiter.
            if (!escape) {
                if (!inContainer && isSubstring(cs, outerEnd, factory.getDelimiter())) {
                    // We are at the end so don't need to add further content.
                    innerEnd = outerEnd;
                    outerEnd += factory.getDelimiter().length;
                    break;
                } else if (isSubstring(cs, outerEnd, factory.getEscape())) {
                    escape = true;
                    outerEnd += factory.getEscape().length;
                } else if (!inContainer && isSubstring(cs, outerEnd, factory.getContainerStart())) {
                    inContainer = true;
                    outerEnd += factory.getContainerStart().length;
                } else if (inContainer && isSubstring(cs, outerEnd, factory.getContainerEnd())) {
                    inContainer = false;
                    outerEnd += factory.getContainerEnd().length;
                } else {
                    outerEnd++;
                }
            } else {
                outerEnd++;
                escape = false;
            }
        }

        // Set the delimiter position to be the end if it wasn't found.
        if (innerEnd == -1) {
            innerEnd = outerEnd;
        }

        // Move start forward to remove whitespace.
        while (innerEnd > innerStart && cs.charAt(innerStart) <= SPACE) {
            innerStart++;
        }
        // Move end backward to remove whitespace.
        while (innerEnd > innerStart && cs.charAt(innerEnd - 1) <= SPACE) {
            innerEnd--;
        }

        // Remove first container if present.
        if (factory.getContainerStart() != null) {
            if (isSubstring(cs, innerStart, factory.getContainerStart())) {
                innerStart += factory.getContainerStart().length;
            }
        }
        // Remove end container if present.
        if (factory.getContainerEnd() != null && innerEnd > factory.getContainerEnd().length) {
            if (isSubstring(cs, innerEnd - factory.getContainerEnd().length, factory.getContainerEnd())) {
                innerEnd -= factory.getContainerStart().length;
            }
        }

        // Remove more whitespace.
        while (innerEnd > innerStart && cs.charAt(innerStart) <= SPACE) {
            innerStart++;
        }
        // Remove more whitespace.
        while (innerEnd > innerStart && cs.charAt(innerEnd - 1) <= SPACE) {
            innerEnd--;
        }

        start[0] = outerStart;
        start[1] = innerStart;
        end[0] = outerEnd;
        end[1] = innerEnd;
        escapeFiltered = null;

        // If we matched some content then the offset will be greater than the
        // original offset.
        return end[0] > start[0];
    }

    boolean isSubstring(final CharSequence cs, final int offset, final char[] sub) {
        if (sub == null) {
            return false;
        }

        int off = 0;
        for (; off + offset < cs.length() && off < sub.length; off++) {
            if (cs.charAt(off + offset) != sub[off]) {
                return false;
            }
        }

        // Only return true if we got to the end of the sub delimiter when
        // matching.
        return off == sub.length;
    }

    @Override
    public int start() {
        return start[0];
    }

    @Override
    public int start(final int group) {
        if (group > 2) {
            throw new IndexOutOfBoundsException("No group " + group);
        }

        return start[group];
    }

    @Override
    public int end() {
        return end[0];
    }

    @Override
    public int end(final int group) {
        if (group > 2) {
            throw new IndexOutOfBoundsException("No group " + group);
        }

        return end[group];
    }

    @Override
    public Buffer filter(final Buffer buffer, final int group) {
        if (group > 3) {
            throw new IndexOutOfBoundsException("No group " + group);
        }

        // Group 0 returns the whole string. Group 1 is the whole string minus
        // delimiter, whitespace and outermost container characters. Group 2 is
        // string minus delimiter and filtered to remove escape characters.
        // Group 3 will return the string minus escape chars before delimiters
        if (group <= 1 || factory.getEscape() == null) {
            if (start[group] == 0 && end[group] == buffer.length()) {
                return buffer.unsafeCopy();
            } else {
                return buffer.subSequence(start[group], end[group] - start[group]);
            }

        } else {
            // Produce escape filtered buffer if we haven't already.
            if (escapeFiltered == null || filteredGroup != group) {
                escapeFiltered = getFilteredBuffer(group);
                filteredGroup = group;
            }

            return escapeFiltered;
        }
    }

    private CharBuffer getFilteredBuffer(final int group) {
        // Produce filtered buffer that removes escape characters.
        boolean escape = false;
        int pos = start[1];

        final StringBuilder sb = new StringBuilder();
        for (; pos < end[1]; ) {
            if (!escape) {
                if (isSubstring(cs, pos, factory.getEscape())) {
                    escape = true;
                    pos += factory.getEscape().length;
                } else {
                    sb.append(cs.charAt(pos));
                    pos++;
                }
            } else {
                if (group == 3 && !isSubstring(cs, pos, factory.getDelimiter())) {
                    sb.append(cs.charAt(pos - 1));
                }
                sb.append(cs.charAt(pos));
                pos++;
                escape = false;
            }
        }

        final char[] chars = sb.toString().toCharArray();
        return new CharBuffer(chars, 0, chars.length);
    }
}
