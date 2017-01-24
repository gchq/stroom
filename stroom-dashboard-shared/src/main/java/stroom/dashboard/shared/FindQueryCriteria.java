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

import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.FindDocumentEntityCriteria;
import stroom.entity.shared.OrderBy;
import stroom.entity.shared.SQLNameConstants;
import stroom.entity.shared.StringCriteria;

public class FindQueryCriteria extends FindDocumentEntityCriteria {
    public static final OrderBy ORDER_BY_TIME = new OrderBy("Time", "createTime", QueryEntity.CREATE_TIME);
    public static final OrderBy ORDER_BY_NAME = new OrderBy("Name", "name", SQLNameConstants.NAME);
    private static final long serialVersionUID = -4421720204507720754L;
    private EntityIdSet<Dashboard> dashboardIdSet;
    private StringCriteria nameCriteria;

    public FindQueryCriteria() {
        // Default constructor necessary for GWT serialisation.
    }

    public FindQueryCriteria(final String name) {
        super(name);
    }

    public EntityIdSet<Dashboard> getDashboardIdSet() {
        return dashboardIdSet;
    }

    public void setDashboardIdSet(final EntityIdSet<Dashboard> dashboardIdSet) {
        this.dashboardIdSet = dashboardIdSet;
    }

    public EntityIdSet<Dashboard> obtainDashboardIdSet() {
        if (dashboardIdSet == null) {
            dashboardIdSet = new EntityIdSet<Dashboard>();
        }

        return dashboardIdSet;
    }

    public StringCriteria getNameCriteria() {
        return nameCriteria;
    }

    public void setNameCriteria(final StringCriteria nameCriteria) {
        this.nameCriteria = nameCriteria;
    }
}
