/*
 * Copyright 2016 Crown Copyright
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

package stroom.util.shared;

import java.io.Serializable;

public class Highlight implements Serializable, Comparable<Highlight> {
    private static final long serialVersionUID = -2327935798577052482L;

    private int streamFrom;
    private int lineFrom;
    private int colFrom;
    private int streamTo;
    private int lineTo;
    private int colTo;

    public Highlight() {
        // Default constructor necessary for GWT serialisation.
    }

    public Highlight(final int streamFrom, final int lineFrom, final int colFrom, final int streamTo, final int lineTo,
                     final int colTo) {
        this.streamFrom = streamFrom;
        this.lineFrom = lineFrom;
        this.colFrom = colFrom;
        this.streamTo = streamTo;
        this.lineTo = lineTo;
        this.colTo = colTo;
    }

    public int getStreamFrom() {
        return streamFrom;
    }

    public int getLineFrom() {
        return lineFrom;
    }

    public int getColFrom() {
        return colFrom;
    }

    public int getStreamTo() {
        return streamTo;
    }

    public int getLineTo() {
        return lineTo;
    }

    public int getColTo() {
        return colTo;
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(streamFrom);
        builder.append(lineFrom);
        builder.append(colFrom);
        return builder.toHashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        } else if (o == null || !(o instanceof Highlight)) {
            return false;
        }

        final Highlight highlight = (Highlight) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(streamFrom, highlight.streamFrom);
        builder.append(lineFrom, highlight.lineFrom);
        builder.append(colFrom, highlight.colFrom);
        return builder.isEquals();
    }

    @Override
    public int compareTo(final Highlight o) {
        final CompareBuilder builder = new CompareBuilder();
        builder.append(streamFrom, o.streamFrom);
        builder.append(lineFrom, o.lineFrom);
        builder.append(colFrom, o.colFrom);
        return builder.toComparison();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("[");
        sb.append(streamFrom);
        sb.append(":");
        sb.append(lineFrom);
        sb.append(":");
        sb.append(colFrom);
        sb.append("][");
        sb.append(streamTo);
        sb.append(":");
        sb.append(lineTo);
        sb.append(":");
        sb.append(colTo);
        sb.append("]");
        return sb.toString();
    }
}
