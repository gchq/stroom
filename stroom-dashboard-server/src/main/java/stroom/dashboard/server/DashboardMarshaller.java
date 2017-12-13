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

package stroom.dashboard.server;

import stroom.dashboard.shared.Dashboard;
import stroom.dashboard.shared.DashboardConfig;
import stroom.entity.server.EntityMarshaller;

class DashboardMarshaller extends EntityMarshaller<Dashboard, DashboardConfig> {
    DashboardMarshaller() {
    }

    @Override
    public DashboardConfig getObject(final Dashboard entity) {
        return entity.getDashboardData();
    }

    @Override
    public void setObject(final Dashboard entity, final DashboardConfig object) {
        entity.setDashboardData(object);
    }

    @Override
    protected String getData(final Dashboard entity) {
        return entity.getData();
    }

    @Override
    protected void setData(final Dashboard entity, final String data) {
        entity.setData(data);
    }

    @Override
    protected Class<DashboardConfig> getObjectType() {
        return DashboardConfig.class;
    }

    @Override
    public String getEntityType() {
        return Dashboard.ENTITY_TYPE;
    }
}
