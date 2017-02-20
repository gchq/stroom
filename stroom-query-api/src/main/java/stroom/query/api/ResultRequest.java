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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

@XmlType(name = "ResultRequest", propOrder = {"componentId", "tableSettings", "requestedRange", "openGroups", "resultStyle", "fetchData"})
@XmlAccessorType(XmlAccessType.FIELD)
public class ResultRequest implements Serializable {
    private static final long serialVersionUID = -7455554742243923562L;

    @XmlElement
    private String componentId;
    @XmlElementWrapper(name = "mappings")
    @XmlElement(name = "tableSettings")
    private List<TableSettings> tableSettings;
    @XmlElement
    private OffsetRange requestedRange;
    @XmlElementWrapper(name = "openGroups")
    @XmlElement(name = "key")
    private List<String> openGroups;
    @XmlElement
    private ResultStyle resultStyle;
    @XmlElement
    private Boolean fetchData;

    public ResultRequest() {
    }

    public ResultRequest(final String componentId) {
        this.componentId = componentId;
    }

    public ResultRequest(final String componentId, final TableSettings tableSettings) {
        this(componentId, Collections.singletonList(tableSettings), null);
    }

    public ResultRequest(final String componentId, final TableSettings tableSettings, final OffsetRange requestedRange) {
        this(componentId, Collections.singletonList(tableSettings), requestedRange);
    }

    public ResultRequest(final String componentId, final List<TableSettings> tableSettings, final OffsetRange requestedRange) {
        this.componentId = componentId;
        this.tableSettings = tableSettings;
        this.requestedRange = requestedRange;
    }

    public String getComponentId() {
        return componentId;
    }

    public void setComponentId(final String componentId) {
        this.componentId = componentId;
    }

    public List<TableSettings> getTableSettings() {
        return tableSettings;
    }

    public void setTableSettings(final List<TableSettings> tableSettings) {
        this.tableSettings = tableSettings;
    }

    public OffsetRange getRequestedRange() {
        return requestedRange;
    }

    public void setRequestedRange(final OffsetRange requestedRange) {
        this.requestedRange = requestedRange;
    }

    public void setRange(final int offset, final int length) {
        requestedRange = new OffsetRange(offset, length);
    }

    public List<String> getOpenGroups() {
        return openGroups;
    }

    public void setOpenGroups(final List<String> openGroups) {
        this.openGroups = openGroups;
    }

    public ResultStyle getResultStyle() {
        return resultStyle;
    }

    public void setResultStyle(final ResultStyle resultStyle) {
        this.resultStyle = resultStyle;
    }

    public Boolean getFetchData() {
        return fetchData;
    }

    public void setFetchData(final Boolean fetchData) {
        this.fetchData = fetchData;
    }

    public boolean fetchData() {
        return fetchData == null || fetchData;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final ResultRequest that = (ResultRequest) o;

        if (componentId != null ? !componentId.equals(that.componentId) : that.componentId != null) return false;
        if (tableSettings != null ? !tableSettings.equals(that.tableSettings) : that.tableSettings != null)
            return false;
        if (requestedRange != null ? !requestedRange.equals(that.requestedRange) : that.requestedRange != null)
            return false;
        if (openGroups != null ? !openGroups.equals(that.openGroups) : that.openGroups != null) return false;
        if (resultStyle != that.resultStyle) return false;
        return fetchData != null ? fetchData.equals(that.fetchData) : that.fetchData == null;
    }

    @Override
    public int hashCode() {
        int result = componentId != null ? componentId.hashCode() : 0;
        result = 31 * result + (tableSettings != null ? tableSettings.hashCode() : 0);
        result = 31 * result + (requestedRange != null ? requestedRange.hashCode() : 0);
        result = 31 * result + (openGroups != null ? openGroups.hashCode() : 0);
        result = 31 * result + (resultStyle != null ? resultStyle.hashCode() : 0);
        result = 31 * result + (fetchData != null ? fetchData.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ResultRequest{" +
                "componentId='" + componentId + '\'' +
                ", tableSettings=" + tableSettings +
                ", requestedRange=" + requestedRange +
                ", openGroups=" + openGroups +
                ", resultStyle=" + resultStyle +
                ", fetchData=" + fetchData +
                '}';
    }

    public enum ResultStyle {
        FLAT, TABLE
    }
}