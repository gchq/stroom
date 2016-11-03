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

import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.server.util.SQLBuilder;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.FindNamedEntityCriteria;
import stroom.entity.shared.NamedEntity;
import stroom.entity.shared.NamedEntityService;
import stroom.security.Insecure;
import stroom.util.config.StroomProperties;
import event.logging.BaseAdvancedQueryItem;

import javax.persistence.Transient;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class NamedEntityServiceImpl<E extends NamedEntity, C extends FindNamedEntityCriteria>
        extends SystemEntityServiceImpl<E, C> implements NamedEntityService<E> {
    public static final String NAME_PATTERN_PROPERTY = "stroom.namePattern";
    public static final String NAME_PATTERN_VALUE = "^[a-zA-Z0-9_\\- \\.\\(\\)]{1,}$";

    protected NamedEntityServiceImpl(final StroomEntityManager entityManager) {
        super(entityManager);
    }

    //    @Secured(permission = DocumentPermissionNames.CREATE)
    @Override
    public E create(final String name) throws RuntimeException {
        // Create a new entity instance.
        E entity;
        try {
            entity = getEntityClass().newInstance();
        } catch (final IllegalAccessException | InstantiationException e) {
            throw new EntityServiceException(e.getMessage());
        }

        entity.setName(name);
        return save(entity);
    }

    /**
     * @param name key to match
     * @return the entity by it's name or null
     */
//    @Secured(permission = DocumentPermissionNames.READ)
    @Override
    public E loadByName(final String name) {
        return loadByName(name, Collections.emptySet());
    }

    //    @Secured(permission = DocumentPermissionNames.READ)
    @SuppressWarnings("unchecked")
    @Override
    public E loadByName(final String name, final Set<String> fetchSet) {
        final SQLBuilder sql = new SQLBuilder();
        sql.append("SELECT e");
        sql.append(" FROM ");
        sql.append(getEntityClass().getName());
        sql.append(" AS e");

        getQueryAppender().appendBasicJoin(sql, "e", fetchSet);

        sql.append(" WHERE e.name = ");
        sql.arg(name);

        // This should just bring back 1
        final List<E> results = getEntityManager().executeQueryResultList(sql);

        if (results == null || results.size() == 0) {
            return null;
        }
        return results.get(0);
    }

    @Transient
    @Override
    public String getNamePattern() {
        return StroomProperties.getProperty(NAME_PATTERN_PROPERTY, NAME_PATTERN_VALUE);
    }

    @Override
    @Insecure
    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final C criteria) {
        if (criteria.getName() != null) {
            CriteriaLoggingUtil.appendStringTerm(items, "name", criteria.getName().getString());
        }
        super.appendCriteria(items, criteria);
    }
}
