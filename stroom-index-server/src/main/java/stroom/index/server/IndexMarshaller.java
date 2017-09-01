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

package stroom.index.server;

import org.springframework.stereotype.Component;
import stroom.entity.server.EntityMarshaller;
import stroom.index.shared.Index;
import stroom.index.shared.IndexFields;

@Component
public class IndexMarshaller extends EntityMarshaller<Index, IndexFields> {
    @Override
    public IndexFields getObject(final Index entity) {
        return entity.getIndexFieldsObject();
    }

    @Override
    public void setObject(final Index entity, final IndexFields object) {
        entity.setIndexFieldsObject(object);
    }

    @Override
    protected String getData(final Index entity) {
        return entity.getIndexFields();
    }

    @Override
    protected void setData(final Index entity, final String data) {
        entity.setIndexFields(data);
    }

    @Override
    protected Class<IndexFields> getObjectType() {
        return IndexFields.class;
    }

    @Override
    public String getEntityType() {
        return Index.ENTITY_TYPE;
    }
}
