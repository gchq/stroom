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

@JsonPropertyOrder({"componentId", "fetchData", "tableSettings", "requestedRange", "openGroups"})
@XmlType(name = "TableResultRequest", propOrder = {"tableSettings", "requestedRange", "openGroups"})
public class TableResultRequest extends ResultRequest {
    private static final long serialVersionUID = 8683770109061652092L;

    private TableSettings tableSettings;
    private OffsetRange requestedRange;
    private String[] openGroups;

    public TableResultRequest() {
    }

    public TableResultRequest(final String componentId, final TableSettings tableSettings, final int offset, final int length) {
        super(componentId);
        this.tableSettings = tableSettings;
        requestedRange = new OffsetRange(offset, length);
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
    public OffsetRange getRequestedRange() {
        return requestedRange;
    }

    public void setRequestedRange(final OffsetRange requestedRange) {
        this.requestedRange = requestedRange;
    }

    public void setRange(final int offset, final int length) {
        requestedRange = new OffsetRange(offset, length);
    }

    @XmlElementWrapper(name = "openGroups")
    @XmlElement(name = "key")
    public String[] getOpenGroups() {
        return openGroups;
    }

    public void setOpenGroups(final String[] openGroups) {
        this.openGroups = openGroups;
    }

//    public void setGroupOpen(final String group, final boolean open) {
//        if (openGroups == null) {
//            openGroups = new HashSet<>();
//        }
//
//        if (open) {
//            openGroups.add(group);
//        } else {
//            openGroups.remove(group);
//        }
//    }
//
//    public boolean isGroupOpen(final String group) {
//        return openGroups != null && openGroups.contains(group);
//    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        final TableResultRequest that = (TableResultRequest) o;

        if (tableSettings != null ? !tableSettings.equals(that.tableSettings) : that.tableSettings != null)
            return false;
        if (requestedRange != null ? !requestedRange.equals(that.requestedRange) : that.requestedRange != null)
            return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        return Arrays.equals(openGroups, that.openGroups);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (tableSettings != null ? tableSettings.hashCode() : 0);
        result = 31 * result + (requestedRange != null ? requestedRange.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(openGroups);
        return result;
    }

    @Override
    public String toString() {
        return "TableResultRequest{" +
                "tableSettings=" + tableSettings +
                ", requestedRange=" + requestedRange +
                ", openGroups=" + Arrays.toString(openGroups) +
                '}';
    }
}
