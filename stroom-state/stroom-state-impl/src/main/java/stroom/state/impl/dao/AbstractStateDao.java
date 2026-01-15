/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.state.impl.dao;

import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.DateTimeSettings;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.data.CqlDuration;
import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;
import jakarta.inject.Provider;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public abstract class AbstractStateDao<T> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractStateDao.class);

    static final CqlDuration TEN_SECONDS = CqlDuration.from("PT10S");

    final Provider<CqlSession> sessionProvider;
    final CqlIdentifier table;

    public AbstractStateDao(final Provider<CqlSession> sessionProvider,
                            final CqlIdentifier table) {
        this.sessionProvider = sessionProvider;
        this.table = table;
    }

    abstract void createTables();

    final void dropTables() {
        final SimpleStatement statement = SchemaBuilder.dropTable(table)
                .ifExists()
                .build();
        sessionProvider.get().execute(statement);
    }

    public abstract void insert(List<T> rows);

    public abstract void delete(List<T> rows);

    public void doDelete(final List<T> rows,
                         final SimpleStatement deleteStatement,
                         final Function<T, Object[]> valuesFunction) {
        Objects.requireNonNull(rows, "Null state list");

        final PreparedStatement preparedStatement = sessionProvider.get().prepare(deleteStatement);

        try (final BatchStatementExecutor executor = new BatchStatementExecutor(sessionProvider)) {
            for (final T row : rows) {
                final Object[] values = valuesFunction.apply(row);
                executor.addStatement(preparedStatement.bind(values));
            }
        }
    }

    public abstract void search(ExpressionCriteria criteria,
                                FieldIndex fieldIndex,
                                DateTimeSettings dateTimeSettings,
                                ValuesConsumer valuesConsumer);

    public long count() {
        final SimpleStatement statement = QueryBuilder.selectFrom(table).countAll().build();
        return sessionProvider.get().execute(statement).one().getLong(0);
    }

    public void condense(final Instant oldest) {
        // Not all implementations condense data.
    }

    public abstract void removeOldData(Instant oldest);

    PreparedStatement prepare(final SimpleStatement statement) {
        PreparedStatement preparedStatement;
        try {
            preparedStatement = sessionProvider.get().prepare(statement);
        } catch (final InvalidQueryException e) {
            LOGGER.debug(e::getMessage, e);
            if (e.getMessage().contains("unconfigured table")) {
                createTables();
            }
            preparedStatement = sessionProvider.get().prepare(statement);
        }
        return preparedStatement;
    }
}
