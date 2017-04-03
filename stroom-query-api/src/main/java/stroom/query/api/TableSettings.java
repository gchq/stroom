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

package stroom.query.api;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.List;

@JsonPropertyOrder({"queryId", "fields", "extractValues", "extractionPipeline", "maxResults",
        "showDetail"})
@XmlType(name = "TableSettings", propOrder = {"queryId", "fields", "extractValues", "extractionPipeline", "maxResults", "showDetail"})
@XmlAccessorType(XmlAccessType.FIELD)
public final class TableSettings implements Serializable {
    private static final long serialVersionUID = -2530827581046882396L;

    @XmlElement
    private String queryId;
    @XmlElementWrapper(name = "fields")
    @XmlElement(name = "field")
    private List<Field> fields;
    @XmlElement
    private Boolean extractValues;
    @XmlElement
    private DocRef extractionPipeline;
    @XmlElementWrapper(name = "maxResults")
    @XmlElement(name = "val")
    private List<Integer> maxResults;
    @XmlElement
    private Boolean showDetail;

    private TableSettings() {
    }

    public TableSettings(final String queryId, final List<Field> fields, final Boolean extractValues, final DocRef extractionPipeline, final List<Integer> maxResults, final Boolean showDetail) {
        this.queryId = queryId;
        this.fields = fields;
        this.extractValues = extractValues;
        this.extractionPipeline = extractionPipeline;
        this.maxResults = maxResults;
        this.showDetail = showDetail;
    }

    public String getQueryId() {
        return queryId;
    }

    public List<Field> getFields() {
        return fields;
    }

    public Boolean getExtractValues() {
        return extractValues;
    }

    public boolean extractValues() {
        if (extractValues == null) {
            return false;
        }
        return extractValues;
    }

    public DocRef getExtractionPipeline() {
        return extractionPipeline;
    }

    public List<Integer> getMaxResults() {
        return maxResults;
    }

    public Boolean getShowDetail() {
        return showDetail;
    }

    public boolean showDetail() {
        if (showDetail == null) {
            return false;
        }
        return showDetail;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final TableSettings that = (TableSettings) o;

        if (queryId != null ? !queryId.equals(that.queryId) : that.queryId != null) return false;
        if (fields != null ? !fields.equals(that.fields) : that.fields != null) return false;
        if (extractValues != null ? !extractValues.equals(that.extractValues) : that.extractValues != null)
            return false;
        if (extractionPipeline != null ? !extractionPipeline.equals(that.extractionPipeline) : that.extractionPipeline != null)
            return false;
        if (maxResults != null ? !maxResults.equals(that.maxResults) : that.maxResults != null) return false;
        return showDetail != null ? showDetail.equals(that.showDetail) : that.showDetail == null;
    }

    @Override
    public int hashCode() {
        int result = queryId != null ? queryId.hashCode() : 0;
        result = 31 * result + (fields != null ? fields.hashCode() : 0);
        result = 31 * result + (extractValues != null ? extractValues.hashCode() : 0);
        result = 31 * result + (extractionPipeline != null ? extractionPipeline.hashCode() : 0);
        result = 31 * result + (maxResults != null ? maxResults.hashCode() : 0);
        result = 31 * result + (showDetail != null ? showDetail.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "TableSettings{" +
                "queryId='" + queryId + '\'' +
                ", fields=" + fields +
                ", extractValues=" + extractValues +
                ", extractionPipeline=" + extractionPipeline +
                ", maxResults=" + maxResults +
                ", showDetail=" + showDetail +
                '}';
    }
}