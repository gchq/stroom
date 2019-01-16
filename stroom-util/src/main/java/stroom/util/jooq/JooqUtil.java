package stroom.util.jooq;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.SelectForUpdateStep;
import org.jooq.SelectLimitAfterOffsetStep;
import org.jooq.SelectLimitStep;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.PageRequest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.function.Function;

public final class JooqUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(JooqUtil.class);

    private JooqUtil() {
        // Utility class.
    }

    public static void context(final DataSource connectionProvider, final Consumer<DSLContext> consumer) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext context = DSL.using(connection, SQLDialect.MYSQL);
            consumer.accept(context);
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static <R> R contextResult(final DataSource connectionProvider, final Function<DSLContext, R> function) {
        R result;
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext context = DSL.using(connection, SQLDialect.MYSQL);
            result = function.apply(context);
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
        return result;
    }

    public static void contextWithOptimisticLocking(final DataSource connectionProvider, final Consumer<DSLContext> consumer) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext context = DSL.using(connection, SQLDialect.MYSQL, new Settings().withExecuteWithOptimisticLocking(true));
            consumer.accept(context);
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static <R> R contextResultWithOptimisticLocking(final DataSource connectionProvider, final Function<DSLContext, R> function) {
        R result;
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext context = DSL.using(connection, SQLDialect.MYSQL, new Settings().withExecuteWithOptimisticLocking(true));
            result = function.apply(context);
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
        return result;
    }

    public static <R extends Record> SelectForUpdateStep<R> applyLimits(final SelectLimitStep<R> select, final PageRequest pageRequest) {
        SelectForUpdateStep<R> result = select;

        if (pageRequest != null) {
            if (pageRequest.getOffset() != null) {
                final SelectLimitAfterOffsetStep<R> selectLimitAfterOffsetStep = select.offset(pageRequest.getOffset().intValue());
                result = selectLimitAfterOffsetStep;

                if (pageRequest.getLength() != null) {
                    result = selectLimitAfterOffsetStep.limit(pageRequest.getLength());
                }
            } else if (pageRequest.getLength() != null) {
                result = select.limit(pageRequest.getLength());
            }
        }

        return result;
    }
}
