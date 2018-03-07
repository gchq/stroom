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

import event.logging.BaseAdvancedQueryItem;
import com.google.inject.persist.Transactional;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Entity;
import stroom.entity.util.FieldMap;
import stroom.security.Secured;
import stroom.security.shared.PermissionNames;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Secured(PermissionNames.ADMINISTRATOR)
// Ensures you must be an administrator to perform any action unless specifically overridden.
@Transactional
public abstract class SystemEntityServiceImpl<E extends Entity, C extends BaseCriteria> implements BaseEntityService<E>, FindService<E, C>, SupportsCriteriaLogging<C> {
    private final StroomEntityManager entityManager;
    private final QueryAppender<E, C> queryAppender;
    private final EntityServiceHelper<E> entityServiceHelper;
    private final FindServiceHelper<E, C> findServiceHelper;

    private String entityType;
    private FieldMap sqlFieldMap;

    protected SystemEntityServiceImpl(final StroomEntityManager entityManager) {
        this.entityManager = entityManager;
        this.queryAppender = createQueryAppender(entityManager);
        this.entityServiceHelper = new EntityServiceHelper<>(entityManager, getEntityClass());
        this.findServiceHelper = new FindServiceHelper<>(entityManager, getEntityClass(), queryAppender);
    }

//    @Secured(permission = DocumentPermissionNames.CREATE)
//    @Override
//    public E create(final E entity) throws RuntimeException {
//        return entityServiceHelper.create(entity);
//    }

    //    @Secured(permission = DocumentPermissionNames.READ)
    @Transactional
    @Override
    public E load(final E entity) throws RuntimeException {
        return entityServiceHelper.load(entity, Collections.emptySet(), queryAppender);
    }

    //    @Secured(permission = DocumentPermissionNames.READ)
    @Transactional
    @Override
    public E load(final E entity, final Set<String> fetchSet) throws RuntimeException {
        return entityServiceHelper.load(entity, fetchSet, queryAppender);
    }

    //    @Secured(permission = DocumentPermissionNames.READ)
    @Transactional
    @Override
    public E loadById(final long id) throws RuntimeException {
        return entityServiceHelper.loadById(id, Collections.emptySet(), queryAppender);
    }

    //    @Secured(permission = DocumentPermissionNames.READ)
    @Transactional
    @Override
    public E loadById(final long id, final Set<String> fetchSet) throws RuntimeException {
        return entityServiceHelper.loadById(id, fetchSet, queryAppender);
    }

//    @Transactional
//    @Override
//    public E loadByIdInsecure(final long id, final Set<String> fetchSet) throws RuntimeException {
//        return entityServiceHelper.loadById(id, Collections.emptySet(), queryAppender);
//    }

    //    @Secured(permission = DocumentPermissionNames.UPDATE)
    @Override
    public E save(final E entity) throws RuntimeException {
        return entityServiceHelper.save(entity, queryAppender);
    }

//    @Secured(permission = DocumentPermissionNames.USE)
//    @Override
//    public BaseResultList<E> find(final C criteria) throws RuntimeException {
//        return super.find(criteria);
//    }

    //    @Secured(permission = DocumentPermissionNames.DELETE)
    @Override
    public Boolean delete(final E entity) throws RuntimeException {
        return entityServiceHelper.delete(entity);
    }

    @Override
    public BaseResultList<E> find(C criteria) throws RuntimeException {
        return findServiceHelper.find(criteria, getSqlFieldMap());
    }

    public String getEntityType() {
        if (entityType == null) {
            try {
                entityType = getEntityClass().newInstance().getType();
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }
        return entityType;
    }

    @Override
    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final C criteria) {
        CriteriaLoggingUtil.appendPageRequest(items, criteria.getPageRequest());
    }

    public StroomEntityManager getEntityManager() {
        return entityManager;
    }

    protected QueryAppender<E, C> createQueryAppender(StroomEntityManager entityManager) {
        return new QueryAppender<>(entityManager);
    }

    protected final QueryAppender<E, C> getQueryAppender() {
        return queryAppender;
    }

    protected FieldMap createFieldMap() {
        return new FieldMap()
                .add(BaseCriteria.FIELD_ID, BaseEntity.ID, "id");
    }

    protected final FieldMap getSqlFieldMap() {
        if (sqlFieldMap == null) {
            sqlFieldMap = createFieldMap();
        }
        return sqlFieldMap;
    }
}
