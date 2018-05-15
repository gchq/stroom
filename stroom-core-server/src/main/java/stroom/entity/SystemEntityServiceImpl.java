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
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Entity;
import stroom.entity.util.FieldMap;
import stroom.security.Security;
import stroom.security.shared.PermissionNames;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class SystemEntityServiceImpl<E extends Entity, C extends BaseCriteria>
        implements BaseEntityService<E>, FindService<E, C>, CountService<C>, SupportsCriteriaLogging<C> {
    private final StroomEntityManager entityManager;
    private final Security security;
    private final QueryAppender<E, C> queryAppender;
    private final EntityServiceHelper<E> entityServiceHelper;
    private final FindServiceHelper<E, C> findServiceHelper;
    private final CountServiceHelper<E, C> countServiceHelper;

    private String entityType;
    private FieldMap sqlFieldMap;

    protected SystemEntityServiceImpl(final StroomEntityManager entityManager,
                                      final Security security) {
        this.entityManager = entityManager;
        this.security = security;
        this.queryAppender = createQueryAppender(entityManager);
        this.entityServiceHelper = new EntityServiceHelper<>(entityManager, getEntityClass());
        this.findServiceHelper = new FindServiceHelper<>(entityManager, getEntityClass(), queryAppender);
        this.countServiceHelper = new CountServiceHelper<>(entityManager, getEntityClass(), queryAppender);
    }

    @Override
    public E load(final E entity) {
        return security.secureResult(permission(), () -> entityServiceHelper.load(entity, Collections.emptySet(), queryAppender));
    }

    @Override
    public E load(final E entity, final Set<String> fetchSet) {
        return security.secureResult(permission(), () -> entityServiceHelper.load(entity, fetchSet, queryAppender));
    }

    @Override
    public E loadById(final long id) {
        return security.secureResult(permission(), () -> entityServiceHelper.loadById(id, Collections.emptySet(), queryAppender));
    }

    @Override
    public E loadById(final long id, final Set<String> fetchSet) {
        return security.secureResult(permission(), () -> entityServiceHelper.loadById(id, fetchSet, queryAppender));
    }

    @Override
    public E save(final E entity) {
        return security.secureResult(permission(), () -> entityServiceHelper.save(entity, queryAppender));
    }

    @Override
    public Boolean delete(final E entity) {
        return security.secureResult(permission(), () -> entityServiceHelper.delete(entity));
    }

    @Override
    public BaseResultList<E> find(C criteria) {
        return security.secureResult(permission(), () -> findServiceHelper.find(criteria, getSqlFieldMap()));
    }

    @Override
    public long count(C criteria) {
        return security.secureResult(permission(), () -> countServiceHelper.count(criteria));
    }

    public String getEntityType() {
        if (entityType == null) {
            try {
                entityType = getEntityClass().newInstance().getType();
            } catch (final InstantiationException | IllegalAccessException e) {
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

    protected String permission() {
        return PermissionNames.ADMINISTRATOR;
    }
}
