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

package stroom.statistics.stroomstats.entity;

import stroom.entity.EntityMarshaller;
import stroom.stats.shared.StroomStatsStoreEntity;
import stroom.stats.shared.StroomStatsStoreEntityData;

class StroomStatsStoreEntityMarshaller extends EntityMarshaller<StroomStatsStoreEntity, StroomStatsStoreEntityData> {
    StroomStatsStoreEntityMarshaller() {
    }

    @Override
    public StroomStatsStoreEntityData getObject(final StroomStatsStoreEntity entity) {
        return entity.getDataObject();
    }

    @Override
    public void setObject(final StroomStatsStoreEntity entity, final StroomStatsStoreEntityData object) {
        entity.setDataObject(object);
    }

    @Override
    protected String getData(final StroomStatsStoreEntity entity) {
        return entity.getData();
    }

    @Override
    protected void setData(final StroomStatsStoreEntity entity, final String data) {
        entity.setData(data);
    }

    @Override
    protected Class<StroomStatsStoreEntityData> getObjectType() {
        return StroomStatsStoreEntityData.class;
    }

    @Override
    protected String getEntityType() {
        return StroomStatsStoreEntity.ENTITY_TYPE;
    }
}
