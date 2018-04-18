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

package stroom.dashboard;

import stroom.dashboard.shared.QueryEntity;
import stroom.entity.EntityMarshaller;
import stroom.query.api.v2.Query;

class QueryEntityMarshaller extends EntityMarshaller<QueryEntity, Query> {
    QueryEntityMarshaller() {
    }

    @Override
    public Query getObject(final QueryEntity entity) {
        return entity.getQuery();
    }

    @Override
    public void setObject(final QueryEntity entity, final Query object) {
        entity.setQuery(object);
    }

    @Override
    protected String getData(final QueryEntity entity) {
        return entity.getData();
    }

    @Override
    protected void setData(final QueryEntity entity, final String data) {
        entity.setData(data);
    }

    @Override
    protected Class<Query> getObjectType() {
        return Query.class;
    }

    @Override
    public String getEntityType() {
        return QueryEntity.ENTITY_TYPE;
    }
}
