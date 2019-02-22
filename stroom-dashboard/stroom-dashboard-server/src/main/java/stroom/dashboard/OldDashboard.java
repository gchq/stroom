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

package stroom.dashboard;

import stroom.dashboard.shared.DashboardConfig;
import stroom.importexport.shared.ExternalFile;
import stroom.importexport.migration.DocumentEntity;

import javax.persistence.Transient;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Used for legacy migration
 **/
@Deprecated
public class OldDashboard extends DocumentEntity {
    private static final String ENTITY_TYPE = "Dashboard";

    private String data;
    private DashboardConfig dashboardData;

    public OldDashboard() {
        // Default constructor necessary for GWT serialisation.
    }

    @ExternalFile
    public String getData() {
        return data;
    }

    public void setData(final String data) {
        this.data = data;
    }

    @Transient
    @XmlTransient
    public DashboardConfig getDashboardData() {
        return dashboardData;
    }

    public void setDashboardData(final DashboardConfig dashboardData) {
        this.dashboardData = dashboardData;
    }

    @Transient
    public final String getType() {
        return ENTITY_TYPE;
    }
}
