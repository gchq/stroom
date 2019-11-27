/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.shared;

import stroom.query.api.v2.DocRef;
import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;
import stroom.util.shared.ToStringBuilder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "table", propOrder = {"queryId", "fields", "extractValues", "extractionPipeline", "maxResults",
        "showDetail"})
public class TableComponentSettings extends ComponentSettings {
    public static final int[] DEFAULT_MAX_RESULTS = {1000000};
    private static final long serialVersionUID = -2530827581046882396L;
    @XmlElement(name = "queryId")
    private String queryId;
    @XmlElementWrapper(name = "fields")
    @XmlElements({@XmlElement(name = "field", type = Field.class)})
    private List<Field> fields;
    @XmlElement(name = "extractValues")
    private Boolean extractValues;
    @XmlElement(name = "extractionPipeline")
    private DocRef extractionPipeline;
    @XmlElementWrapper(name = "maxResults")
    @XmlElement(name = "level")
    private int[] maxResults = DEFAULT_MAX_RESULTS;
    @XmlElement(name = "showDetail")
    private Boolean showDetail;

    public TableComponentSettings() {
        // Default constructor necessary for GWT serialisation.
    }

    public TableComponentSettings(final List<Field> fields) {
        this.fields = fields;
    }

    public TableComponentSettings(final String queryId, final List<Field> fields, final Boolean extractValues, final DocRef extractionPipeline, final int[] maxResults, final Boolean showDetail) {
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

    public void setQueryId(final String queryId) {
        this.queryId = queryId;
    }

    public List<Field> getFields() {
        return fields;
    }

    public void setFields(final List<Field> fields) {
        this.fields = fields;
    }

    public void addField(final Field field) {
        if (fields == null) {
            fields = new ArrayList<>();
        }

        Objects.requireNonNull(field.getId(), "Field id is null");

        fields.add(field);
    }

    public void removeField(final Field field) {
        if (fields != null) {
            fields.remove(field);
        }
    }

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

    public DocRef getExtractionPipeline() {
        return extractionPipeline;
    }

    public void setExtractionPipeline(final DocRef extractionPipeline) {
        this.extractionPipeline = extractionPipeline;
    }

    public int[] getMaxResults() {
        if (maxResults == null || maxResults.length == 0) {
            return DEFAULT_MAX_RESULTS;
        }

        return maxResults;
    }

    public void setMaxResults(final int[] maxResults) {
        if (maxResults == null || maxResults.length == 0) {
            this.maxResults = DEFAULT_MAX_RESULTS;
        } else {
            this.maxResults = maxResults;
        }
    }

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
        if (o == this) {
            return true;
        } else if (!(o instanceof TableComponentSettings)) {
            return false;
        }

        final TableComponentSettings tableSettings = (TableComponentSettings) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(queryId, tableSettings.queryId);
        builder.append(fields, tableSettings.fields);
        builder.append(extractValues, tableSettings.extractValues);
        builder.append(extractionPipeline, tableSettings.extractionPipeline);
        builder.append(maxResults, tableSettings.maxResults);
        builder.append(showDetail, tableSettings.showDetail);
        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(queryId);
        builder.append(fields);
        builder.append(extractValues);
        builder.append(extractionPipeline);
        builder.append(maxResults);
        builder.append(showDetail);
        return builder.toHashCode();
    }

    @Override
    public String toString() {
        final ToStringBuilder builder = new ToStringBuilder();
        builder.append("queryId", queryId);
        builder.append("fields", fields);
        builder.append("extractValues", extractValues);
        builder.append("extractionPipeline", extractionPipeline);
        builder.append("maxResults", Arrays.toString(maxResults));
        builder.append("showDetail", showDetail);
        return builder.toString();
    }

    public TableComponentSettings copy() {
        List<Field> fieldsCopy = null;
        if (fields != null) {
            fieldsCopy = new ArrayList<>(fields.size());
            for (final Field field : fields) {
                fieldsCopy.add(field.copy());
            }
        }

        int[] maxResultCopy = null;
        if (maxResults != null) {
            maxResultCopy = new int[maxResults.length];
            for (int i = 0; i < maxResults.length; i++) {
                maxResultCopy[i] = maxResults[i];
            }
        }

        return new TableComponentSettings(queryId, fieldsCopy, extractValues, extractionPipeline, maxResultCopy, showDetail);
    }
}
