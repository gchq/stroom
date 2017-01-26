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

package stroom.query.api;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@JsonPropertyOrder({"queryId", "fields", "extractValues", "extractionPipeline", "maxResults",
        "showDetail"})
@XmlType(name = "TableSettings", propOrder = {"queryId", "fields", "extractValues", "extractionPipeline", "maxResults", "showDetail"})
public class TableSettings implements Serializable {
    private static final long serialVersionUID = -2530827581046882396L;

    private String queryId;
    private List<Field> fields;
    private Boolean extractValues;
    private DocRef extractionPipeline;
    private Integer[] maxResults;
    private Boolean showDetail;

    public TableSettings() {
    }

    @XmlElement
    public String getQueryId() {
        return queryId;
    }

    public void setQueryId(final String queryId) {
        this.queryId = queryId;
    }

    @XmlElementWrapper(name = "fields")
    @XmlElement(name = "field")
    public Field[] getFields() {
        if (fields == null || fields.size() == 0) {
            return null;
        }
        return fields.toArray(new Field[fields.size()]);
    }

    public void setFields(final Field[] fields) {
        if (fields != null && fields.length > 0) {
            this.fields = new ArrayList<>(Arrays.asList(fields));
        } else {
            this.fields = null;
        }
    }

    public void addField(final Field field) {
        if (fields == null) {
            fields = new ArrayList<>();
        }
        fields.add(field);
    }

    public void addField(final int index, final Field field) {
        if (fields == null) {
            fields = new ArrayList<>();
        }
        fields.add(index, field);
    }

    public void removeField(final Field field) {
        if (fields != null) {
            fields.remove(field);
            if (fields.size() == 0) {
                fields = null;
            }
        }
    }

    @XmlElement
    public Boolean getExtractValues() {
        return extractValues;
    }

    public void setExtractValues(final Boolean extractValues) {
        if (extractValues != null && extractValues) {
            this.extractValues = null;
        } else {
            this.extractValues = Boolean.FALSE;
        }
    }

    public boolean extractValues() {
        if (extractValues == null) {
            return true;
        }
        return extractValues;
    }

    @XmlElement
    public DocRef getExtractionPipeline() {
        return extractionPipeline;
    }

    public void setExtractionPipeline(final DocRef extractionPipeline) {
        this.extractionPipeline = extractionPipeline;
    }

    @XmlElementWrapper(name = "maxResults")
    @XmlElement(name = "val")
    public Integer[] getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(final Integer[] maxResults) {
        this.maxResults = maxResults;
    }

    @XmlElement
    public Boolean getShowDetail() {
        return showDetail;
    }

    public void setShowDetail(final Boolean showDetail) {
        if (showDetail != null && showDetail) {
            this.showDetail = Boolean.TRUE;
        } else {
            this.showDetail = null;
        }
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
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(maxResults, that.maxResults)) return false;
        return showDetail != null ? showDetail.equals(that.showDetail) : that.showDetail == null;
    }

    @Override
    public int hashCode() {
        int result = queryId != null ? queryId.hashCode() : 0;
        result = 31 * result + (fields != null ? fields.hashCode() : 0);
        result = 31 * result + (extractValues != null ? extractValues.hashCode() : 0);
        result = 31 * result + (extractionPipeline != null ? extractionPipeline.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(maxResults);
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
                ", maxResults=" + Arrays.toString(maxResults) +
                ", showDetail=" + showDetail +
                '}';
    }
}
