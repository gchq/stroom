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

package stroom.entity.server;

import stroom.entity.shared.BaseEntity;

public interface GenericEntityMarshaller {
    <E extends BaseEntity> E marshal(String entityType, E entity);

    <E extends BaseEntity> E unmarshal(String entityType, E entity);

    <E extends BaseEntity> Object getObject(String entityType, E entity);

    <E extends BaseEntity> void setObject(String entityType, E entity, Object object);
}
