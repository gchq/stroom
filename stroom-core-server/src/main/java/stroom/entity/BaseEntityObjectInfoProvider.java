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
 *
 */

package stroom.entity;

import event.logging.BaseObject;
import event.logging.Object;
import stroom.entity.shared.BaseEntity;
import stroom.util.shared.Document;
import stroom.entity.shared.NamedEntity;
import stroom.event.logging.api.ObjectInfoProvider;

class BaseEntityObjectInfoProvider implements ObjectInfoProvider {
    @Override
    public BaseObject createBaseObject(final java.lang.Object obj) {
        final BaseEntity entity = (BaseEntity) obj;

        final String type = getObjectType(entity);
        final String id = getId(entity);
        String name = null;
        String description = null;

        // Add name.
        if (entity instanceof NamedEntity) {
            final NamedEntity namedEntity = (NamedEntity) entity;
            name = namedEntity.getName();
        }

        final Object object = new Object();
        object.setType(type);
        object.setId(id);
        object.setName(name);
        object.setDescription(description);

        return object;
    }

    private String getId(final BaseEntity entity) {
        if (entity == null) {
            return null;
        }

        if (entity instanceof Document) {
            return ((Document) entity).getUuid();
        }

        return String.valueOf(entity.getId());
    }

    @Override
    public String getObjectType(final java.lang.Object object) {
        return object.getClass().getSimpleName();
    }
}
