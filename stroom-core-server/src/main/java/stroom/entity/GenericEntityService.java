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

import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Entity;

import java.util.Set;

public interface GenericEntityService {
    <E extends Entity> E load(E entity);

    <E extends Entity> E load(E entity, Set<String> fetchSet);

    <E extends Entity> E loadById(String entityType, long id);

    <E extends Entity> E loadById(String entityType, long id, Set<String> fetchSet);

    <E extends Entity> E loadByUuid(String entityType, String uuid);

    <E extends Entity> E loadByUuid(String entityType, String uuid, Set<String> fetchSet);

//    <E extends Entity> E loadByName(String entityType, String name);
//
//    <E extends Entity> E loadByName(String entityType, DocRef folder, String name);
//
//    <E extends Entity> E loadByName(String entityType, String name, Set<String> fetchSet);
//
//    <E extends Entity> E loadByName(String entityType, DocRef folder, String name, Set<String> fetchSet);

    <E extends Entity, C extends BaseCriteria> BaseResultList<E> find(String entityType, C criteria);

//    <E extends DocumentEntity> List<E> findByFolder(String entityType, DocRef folder, Set<String> fetchSet);

    <E extends Entity> E save(E entity);

//    <E extends DocumentEntity> E saveAs(E entity, DocRef folder, String name);

    <E extends Entity> Boolean delete(E entity);

//    Collection<DocumentService<?>> findAll();
//
//    <E extends DocumentEntity> E importEntity(E entity, DocRef folder);
//
//    <E extends DocumentEntity> E exportEntity(E entity);

    <E extends Entity> EntityService<E> getEntityService(String entityType);
//
//    <E extends Entity, C extends BaseCriteria> FindService<E, C> getFindService(String entityType);
}
