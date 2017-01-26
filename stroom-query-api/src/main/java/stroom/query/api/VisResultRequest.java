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
import java.util.Arrays;
import java.util.Map;

@JsonPropertyOrder({"componentId", "fetchData", "tableSettings", "structure", "params"})
@XmlType(name = "VisResultRequest", propOrder = {"tableSettings", "structure", "params"})
public class VisResultRequest extends ResultRequest {
    static final long serialVersionUID = 8683770109061652092L;

    private TableSettings tableSettings;
    private VisStructure structure;
    private Param[] params;

    public VisResultRequest() {
    }

    public VisResultRequest(final String componentId) {
        super(componentId);
    }

    @XmlElement
    @Override
    public TableSettings getTableSettings() {
        return tableSettings;
    }

    public void setTableSettings(final TableSettings tableSettings) {
        this.tableSettings = tableSettings;
    }

    @XmlElement
    public VisStructure getStructure() {
        return structure;
    }

    public void setStructure(final VisStructure structure) {
        this.structure = structure;
    }

    @XmlElementWrapper(name = "params")
    @XmlElement(name = "param")
    public Param[] getParams() {
        return params;
    }

    public void setParams(final Param[] params) {
        this.params = params;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        final VisResultRequest that = (VisResultRequest) o;

        if (tableSettings != null ? !tableSettings.equals(that.tableSettings) : that.tableSettings != null)
            return false;
        if (structure != null ? !structure.equals(that.structure) : that.structure != null) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        return Arrays.equals(params, that.params);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (tableSettings != null ? tableSettings.hashCode() : 0);
        result = 31 * result + (structure != null ? structure.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(params);
        return result;
    }

    @Override
    public String toString() {
        return "VisResultRequest{" +
                "tableSettings=" + tableSettings +
                ", structure=" + structure +
                ", params=" + Arrays.toString(params) +
                '}';
    }
}