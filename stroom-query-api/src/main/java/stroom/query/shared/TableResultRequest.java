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

package stroom.query.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;
import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;
import stroom.util.shared.OffsetRange;

import javax.xml.bind.annotation.*;
import java.util.HashSet;
import java.util.Set;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "tableResultRequest", propOrder = {"tableSettings", "requestedRange", "openGroups"})
public class TableResultRequest extends ComponentResultRequest {
    private static final long serialVersionUID = 8683770109061652092L;

    @XmlElement
    private TableSettings tableSettings;

    @XmlElement
    private OffsetRange<Integer> requestedRange = new OffsetRange<Integer>(0, 100);

    @XmlElement
    private Set<String> openGroups;

    public TableResultRequest() {
        // Default constructor necessary for GWT serialisation.
    }

    public TableResultRequest(final int offset, final int length) {
        requestedRange = new OffsetRange<Integer>(offset, length);
    }

    public TableSettings getTableSettings() {
        return tableSettings;
    }

    public void setTableSettings(final TableSettings tableSettings) {
        this.tableSettings = tableSettings;
    }

    public OffsetRange<Integer> getRequestedRange() {
        return requestedRange;
    }

    public Set<String> getOpenGroups() {
        return openGroups;
    }

    public void setOpenGroups(final Set<String> openGroups) {
        this.openGroups = openGroups;
    }

    public void setGroupOpen(final String group, final boolean open) {
        if (openGroups == null) {
            openGroups = new HashSet<String>();
        }

        if (open) {
            openGroups.add(group);
        } else {
            openGroups.remove(group);
        }
    }

    public void setRange(final int offset, final int length) {
        requestedRange = new OffsetRange<Integer>(offset, length);
    }

    public boolean isGroupOpen(final String group) {
        return openGroups != null && openGroups.contains(group);
    }

    @Override
    @JsonIgnore
    @XmlTransient
    public ComponentType getComponentType() {
        return ComponentType.TABLE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        TableResultRequest that = (TableResultRequest) o;

        return new EqualsBuilder()
                .append(tableSettings, that.tableSettings)
                .append(requestedRange, that.requestedRange)
                .append(openGroups, that.openGroups)
                .isEquals();
    }

    @Override
    public int hashCode() {
        HashCodeBuilder hashCodeBuilder = new HashCodeBuilder();
        hashCodeBuilder .append(tableSettings);
        hashCodeBuilder.append(requestedRange);
        hashCodeBuilder.append(openGroups);
        return hashCodeBuilder.toHashCode();
    }

    @Override
    public String toString() {
        return "TableResultRequest{" +
                "tableSettings=" + tableSettings +
                ", requestedRange=" + requestedRange +
                ", openGroups=" + openGroups +
                '}';
    }
}
