package stroom.state.impl.dao;

import stroom.entity.shared.ExpressionCriteria;
import stroom.expression.api.DateTimeSettings;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import jakarta.inject.Provider;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;

public abstract class AbstractStateDao<T> {

    final Provider<CqlSession> sessionProvider;
    private final CqlIdentifier table;

    public AbstractStateDao(final Provider<CqlSession> sessionProvider,
                            final CqlIdentifier table) {
        this.sessionProvider = sessionProvider;
        this.table = table;
    }

    public abstract void createTables();

    public abstract void dropTables();

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
        final SimpleStatement statement = selectFrom(table).countAll().build();
        return sessionProvider.get().execute(statement).one().getLong(0);
    }

    public void condense(Instant oldest) {
        // Not all implementations condense data.
    }

    public abstract void removeOldData(Instant oldest);
}
