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

import stroom.pipeline.xml.converter.ds3.ref.VarMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class RegexFactory extends ExpressionFactory {

    private final Pattern pattern;

    public RegexFactory(final NodeFactory parent, final String id, final String pattern) {
        this(parent, id, 0, -1, null, -1, pattern, 0);
    }

    public RegexFactory(final NodeFactory parent, final String id, final int minMatch, final int maxMatch,
                        final Set<Integer> onlyMatch, final int advance, final String pattern, final int flags) {
        super(parent, id, minMatch, maxMatch, onlyMatch, advance);
        if (pattern != null) {
            this.pattern = Pattern.compile(pattern, flags);
        } else {
            this.pattern = null;
        }

        final StringBuilder sb = new StringBuilder();
        if (pattern != null) {
            sb.append(" pattern=\"");
            sb.append(pattern);
            sb.append("\"");
        }
        if ((flags & Pattern.CASE_INSENSITIVE) != 0) {
            sb.append(" caseInsensitive=\"true\"");
        }
        if ((flags & Pattern.DOTALL) != 0) {
            sb.append(" dotAll=\"true\"");
        }
        if (advance != -1) {
            sb.append(" advance=\"");
            sb.append(advance);
            sb.append("\"");
        }
        setAttributes(sb.toString());
    }

    static List<String> decodeFlags(final int flags) {
        if (flags == 0) {
            return Collections.emptyList();
        }

        final List<String> list = new ArrayList<>();
        if ((flags & Pattern.UNIX_LINES) != 0) {
            list.add("Unix Lines");
        }
        if ((flags & Pattern.CASE_INSENSITIVE) != 0) {
            list.add("Case Insensitive");
        }
        if ((flags & Pattern.COMMENTS) != 0) {
            list.add("Comments");
        }
        if ((flags & Pattern.MULTILINE) != 0) {
            list.add("Multiline");
        }
        if ((flags & Pattern.LITERAL) != 0) {
            list.add("Literal");
        }
        if ((flags & Pattern.DOTALL) != 0) {
            list.add("Dotall");
        }
        if ((flags & Pattern.UNICODE_CASE) != 0) {
            list.add("Unicode-aware case folding");
        }
        if ((flags & Pattern.CANON_EQ) != 0) {
            list.add("Canonical Equivalence");
        }
        if ((flags & Pattern.UNICODE_CHARACTER_CLASS) != 0) {
            list.add("Unicode character class");
        }
        return list;
    }

    public Pattern getPattern() {
        return pattern;
    }

    @Override
    public Regex newInstance(final VarMap varMap) {
        return new Regex(varMap, this);
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.REGEX;
    }
}
