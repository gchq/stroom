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

package stroom;

import org.junit.Assert;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import stroom.entity.server.util.HqlBuilder;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.shared.BaseEntity;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * Class to help with testing.
 * </p>
 */
@Component
public class DatabaseCommonTestControlTransactionHelper {
    @Resource
    private StroomEntityManager entityManager;

    /**
     * Clear a HIBERNATE context.
     */
    public void clearContext() {
        entityManager.flush();
    }

    /**
     * Count the records.
     *
     * @param clazz to count
     * @return the count
     */
    public int countEntity(final Class<?> clazz) {
        final HqlBuilder sql = new HqlBuilder();
        sql.append("SELECT count(*) FROM ");
        sql.append(clazz.getName());
        return (int) entityManager.executeQueryLongResult(sql);
    }

    /**
     * Helper.
     *
     * @param clazz
     */
    @SuppressWarnings({"unchecked"})
    public void deleteClass(final Class<?> clazz) {
        final HqlBuilder sql = new HqlBuilder();
        sql.append("SELECT e FROM ");
        sql.append(clazz.getName());
        sql.append(" as e ");
        final List<BaseEntity> results = entityManager.executeQueryResultList(sql);

        boolean foundError = true;
        int tryCount = 0;
        final int maxTryCount = 3;

        // Try to delete entities more than once if needed as we have self
        // referential entities in some cases.
        while (foundError && tryCount < maxTryCount) {
            foundError = false;
            tryCount++;

            for (int i = results.size() - 1; i >= 0; i--) {
                final BaseEntity baseEntity = results.get(i);
                try {
                    entityManager.deleteEntity(baseEntity);
                    results.remove(i);
                } catch (final DataIntegrityViolationException e) {
                    foundError = true;

                    if (tryCount == maxTryCount) {
                        throw e;
                    }
                }
            }

            entityManager.flush();
        }

        final int count = countEntity(clazz);
        if (count > 0) {
            Assert.fail("Entities not deleted for: " + clazz.getName());
        }
    }

    public void shutdown() {
        entityManager.shutdown();
    }
}
