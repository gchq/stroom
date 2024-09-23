package stroom.state.impl.dao;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BatchStatementBuilder;
import com.datastax.oss.driver.api.core.cql.BatchType;
import com.datastax.oss.driver.api.core.cql.BatchableStatement;
import jakarta.inject.Provider;

public class BatchStatementExecutor implements AutoCloseable {

    public static final int MAX_BATCH_STATEMENTS = 65535;

    private final Provider<CqlSession> sessionProvider;
    private final BatchStatementBuilder builder = new BatchStatementBuilder(BatchType.UNLOGGED);
    private int statementCount;

    public BatchStatementExecutor(final Provider<CqlSession> sessionProvider) {
        this.sessionProvider = sessionProvider;
    }

    public void addStatement(final BatchableStatement<?> statement) {
        builder.addStatement(statement);
        statementCount++;

        if (statementCount >= MAX_BATCH_STATEMENTS) {
            sessionProvider.get().execute(builder.build());
            builder.clearStatements();
            statementCount = 0;
        }
    }

    @Override
    public void close() {
        if (statementCount > 0) {
            sessionProvider.get().execute(builder.build());
            builder.clearStatements();
            statementCount = 0;
        }
    }
}
