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

import event.logging.BaseAdvancedQueryItem;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.FindNamedEntityCriteria;
import stroom.entity.shared.NamedEntity;
import stroom.entity.util.FieldMap;
import stroom.entity.util.HqlBuilder;
import stroom.security.Security;
import stroom.util.config.StroomProperties;

import javax.persistence.Transient;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class NamedEntityServiceImpl<E extends NamedEntity, C extends FindNamedEntityCriteria>
        extends SystemEntityServiceImpl<E, C> implements NamedEntityService<E> {
    private static final String NAME_PATTERN_PROPERTY = "stroom.namePattern";
    private static final String NAME_PATTERN_VALUE = "^[a-zA-Z0-9_\\- \\.\\(\\)]{1,}$";

    private final Security security;

    protected NamedEntityServiceImpl(final StroomEntityManager entityManager,
                                     final Security security) {
        super(entityManager, security);
        this.security = security;
    }

    @Override
    public E create(final String name) {
        return security.secureResult(permission(), () -> {
            // Create a new entity instance.
            E entity;
            try {
                entity = getEntityClass().newInstance();
            } catch (final IllegalAccessException | InstantiationException e) {
                throw new EntityServiceException(e.getMessage());
            }

            entity.setName(name);
            return save(entity);
        });
    }

    /**
     * @param name key to match
     * @return the entity by it's name or null
     */
    @Override
    public E loadByName(final String name) {
        return security.secureResult(permission(), () -> loadByName(name, Collections.emptySet()));
    }

    @SuppressWarnings("unchecked")
    @Override
    public E loadByName(final String name, final Set<String> fetchSet) {
        return security.secureResult(permission(), () -> {
            E entity = null;

            final HqlBuilder sql = new HqlBuilder();
            sql.append("SELECT e");
            sql.append(" FROM ");
            sql.append(getEntityClass().getName());
            sql.append(" AS e");

            getQueryAppender().appendBasicJoin(sql, "e", fetchSet);

            sql.append(" WHERE e.name = ");
            sql.arg(name);

            // This should just bring back 1
            final List<E> resultList = getEntityManager().executeQueryResultList(sql, null, true);
            if (resultList != null && resultList.size() > 0) {
                entity = resultList.get(0);
            }

            return entity;
        });
    }

    @Transient
    @Override
    public String getNamePattern() {
        return StroomProperties.getProperty(NAME_PATTERN_PROPERTY, NAME_PATTERN_VALUE);
    }

    @Override
    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final C criteria) {
        if (criteria.getName() != null) {
            CriteriaLoggingUtil.appendStringTerm(items, "name", criteria.getName().getString());
        }
        super.appendCriteria(items, criteria);
    }

    protected FieldMap createFieldMap() {
        return super.createFieldMap()
                .add(FindNamedEntityCriteria.FIELD_NAME, NamedEntity.NAME, "name");
    }
}
