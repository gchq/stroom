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

import stroom.entity.server.util.SQLBuilder;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.shared.Entity;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.HasUuid;
import stroom.util.shared.HasId;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class EntityServiceHelper<E extends Entity> {
    protected final StroomEntityManager entityManager;
    private final Class<E> entityClass;
    private final QueryAppender queryAppender;
    private String entityType = null;


    public EntityServiceHelper(final StroomEntityManager entityManager, final Class<E> entityClass, final QueryAppender queryAppender) {
        this.entityManager = entityManager;
        this.entityClass = entityClass;
        this.queryAppender = queryAppender;
    }

    public E create(E entity) throws RuntimeException {
        if (entity.isPersistent()) {
            throw new EntityServiceException("Entity is already persistent");
        }

        if (entity != null) {
            queryAppender.preSave(entity);
        }

        entity = entityManager.saveEntity(entity);

        if (entity != null) {
            queryAppender.postLoad(entity);
        }

        return entity;
    }

    public E load(final E entity) throws RuntimeException {
        return load(entity, Collections.emptySet());
    }

    public E load(final E entity, final Set<String> fetchSet) throws RuntimeException {
        if (entity == null) {
            return null;
        }

        if (entity instanceof HasId) {
            return loadById(((HasId)entity).getId(), fetchSet);
        }
        if (entity instanceof HasUuid) {
            return loadByUuid(((HasUuid) entity).getUuid(), fetchSet);
        }

        throw new RuntimeException("Entity does not have an id or uuid");
    }

    public E loadById(final long id) throws RuntimeException {
        return loadById(id, Collections.emptySet());
    }

    @SuppressWarnings("unchecked")
    public E loadById(final long id, final Set<String> fetchSet) throws RuntimeException {
        E entity = null;

        final SQLBuilder sql = new SQLBuilder();
        sql.append("SELECT e");
        sql.append(" FROM ");
        sql.append(entityClass.getName());
        sql.append(" AS e");

        queryAppender.appendBasicJoin(sql, "e", fetchSet);

        sql.append(" WHERE e.id = ");
        sql.arg(id);

        final List<E> resultList = getEntityManager().executeQueryResultList(sql);
        if (resultList != null && resultList.size() > 0) {
            entity = resultList.get(0);
        }

        if (entity != null) {
            queryAppender.postLoad(entity);
        }

        return entity;
    }

    public E loadByUuid(final String uuid) throws RuntimeException {
        return loadByUuid(uuid, Collections.emptySet());
    }

    @SuppressWarnings("unchecked")
    public E loadByUuid(final String uuid, final Set<String> fetchSet) throws RuntimeException {
        E entity = null;

        final SQLBuilder sql = new SQLBuilder();
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

        if (entity != null) {
            queryAppender.postLoad(entity);
        }

        return entity;
    }

    public E save(E entity) throws RuntimeException {
        if (entity != null) {
            queryAppender.preSave(entity);
        }

        entity = entityManager.saveEntity(entity);

        if (entity != null) {
            queryAppender.postLoad(entity);
        }

        return entity;
    }

//    @Transactional(readOnly = true)
//    public BaseResultList<E> find(final C criteria) throws RuntimeException {
//        return doBasicFind(criteria);
//    }
//

//
//    protected void appendBasicCriteria(final SQLBuilder sql, final String alias, final C criteria) {
//        if (criteria instanceof FindDocumentEntityCriteria) {
//            final FindDocumentEntityCriteria findGroupedEntityCriteria = (FindDocumentEntityCriteria) criteria;
//            if (findGroupedEntityCriteria instanceof FindFolderCriteria) {
//                final FindFolderCriteria findFolderCriteria = (FindFolderCriteria) findGroupedEntityCriteria;
//                if (findFolderCriteria.isSelf()) {
//                    SQLUtil.appendSetQuery(sql, true, alias + ".id", findFolderCriteria.getFolderIdSet());
//                } else {
//                    UserManagerQueryUtil.appendFolderCriteria(findGroupedEntityCriteria.getFolderIdSet(),
//                            alias + ".folder", sql, true, getEntityManager());
//                }
//
//            } else {
//                UserManagerQueryUtil.appendFolderCriteria(findGroupedEntityCriteria.getFolderIdSet(), alias + ".folder",
//                        sql, true, getEntityManager());
//            }
//        }
//
//        if (criteria instanceof FindNamedEntityCriteria) {
//            final FindNamedEntityCriteria findNamedEntityCriteria = (FindNamedEntityCriteria) criteria;
//            SQLUtil.appendValueQuery(sql, alias + ".name", findNamedEntityCriteria.getName());
//        }
//
//    }
//
//    protected BaseResultList<E> doBasicFind(final C criteria) throws RuntimeException {
//        return doBasicFind(criteria, "e");
//    }
//
//    @SuppressWarnings("unchecked")
//    protected BaseResultList<E> doBasicFind(final C criteria, final String alias) throws RuntimeException {
//        final SQLBuilder sql = new SQLBuilder();
//        sql.append("SELECT ");
//        sql.append(alias);
//        sql.append(" FROM ");
//        sql.append(entityClass.getName());
//        sql.append(" AS ");
//        sql.append(alias);
//
//        appendBasicJoin(sql, alias, criteria.getFetchSet());
//
//        sql.append(" WHERE 1=1");
//
//        appendBasicCriteria(sql, alias, criteria);
//
//        // Append order by criteria.
//        SQLUtil.appendOrderBy(sql, true, criteria, alias);
//
//        final List<E> results = postLoad(criteria, getEntityManager().executeQueryResultList(sql, criteria));
//
//        results.forEach(this::postLoad);
//
//        return BaseResultList.createCriterialBasedList(results, criteria);
//    }

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
//
//    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final C criteria) {
//        CriteriaLoggingUtil.appendPageRequest(items, criteria.getPageRequest());
//    }
}
