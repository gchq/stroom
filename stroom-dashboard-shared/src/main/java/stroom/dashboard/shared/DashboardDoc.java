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

package stroom.dashboard.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docstore.shared.Doc;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser"})
@XmlRootElement(name = "dashboard")
@XmlType(name = "DashboardDoc", propOrder = {"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser"})
public class DashboardDoc extends Doc {
    private static final long serialVersionUID = 3598996730392094523L;

    public static final String DOCUMENT_TYPE = "Dashboard";

    @XmlTransient
    @JsonIgnore
    private DashboardConfig dashboardConfig;

    public DashboardDoc() {
        // Default constructor necessary for GWT serialisation.
    }

    public DashboardConfig getDashboardConfig() {
        return dashboardConfig;
    }

    public void setDashboardConfig(final DashboardConfig dashboardConfig) {
        this.dashboardConfig = dashboardConfig;
    }
}
