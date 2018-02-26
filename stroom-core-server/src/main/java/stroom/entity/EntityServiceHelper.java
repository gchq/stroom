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

package stroom.entity;

import stroom.entity.util.HqlBuilder;
import stroom.entity.shared.Entity;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.HasUuid;
import stroom.util.shared.HasId;

import java.util.List;
import java.util.Set;

public class EntityServiceHelper<E extends Entity> {
    protected final StroomEntityManager entityManager;
    private final Class<E> entityClass;
    //    private final QueryAppender<E, ?> queryAppender;
    private String entityType = null;


    public EntityServiceHelper(final StroomEntityManager entityManager, final Class<E> entityClass) {
        this.entityManager = entityManager;
        this.entityClass = entityClass;
//        this.queryAppender = queryAppender;
    }

//    public E create(E entity) throws RuntimeException {
//        return create(entity, queryAppender);
//    }

    public E create(E entity, final QueryAppender<E, ?> queryAppender) throws RuntimeException {
        if (entity == null) {
            throw new EntityServiceException("Entity is null");
        }

        if (entity.isPersistent()) {
            throw new EntityServiceException("Entity is already persistent");
        }

        if (queryAppender != null) {
            queryAppender.preSave(entity);
        }

        entity = entityManager.saveEntity(entity);

        if (entity != null && queryAppender != null) {
            queryAppender.postLoad(entity);
        }

        return entity;
    }

//    public E load(final E entity) throws RuntimeException {
//        return load(entity, Collections.emptySet());
//    }

    public E load(final E entity, final Set<String> fetchSet, final QueryAppender<E, ?> queryAppender) throws RuntimeException {
        if (entity == null) {
            return null;
        }

        if (entity instanceof HasId) {
            return loadById(((HasId) entity).getId(), fetchSet, queryAppender);
        }
        if (entity instanceof HasUuid) {
            return loadByUuid(((HasUuid) entity).getUuid(), fetchSet, queryAppender);
        }

        throw new RuntimeException("Entity does not have an id or uuid");
    }

//    public E loadById(final long id) throws RuntimeException {
//        return loadById(id, Collections.emptySet());
//    }
//
//    public E loadById(final long id, final Set<String> fetchSet) throws RuntimeException {
//        return loadById(id, fetchSet, queryAppender);
//    }

    @SuppressWarnings("unchecked")
    public E loadById(final long id, final Set<String> fetchSet, final QueryAppender<E, ?> queryAppender) throws RuntimeException {
        E entity = null;

        final HqlBuilder sql = new HqlBuilder();
        sql.append("SELECT e");
        sql.append(" FROM ");
        sql.append(entityClass.getName());
        sql.append(" AS e");

        if (queryAppender != null) {
            queryAppender.appendBasicJoin(sql, "e", fetchSet);
        }

        sql.append(" WHERE e.id = ");
        sql.arg(id);

        final List<E> resultList = getEntityManager().executeQueryResultList(sql);
        if (resultList != null && resultList.size() > 0) {
            entity = resultList.get(0);
        }

        if (entity != null && queryAppender != null) {
            queryAppender.postLoad(entity);
        }

        return entity;
    }

//    public E loadByUuid(final String uuid) throws RuntimeException {
//        return loadByUuid(uuid, Collections.emptySet());
//    }
//
//    public E loadByUuid(final String uuid, final Set<String> fetchSet) throws RuntimeException {
//        return loadByUuid(uuid, fetchSet, queryAppender);
//    }

    @SuppressWarnings("unchecked")
    public E loadByUuid(final String uuid, final Set<String> fetchSet, final QueryAppender<E, ?> queryAppender) throws RuntimeException {
        E entity = null;

        final HqlBuilder sql = new HqlBuilder();
        sql.append("SELECT e FROM ");
        sql.append(entityClass.getName());
        sql.append(" AS e");
//        queryAppender.appendBasicJoin(sql, "e", fetchSet);
        sql.append(" WHERE e.uuid = ");
        sql.arg(uuid);

        final List<E> resultList = getEntityManager().executeQueryResultList(sql);
        if (resultList != null && resultList.size() > 0) {
            entity = resultList.get(0);
        }

        if (entity != null && queryAppender != null) {
            queryAppender.postLoad(entity);
        }

        return entity;
    }

//    public E save(E entity) throws RuntimeException {
//        return save(entity, queryAppender);
//    }

    public E save(E entity, final QueryAppender<E, ?> queryAppender) throws RuntimeException {
        if (entity != null && queryAppender != null) {
            queryAppender.preSave(entity);
        }

        entity = entityManager.saveEntity(entity);

        if (entity != null && queryAppender != null) {
            queryAppender.postLoad(entity);
        }

        return entity;
    }

    public Boolean delete(final E entity) throws RuntimeException {
        return entityManager.deleteEntity(entity);
    }

    public StroomEntityManager getEntityManager() {
        return entityManager;
    }

    public String getEntityType() {
        if (entityType == null) {
            try {
                entityType = entityClass.newInstance().getType();
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }
        return entityType;
    }
}
