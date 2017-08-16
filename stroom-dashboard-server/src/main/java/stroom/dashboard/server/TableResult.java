/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dashboard.server;

import stroom.query.api.v2.Row;
import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;
import stroom.util.shared.OffsetRange;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "tableResult", propOrder = {"rows", "resultRange", "totalResults", "error"})
@XmlRootElement(name = "tableResult")
public class TableResult implements ComponentResult {
    private static final long serialVersionUID = -2964122512841756795L;

    @XmlElement
    private List<Row> rows;

    @XmlElement
    private OffsetRange<Integer> resultRange;

    @XmlElement
    private Integer totalResults;

    @XmlElement
    private String error;

    public TableResult() {
        // Default constructor necessary for GWT serialisation.
    }

    public List<Row> getRows() {
        return rows;
    }

    public void setRows(final List<Row> rows) {
        this.rows = rows;
    }

    public OffsetRange<Integer> getResultRange() {
        return resultRange;
    }

    public void setResultRange(final OffsetRange<Integer> resultRange) {
        this.resultRange = resultRange;
    }

    public Integer getTotalResults() {
        return totalResults;
    }

    public void setTotalResults(final Integer totalResults) {
        this.totalResults = totalResults;
    }

    public String getError() {
        return error;
    }

    public void setError(final String error) {
        this.error = error;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof TableResult)) {
            return false;
        }

        final TableResult result = (TableResult) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(rows, result.rows);
        builder.append(resultRange, result.resultRange);
        builder.append(totalResults, result.totalResults);
        builder.append(error, result.error);
        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(rows);
        builder.append(resultRange);
        builder.append(totalResults);
        builder.append(error);
        return builder.toHashCode();
    }

    @Override
    public String toString() {
        if (rows == null) {
            return "";
        }

        return rows.size() + " rows";
    }
}
