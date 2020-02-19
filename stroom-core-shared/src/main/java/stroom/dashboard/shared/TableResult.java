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

package stroom.dashboard.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
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
@JsonInclude(Include.NON_DEFAULT)
public class TableResult implements ComponentResult {
    private static final long serialVersionUID = -2964122512841756795L;

    @XmlElement
    @JsonProperty
    private List<Field> fields;

    @XmlElement
    @JsonProperty
    private List<Row> rows;

    @XmlElement
    @JsonProperty
    private OffsetRange<Integer> resultRange;

    @XmlElement
    @JsonProperty
    private Integer totalResults;

    @XmlElement
    @JsonProperty
    private String error;

    public TableResult() {
        // Default constructor necessary for GWT serialisation.
    }

    @JsonCreator
    public TableResult(@JsonProperty("fields") final List<Field> fields,
                       @JsonProperty("rows") final List<Row> rows,
                       @JsonProperty("resultRange") final OffsetRange<Integer> resultRange,
                       @JsonProperty("totalResults") final Integer totalResults,
                       @JsonProperty("error") final String error) {
        this.fields = fields;
        this.rows = rows;
        this.resultRange = resultRange;
        this.totalResults = totalResults;
        this.error = error;
    }

    public List<Field> getFields() {
        return fields;
    }

    public void setFields(final List<Field> fields) {
        this.fields = fields;
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
