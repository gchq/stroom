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

import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.ExternalFile;
import stroom.entity.shared.SQLNameConstants;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;
import javax.xml.bind.annotation.XmlTransient;

@Entity
@Table(name = "DASH")
public class Dashboard extends DocumentEntity {
    public static final String ENTITY_TYPE = "Dashboard";
    public static final String TABLE_NAME = SQLNameConstants.DASHBOARD;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String DOWNLOAD_SEARCH_RESULTS_PERMISSION = "Download Search Results";

    private static final long serialVersionUID = 3598996730392094523L;

    private String data;
    private DashboardConfig dashboardData;

    public Dashboard() {
        // Default constructor necessary for GWT serialisation.
    }

    public static final Dashboard createStub(final long pk) {
        final Dashboard dashboard = new Dashboard();
        dashboard.setStub(pk);
        return dashboard;
    }

    @Lob
    @Column(name = SQLNameConstants.DATA, length = Integer.MAX_VALUE)
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

    @Override
    protected void toString(final StringBuilder sb) {
        super.toString(sb);
        if (data != null) {
            sb.append(data);
        }
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }
}
