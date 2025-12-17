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

import java.util.Set;

public abstract class ExpressionFactory extends NodeFactory {
    private final int minMatch;
    private final int maxMatch;
    private final Set<Integer> onlyMatch;
    private final int advance;

    public ExpressionFactory(final NodeFactory parent, final String id, final int minMatch, final int maxMatch,
                             final Set<Integer> onlyMatch, final int advance) {
        super(parent, id);
        this.minMatch = minMatch;
        this.maxMatch = maxMatch;
        this.onlyMatch = onlyMatch;
        this.advance = advance;
    }

    public int getMinMatch() {
        return minMatch;
    }

    public int getMaxMatch() {
        return maxMatch;
    }

    public Set<Integer> getOnlyMatch() {
        return onlyMatch;
    }

    public int getAdvance() {
        return advance;
    }

    void setAttributes(final String attributes) {
        final StringBuilder sb = new StringBuilder();
        if (minMatch > 0) {
            sb.append(" minMatch=\"");
            sb.append(minMatch);
            sb.append("\"");
        }
        if (maxMatch > 0) {
            sb.append(" maxMatch=\"");
            sb.append(maxMatch);
            sb.append("\"");
        }
        if (onlyMatch != null) {
            sb.append(" onlyMatch=\"");
            for (final int match : onlyMatch) {
                sb.append(match);
                sb.append(",");
            }
            sb.setLength(sb.length() - 1);
            sb.append("\"");
        }
        sb.append(attributes);

        super.setAttributes(sb);
    }
}
