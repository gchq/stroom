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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TableResultRequest.class, name = "table"),
        @JsonSubTypes.Type(value = VisResultRequest.class, name = "vis")
})
@XmlType(name = "ResultRequest", propOrder = {"componentId", "fetchData"})
public abstract class ResultRequest implements Serializable {
    private static final long serialVersionUID = -7455554742243923562L;

    private String componentId;
    private Boolean fetchData;

    public ResultRequest() {
    }

    public ResultRequest(final String componentId) {
        this.componentId = componentId;
    }

    @XmlElement
    public String getComponentId() {
        return componentId;
    }

    public void setComponentId(final String componentId) {
        this.componentId = componentId;
    }

    @XmlElement
    public Boolean getFetchData() {
        return fetchData;
    }

    public void setFetchData(final Boolean fetchData) {
        this.fetchData = fetchData;
    }

    public boolean fetchData() {
        return fetchData == null || fetchData;
    }

    public abstract TableSettings getTableSettings();

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof ResultRequest)) return false;

        final ResultRequest that = (ResultRequest) o;

        return fetchData != null ? fetchData.equals(that.fetchData) : that.fetchData == null;
    }

    @Override
    public int hashCode() {
        return fetchData != null ? fetchData.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "ComponentResultRequest{" +
                "fetchData=" + fetchData +
                '}';
    }
}