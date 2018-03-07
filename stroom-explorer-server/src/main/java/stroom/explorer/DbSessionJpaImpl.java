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

package stroom.explorer;

import fri.util.database.jpa.commons.DbSession;
import stroom.spring.EntityManagerSupport;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.io.Serializable;
import java.util.List;

class DbSessionJpaImpl implements DbSession {
    private final EntityManagerSupport entityManagerSupport;

    @Inject
    DbSessionJpaImpl(final EntityManagerSupport entityManagerSupport) {
        this.entityManagerSupport = entityManagerSupport;
    }

    @Override
    public Object get(Class<?> entityClass, Serializable id) {
        // TODO: better use getReference(entityClass, id) ?
        return entityManagerSupport.executeResult(entityManager -> entityManager.find(entityClass, id));
    }

    @Override
    public Object save(Object node) {
        return entityManagerSupport.transactionResult(entityManager -> entityManager.merge(node));
    }

    @Override
    public void flush() {
        entityManagerSupport.execute(EntityManager::flush);
    }

    @Override
    public void refresh(Object node) {
        entityManagerSupport.execute(entityManager -> entityManager.refresh(node));
    }

    @Override
    public void delete(Object node) {
        entityManagerSupport.execute(entityManager -> entityManager.remove(node));
    }

    @Override
    public List<?> queryList(String queryText, Object[] parameters) {
        return entityManagerSupport.executeResult(entityManager -> {
            Query query = query(entityManager, queryText, parameters);
            return query.getResultList();
        });
    }

    @Override
    public int queryCount(String queryText, Object[] parameters) {
        return entityManagerSupport.executeResult(entityManager -> {
            @SuppressWarnings("rawtypes")
            List result = queryList(queryText, parameters);
            return ((Number) result.get(0)).intValue();
        });
    }

    @Override
    public void executeUpdate(String sqlCommand, Object[] parameters) {
        entityManagerSupport.transaction(entityManager -> {
            Query query = query(entityManager, sqlCommand, parameters);
            query.executeUpdate();
        });
    }

    private Query query(final EntityManager entityManager, String queryText, Object[] parameters) {
        Query query = entityManager.createQuery(queryText);
        if (parameters != null) {
            int i = 1;
            for (Object parameter : parameters) {
                if (parameter == null)
                    throw new IllegalArgumentException("Binding parameter at position " + i + " can not be null: " + queryText);

                query.setParameter(i, parameter);
                i++;
            }
        }
        return query;
    }
}
