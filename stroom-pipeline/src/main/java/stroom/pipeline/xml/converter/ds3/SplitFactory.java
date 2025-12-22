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

import org.apache.commons.text.StringEscapeUtils;

import java.util.Set;

public class SplitFactory extends ExpressionFactory {
    private final char[] delimiter;
    private final char[] escape;
    private final char[] containerStart;
    private final char[] containerEnd;

    public SplitFactory(final NodeFactory parent, final String id, final String delimiter) {
        this(
                parent,
                id,
                0,
                -1,
                null,
                delimiter,
                null,
                null,
                null);
    }

    public SplitFactory(final NodeFactory parent,
                        final String id,
                        final int minMatch,
                        final int maxMatch,
                        final Set<Integer> onlyMatch,
                        final String delimiter,
                        final String escape,
                        final String containerStart,
                        final String containerEnd) {
        super(parent, id, minMatch, maxMatch, onlyMatch, -1);
        if (delimiter != null) {
            this.delimiter = delimiter.toCharArray();
        } else {
            this.delimiter = null;
        }
        if (escape != null) {
            this.escape = escape.toCharArray();
        } else {
            this.escape = null;
        }
        if (containerStart != null) {
            this.containerStart = containerStart.toCharArray();
        } else {
            this.containerStart = null;
        }
        if (containerEnd != null) {
            this.containerEnd = containerEnd.toCharArray();
        } else {
            this.containerEnd = null;
        }

        final StringBuilder sb = new StringBuilder();
        if (delimiter != null) {
            sb.append(" delimiter=\"");
            sb.append(StringEscapeUtils.escapeJava(delimiter));
            sb.append("\"");
        }
        if (escape != null) {
            sb.append(" escape=\"");
            sb.append(escape);
            sb.append("\"");
        }
        if (containerStart != null) {
            sb.append(" containerStart=\"");
            sb.append(containerStart);
            sb.append("\"");
        }
        if (containerEnd != null) {
            sb.append(" containerEnd=\"");
            sb.append(containerEnd);
            sb.append("\"");
        }
        setAttributes(sb.toString());
    }

    public char[] getDelimiter() {
        return delimiter;
    }

    public char[] getEscape() {
        return escape;
    }

    public char[] getContainerStart() {
        return containerStart;
    }

    public char[] getContainerEnd() {
        return containerEnd;
    }

    @Override
    public Split newInstance(final VarMap varMap) {
        return new Split(varMap, this);
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.SPLIT;
    }
}
