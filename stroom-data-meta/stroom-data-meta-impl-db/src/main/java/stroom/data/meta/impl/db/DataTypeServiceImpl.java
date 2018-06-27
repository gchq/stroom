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

package stroom.data.meta.impl.db;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static stroom.data.meta.impl.db.stroom.tables.DataType.DATA_TYPE;

@Singleton
class DataTypeServiceImpl implements DataTypeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataTypeServiceImpl.class);

    private final Map<String, Integer> cache = new ConcurrentHashMap<>();

    private final DataSource dataSource;

    @Inject
    DataTypeServiceImpl(final DataMetaDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Integer getOrCreate(final String name) {
        Integer id = get(name);
        if (id == null) {
            // Try and create.
            id = create(name);
            if (id == null) {
                // Get again.
                id = get(name);
            }
        }

        return id;
    }

    @Override
    public List<String> list() {
        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            return create
                    .select(DATA_TYPE.NAME)
                    .from(DATA_TYPE)
                    .fetch(DATA_TYPE.NAME);

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private Integer get(final String name) {
        Integer id = cache.get(name);
        if (id != null) {
            return id;
        }

        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            id = create
                    .select(DATA_TYPE.ID)
                    .from(DATA_TYPE)
                    .where(DATA_TYPE.NAME.eq(name))
                    .fetchOne(DATA_TYPE.ID);

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

        if (id != null) {
            cache.put(name, id);
        }

        return id;
    }

    private Integer create(final String name) {
        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            final Integer id = create
                    .insertInto(DATA_TYPE, DATA_TYPE.NAME)
                    .values(name)
                    .returning(DATA_TYPE.ID)
                    .fetchOne()
                    .getId();
            cache.put(name, id);
            return id;

        } catch (final SQLException | RuntimeException e) {
            // Expect errors in the case of duplicate names.
            LOGGER.debug(e.getMessage(), e);
        }

        return null;
    }

    void clear() {
        deleteAll();
        cache.clear();
    }

    int deleteAll() {
        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            return create
                    .delete(DATA_TYPE)
                    .execute();
        } catch (final SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
